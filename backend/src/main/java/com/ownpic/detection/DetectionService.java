package com.ownpic.detection;

import com.ownpic.detection.domain.*;
import com.ownpic.detection.dto.DetectionResultResponse;
import com.ownpic.detection.dto.DetectionScanDetailResponse;
import com.ownpic.detection.dto.DetectionScanResponse;
import com.ownpic.detection.dto.InternetDetectionResultResponse;
import com.ownpic.detection.port.SimilarImageSearchPort;
import com.ownpic.detection.port.SimilarImageSearchPort.BatchResult;
import com.ownpic.detection.port.SimilarImageSearchPort.ImageEmbedding;
import com.ownpic.image.domain.Image;
import com.ownpic.image.domain.ImageRepository;
import com.ownpic.image.domain.ImageStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
public class DetectionService {

    private static final Logger log = LoggerFactory.getLogger(DetectionService.class);

    private static final int BATCH_SIZE = 10;
    private static final double SSCD_THRESHOLD = 0.30;
    private static final double DINO_THRESHOLD = 0.70;

    private final DetectionScanRepository scanRepository;
    private final DetectionResultRepository resultRepository;
    private final InternetDetectionResultRepository internetResultRepository;
    private final ImageRepository imageRepository;
    private final SimilarImageSearchPort searchPort;

    public DetectionService(DetectionScanRepository scanRepository,
                            DetectionResultRepository resultRepository,
                            InternetDetectionResultRepository internetResultRepository,
                            ImageRepository imageRepository,
                            SimilarImageSearchPort searchPort) {
        this.scanRepository = scanRepository;
        this.resultRepository = resultRepository;
        this.internetResultRepository = internetResultRepository;
        this.imageRepository = imageRepository;
        this.searchPort = searchPort;
    }

    @Transactional
    public DetectionScanResponse startScan(UUID userId) {
        List<Image> indexed = imageRepository.findByUserIdAndStatus(userId, ImageStatus.INDEXED);
        if (indexed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "인덱싱된 이미지가 없습니다.");
        }

        var scan = new DetectionScan(userId, indexed.size());
        scan = scanRepository.save(scan);

        executeScanAsync(scan.getId(), userId, indexed);

        return DetectionScanResponse.from(scan);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeScanAsync(Long scanId, UUID userId, List<Image> images) {
        try {
            List<DetectionResult> allResults = new ArrayList<>();
            int scannedCount = 0;

            List<List<Image>> chunks = partition(images, BATCH_SIZE);

            for (List<Image> chunk : chunks) {
                // SSCD 배치
                List<ImageEmbedding> sscdBatch = new ArrayList<>();
                for (Image img : chunk) {
                    float[] emb = bytesToFloats(img.getEmbedding());
                    if (emb != null) sscdBatch.add(new ImageEmbedding(img.getId(), emb));
                }
                List<BatchResult> sscdMatches = sscdBatch.isEmpty()
                        ? List.of()
                        : searchPort.findAllBatch(sscdBatch, SSCD_THRESHOLD, 20);

                // DINOv2 배치
                List<ImageEmbedding> dinoBatch = new ArrayList<>();
                for (Image img : chunk) {
                    float[] emb = bytesToFloats(img.getEmbeddingDino());
                    if (emb != null) dinoBatch.add(new ImageEmbedding(img.getId(), emb));
                }
                List<BatchResult> dinoMatches = dinoBatch.isEmpty()
                        ? List.of()
                        : searchPort.findAllDinoBatch(dinoBatch, DINO_THRESHOLD, 20);

                // 병합 + 판정
                mergeAndJudge(scanId, userId, sscdMatches, dinoMatches, allResults);

                scannedCount += chunk.size();
                updateProgress(scanId, scannedCount);
            }

            // 결과 벌크 저장
            resultRepository.saveAll(allResults);
            completeScan(scanId, allResults.size());

            log.info("Detection scan {} completed: {} images scanned, {} matches",
                    scanId, images.size(), allResults.size());
        } catch (Exception e) {
            log.error("Detection scan {} failed", scanId, e);
            failScan(scanId);
        }
    }

    private void mergeAndJudge(Long scanId, UUID userId,
                                List<BatchResult> sscdMatches, List<BatchResult> dinoMatches,
                                List<DetectionResult> results) {
        // 매칭 쌍별로 최고 유사도 수집
        // key: "sourceId:matchedId"
        Map<String, double[]> pairScores = new LinkedHashMap<>();
        Map<String, UUID> pairUsers = new HashMap<>();

        for (BatchResult m : sscdMatches) {
            if (m.matchedUserId().equals(userId)) continue; // 같은 유저 제외
            String key = m.sourceImageId() + ":" + m.matchedImageId();
            pairScores.computeIfAbsent(key, k -> new double[]{0, 0})[0] =
                    Math.max(pairScores.getOrDefault(key, new double[]{0, 0})[0], m.similarity());
            pairUsers.put(key, m.matchedUserId());
        }

        for (BatchResult m : dinoMatches) {
            if (m.matchedUserId().equals(userId)) continue;
            String key = m.sourceImageId() + ":" + m.matchedImageId();
            pairScores.computeIfAbsent(key, k -> new double[]{0, 0})[1] =
                    Math.max(pairScores.getOrDefault(key, new double[]{0, 0})[1], m.similarity());
            pairUsers.put(key, m.matchedUserId());
        }

        for (var entry : pairScores.entrySet()) {
            String[] ids = entry.getKey().split(":");
            long sourceId = Long.parseLong(ids[0]);
            long matchedId = Long.parseLong(ids[1]);
            if (sourceId == matchedId) continue; // 자기 자신 제외

            double sscd = entry.getValue()[0];
            double dino = entry.getValue()[1];

            // 듀얼 판정
            String judgment;
            if (sscd >= SSCD_THRESHOLD || dino >= DINO_THRESHOLD) {
                judgment = "INFRINGEMENT";
            } else {
                continue; // threshold 미달은 저장하지 않음
            }

            results.add(new DetectionResult(scanId, sourceId, matchedId,
                    pairUsers.get(entry.getKey()), sscd > 0 ? sscd : null,
                    dino > 0 ? dino : null, judgment));
        }
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

    @Transactional(readOnly = true)
    public Page<DetectionScanResponse> getScans(UUID userId, Pageable pageable) {
        return scanRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(DetectionScanResponse::from);
    }

    @Transactional(readOnly = true)
    public DetectionScanDetailResponse getScanDetail(UUID userId, Long scanId) {
        var scan = scanRepository.findByIdAndUserId(scanId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "스캔을 찾을 수 없습니다."));
        var results = resultRepository.findByScanIdOrderByCreatedAt(scanId)
                .stream().map(DetectionResultResponse::from).toList();
        var internetResults = internetResultRepository.findByScanIdOrderByCreatedAt(scanId)
                .stream().map(InternetDetectionResultResponse::from).toList();
        return new DetectionScanDetailResponse(
                DetectionScanResponse.from(scan), results, internetResults);
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

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
