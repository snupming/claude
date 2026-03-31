package com.ownpic.detection;

import com.ownpic.detection.domain.*;
import com.ownpic.detection.dto.DetectionScanResponse;
import com.ownpic.detection.port.DinoEmbeddingPort;
import com.ownpic.detection.port.ExternalImageDownloadPort;
import com.ownpic.detection.port.InternetImageSearchPort;
import com.ownpic.detection.port.InternetImageSearchPort.SearchResult;
import com.ownpic.detection.port.SscdEmbeddingPort;
import com.ownpic.image.domain.Image;
import com.ownpic.image.domain.ImageRepository;
import com.ownpic.image.domain.ImageStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;

@Service
public class InternetDetectionService {

    private static final Logger log = LoggerFactory.getLogger(InternetDetectionService.class);

    private static final double SSCD_THRESHOLD = 0.30;
    private static final double DINO_THRESHOLD = 0.70;
    private static final int DOWNLOAD_TIMEOUT_MS = 10_000;
    private static final int MAX_SEARCH_RESULTS = 50;

    private final DetectionScanRepository scanRepository;
    private final InternetDetectionResultRepository resultRepository;
    private final ImageRepository imageRepository;
    private final InternetImageSearchPort searchPort;
    private final ExternalImageDownloadPort downloadPort;
    private final SscdEmbeddingPort sscdPort;
    private final DinoEmbeddingPort dinoPort;

    public InternetDetectionService(DetectionScanRepository scanRepository,
                                    InternetDetectionResultRepository resultRepository,
                                    ImageRepository imageRepository,
                                    InternetImageSearchPort searchPort,
                                    ExternalImageDownloadPort downloadPort,
                                    SscdEmbeddingPort sscdPort,
                                    DinoEmbeddingPort dinoPort) {
        this.scanRepository = scanRepository;
        this.resultRepository = resultRepository;
        this.imageRepository = imageRepository;
        this.searchPort = searchPort;
        this.downloadPort = downloadPort;
        this.sscdPort = sscdPort;
        this.dinoPort = dinoPort;
    }

    @Transactional
    public DetectionScanResponse startInternetScan(UUID userId) {
        List<Image> indexed = imageRepository.findByUserIdAndStatus(userId, ImageStatus.INDEXED);
        if (indexed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "인덱싱된 이미지가 없습니다.");
        }

        var scan = new DetectionScan(userId, indexed.size());
        scan.setScanType("INTERNET");
        scan = scanRepository.save(scan);

        executeInternetScanAsync(scan.getId(), userId, indexed);

        return DetectionScanResponse.from(scan);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeInternetScanAsync(Long scanId, UUID userId, List<Image> images) {
        try {
            List<InternetDetectionResult> allResults = new ArrayList<>();
            int scannedCount = 0;

            for (Image image : images) {
                float[] sourceSSCD = bytesToFloats(image.getEmbedding());
                float[] sourceDINO = bytesToFloats(image.getEmbeddingDino());

                // 이미지 이름/키워드 기반으로 네이버 검색
                String keyword = buildSearchKeyword(image);
                List<SearchResult> searchResults = searchPort.searchByKeyword(keyword, MAX_SEARCH_RESULTS);

                for (SearchResult sr : searchResults) {
                    InternetDetectionResult result = processSearchResult(
                            scanId, image.getId(), sr, sourceSSCD, sourceDINO);
                    if (result != null) {
                        allResults.add(result);
                    }
                }

                scannedCount++;
                updateProgress(scanId, scannedCount);
            }

            resultRepository.saveAll(allResults);
            completeScan(scanId, allResults.size());

            log.info("Internet scan {} completed: {} images scanned, {} matches",
                    scanId, images.size(), allResults.size());
        } catch (Exception e) {
            log.error("Internet scan {} failed", scanId, e);
            failScan(scanId);
        }
    }

    private InternetDetectionResult processSearchResult(
            Long scanId, Long sourceImageId, SearchResult sr,
            float[] sourceSSCD, float[] sourceDINO) {

        // 1. 외부 이미지 다운로드
        byte[] foundBytes = downloadPort.download(sr.imageUrl(), DOWNLOAD_TIMEOUT_MS);
        if (foundBytes == null) return null;

        // 2. 임베딩 생성 + 코사인 유사도 비교
        Double sscdSim = null;
        Double dinoSim = null;

        if (sourceSSCD != null) {
            float[] foundSSCD = sscdPort.generateEmbedding(foundBytes);
            if (foundSSCD != null) {
                sscdSim = cosineSimilarity(sourceSSCD, foundSSCD);
            }
        }

        if (sourceDINO != null) {
            float[] foundDINO = dinoPort.generateEmbedding(foundBytes);
            if (foundDINO != null) {
                dinoSim = cosineSimilarity(sourceDINO, foundDINO);
            }
        }

        // 3. 듀얼 판정
        boolean sscdMatch = sscdSim != null && sscdSim >= SSCD_THRESHOLD;
        boolean dinoMatch = dinoSim != null && dinoSim >= DINO_THRESHOLD;

        if (!sscdMatch && !dinoMatch) {
            return null; // 임계값 미달 → 스킵
        }

        return new InternetDetectionResult(
                scanId, sourceImageId,
                sr.imageUrl(), sr.sourcePageUrl(), sr.title(),
                sscdSim, dinoSim, "INFRINGEMENT", "NAVER");
    }

    /**
     * 이미지의 이름/키워드에서 검색어를 생성한다.
     * keywords 필드가 있으면 우선 사용, 없으면 파일명에서 확장자 제거 후 사용.
     */
    private String buildSearchKeyword(Image image) {
        if (image.getKeywords() != null && !image.getKeywords().isBlank()) {
            return image.getKeywords();
        }
        // 파일명에서 확장자 제거
        String name = image.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        // 언더스코어/하이픈을 공백으로
        return name.replaceAll("[_\\-]+", " ").trim();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(Long scanId, int scannedImages) {
        scanRepository.findById(scanId).ifPresent(scan -> {
            scan.setScannedImages(scannedImages);
            scanRepository.save(scan);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeScan(Long scanId, int matchesFound) {
        scanRepository.findById(scanId).ifPresent(scan -> {
            scan.setStatus("COMPLETED");
            scan.setMatchesFound(matchesFound);
            scan.setScannedImages(scan.getTotalImages());
            scan.setCompletedAt(Instant.now());
            scanRepository.save(scan);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failScan(Long scanId) {
        scanRepository.findById(scanId).ifPresent(scan -> {
            scan.setStatus("FAILED");
            scan.setCompletedAt(Instant.now());
            scanRepository.save(scan);
        });
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dot / denom;
    }

    private static float[] bytesToFloats(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        float[] result = new float[bytes.length / 4];
        for (int i = 0; i < result.length; i++) {
            result[i] = buf.getFloat();
        }
        return result;
    }
}
