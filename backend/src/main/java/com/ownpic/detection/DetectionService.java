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
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class DetectionService {

    private static final Logger log = LoggerFactory.getLogger(DetectionService.class);

    private static final int BATCH_SIZE = 10;

    /**
     * SSCD 논문 기준 threshold:
     * - 0.75: 90% precision (논문 권장 기본값)
     * - 0.90: ~99% precision (거의 동일 이미지)
     * - 0.60: ~70% precision (recall 우선)
     */
    private static final double SSCD_THRESHOLD = 0.75;

    private final DetectionScanRepository scanRepository;
    private final DetectionResultRepository resultRepository;
    private final InternetDetectionResultRepository internetResultRepository;
    private final ImageRepository imageRepository;
    private final SimilarImageSearchPort searchPort;

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

            for (List<Image> chunk : partition(images)) {
                List<ImageEmbedding> batch = new ArrayList<>();
                for (Image img : chunk) {
                    float[] emb = bytesToFloats(img.getEmbedding());
                    if (emb != null) batch.add(new ImageEmbedding(img.getId(), emb));
                }

                if (!batch.isEmpty()) {
                    List<BatchResult> matches = searchPort.findAllBatch(batch, SSCD_THRESHOLD, 20);
                    collectResults(scanId, userId, matches, allResults);
                }

                scannedCount += chunk.size();
                updateProgress(scanId, scannedCount);
            }

            resultRepository.saveAll(allResults);
            completeScan(scanId, allResults.size());

            log.info("Detection scan {} completed: {} images scanned, {} matches",
                    scanId, images.size(), allResults.size());
        } catch (Exception e) {
            log.error("Detection scan {} failed", scanId, e);
            failScan(scanId);
        }
    }

    private void collectResults(Long scanId, UUID userId,
                                List<BatchResult> matches,
                                List<DetectionResult> results) {
        // 쌍별 최고 유사도 수집
        Map<String, Double> pairScores = new LinkedHashMap<>();
        Map<String, UUID> pairUsers = new HashMap<>();

        for (BatchResult m : matches) {
            if (m.matchedUserId().equals(userId)) continue;

            String key = m.sourceImageId() + ":" + m.matchedImageId();
            pairScores.merge(key, m.similarity(), Math::max);
            pairUsers.put(key, m.matchedUserId());
        }

        for (var entry : pairScores.entrySet()) {
            String[] ids = entry.getKey().split(":");
            long sourceId = Long.parseLong(ids[0]);
            long matchedId = Long.parseLong(ids[1]);
            if (sourceId == matchedId) continue;

            double sscd = entry.getValue();

            results.add(new DetectionResult(
                    scanId, sourceId, matchedId,
                    pairUsers.get(entry.getKey()),
                    sscd, null, "INFRINGEMENT"));
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

    private static <T> List<List<T>> partition(List<T> list) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += BATCH_SIZE) {
            partitions.add(list.subList(i, Math.min(i + BATCH_SIZE, list.size())));
        }
        return partitions;
    }
}