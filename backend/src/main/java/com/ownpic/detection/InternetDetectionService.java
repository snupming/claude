package com.ownpic.detection;

import com.ownpic.detection.domain.*;
import com.ownpic.detection.dto.DetectionScanResponse;
import com.ownpic.detection.port.*;
import com.ownpic.detection.port.InternetImageSearchPort.SearchResult;
import com.ownpic.detection.port.ReverseImageSearchPort.ReverseSearchResult;
import com.ownpic.image.domain.Image;
import com.ownpic.image.domain.ImageRepository;
import com.ownpic.image.domain.ImageStatus;
import com.ownpic.image.port.ImageStoragePort;
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
    private static final double SSCD_MIN_FOR_DINO = 0.15;
    private static final int DOWNLOAD_TIMEOUT_MS = 10_000;
    private static final int MAX_SEARCH_RESULTS = 50;

    private final DetectionScanRepository scanRepository;
    private final InternetDetectionResultRepository resultRepository;
    private final ImageRepository imageRepository;
    private final InternetImageSearchPort searchPort;
    private final ReverseImageSearchPort reverseSearchPort;
    private final ExternalImageDownloadPort downloadPort;
    private final ImageStoragePort storagePort;
    private final SscdEmbeddingPort sscdPort;
    private final DinoEmbeddingPort dinoPort;
    private final ImageCaptionPort captionPort;

    public InternetDetectionService(DetectionScanRepository scanRepository,
                                    InternetDetectionResultRepository resultRepository,
                                    ImageRepository imageRepository,
                                    InternetImageSearchPort searchPort,
                                    ReverseImageSearchPort reverseSearchPort,
                                    ExternalImageDownloadPort downloadPort,
                                    ImageStoragePort storagePort,
                                    SscdEmbeddingPort sscdPort,
                                    DinoEmbeddingPort dinoPort,
                                    ImageCaptionPort captionPort) {
        this.scanRepository = scanRepository;
        this.resultRepository = resultRepository;
        this.imageRepository = imageRepository;
        this.searchPort = searchPort;
        this.reverseSearchPort = reverseSearchPort;
        this.downloadPort = downloadPort;
        this.storagePort = storagePort;
        this.sscdPort = sscdPort;
        this.dinoPort = dinoPort;
        this.captionPort = captionPort;
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

                // 1단계: 네이버 키워드 검색 (키워드가 있을 때만)
                String keyword = buildSearchKeyword(image);
                if (keyword != null && !keyword.isBlank()) {
                    List<SearchResult> keywordResults = searchPort.searchByKeyword(keyword, MAX_SEARCH_RESULTS);

                    for (SearchResult sr : keywordResults) {
                        InternetDetectionResult result = processSearchResult(
                                scanId, image.getId(), sr, sourceSSCD, sourceDINO, "NAVER");
                        if (result != null) {
                            allResults.add(result);
                        }
                    }
                }

                // 2단계: 구글 리버스 이미지 검색 (항상 실행)
                if (image.getGcsPath() != null) {
                    byte[] imageBytes = storagePort.load(image.getGcsPath());
                    if (imageBytes != null && imageBytes.length > 0) {
                        List<ReverseSearchResult> reverseResults =
                                reverseSearchPort.searchByImage(imageBytes, MAX_SEARCH_RESULTS);

                        for (ReverseSearchResult rr : reverseResults) {
                            SearchResult sr = new SearchResult(rr.imageUrl(), rr.sourcePageUrl(), rr.title());
                            InternetDetectionResult result = processSearchResult(
                                    scanId, image.getId(), sr, sourceSSCD, sourceDINO, "GOOGLE");
                            if (result != null) {
                                allResults.add(result);
                            }
                        }
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
            float[] sourceSSCD, float[] sourceDINO, String searchEngine) {

        // 1. 외부 이미지 다운로드
        byte[] foundBytes = downloadPort.download(sr.imageUrl(), DOWNLOAD_TIMEOUT_MS);
        if (foundBytes == null) return null;

        // 2. 임베딩 생성 + 코사인 유사도 비교
        Double sscdSim = null;
        Double dinoSim = null;

        try {
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
        } catch (Exception e) {
            log.debug("Embedding failed for {}: {}", sr.imageUrl(), e.getMessage());
            return null;
        }

        // 3. 듀얼 판정 — SSCD 메인, DINO 보조 (bg_swap 등 변형 탐지)
        boolean sscdMatch = sscdSim != null && sscdSim >= SSCD_THRESHOLD;
        boolean dinoAssist = sscdSim != null && sscdSim >= SSCD_MIN_FOR_DINO
                && dinoSim != null && dinoSim >= DINO_THRESHOLD;

        if (!sscdMatch && !dinoAssist) {
            return null; // 임계값 미달 → 스킵
        }

        return new InternetDetectionResult(
                scanId, sourceImageId,
                sr.imageUrl(), sr.sourcePageUrl(), sr.title(),
                sscdSim, dinoSim, "INFRINGEMENT", searchEngine);
    }

    /**
     * 이미지의 검색 키워드를 결정한다.
     * 우선순위: 사용자 입력 키워드 → AI 이미지 캡션 → null (리버스 이미지 검색으로 fallback)
     */
    private String buildSearchKeyword(Image image) {
        // 1. 사용자가 직접 입력한 키워드
        if (image.getKeywords() != null && !image.getKeywords().isBlank()) {
            return image.getKeywords();
        }

        // 2. AI Vision으로 이미지 내용 추론하여 키워드 생성
        if (image.getGcsPath() != null) {
            try {
                byte[] imageBytes = storagePort.load(image.getGcsPath());
                if (imageBytes != null && imageBytes.length > 0) {
                    String caption = captionPort.generateKeywords(imageBytes);
                    if (caption != null && !caption.isBlank()) {
                        log.info("AI-generated keywords for image {}: {}", image.getId(), caption);
                        image.setKeywords(caption);
                        imageRepository.save(image);
                        return caption;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to generate AI keywords for image {}: {}", image.getId(), e.getMessage());
            }
        }

        // 키워드 없으면 null → 키워드 검색 스킵, 리버스 이미지 검색으로 진행
        return null;
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
