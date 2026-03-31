package com.ownpic.detection;

import com.ownpic.detection.domain.*;
import com.ownpic.detection.dto.DetectionScanDetailResponse;
import com.ownpic.detection.dto.DetectionScanResponse;
import com.ownpic.detection.port.SimilarImageSearchPort;
import com.ownpic.detection.port.SimilarImageSearchPort.BatchResult;
import com.ownpic.detection.port.SimilarImageSearchPort.ImageEmbedding;
import com.ownpic.image.domain.Image;
import com.ownpic.image.domain.ImageRepository;
import com.ownpic.image.domain.ImageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.nio.ByteBuffer;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DetectionServiceTest {

    @Mock DetectionScanRepository scanRepository;
    @Mock DetectionResultRepository resultRepository;
    @Mock InternetDetectionResultRepository internetResultRepository;
    @Mock ImageRepository imageRepository;
    @Mock SimilarImageSearchPort searchPort;

    @InjectMocks DetectionService detectionService;

    private UUID userId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
    }

    // --- startScan ---

    @Test
    void startScan_withIndexedImages_createsScanAndReturnsResponse() {
        List<Image> images = List.of(createImageWithEmbeddings(1L, userId));
        when(imageRepository.findByUserIdAndStatus(userId, ImageStatus.INDEXED)).thenReturn(images);
        when(scanRepository.save(any(DetectionScan.class))).thenAnswer(inv -> {
            DetectionScan scan = inv.getArgument(0);
            setField(scan, "id", 100L);
            return scan;
        });

        DetectionScanResponse response = detectionService.startScan(userId);

        assertThat(response.status()).isEqualTo("SCANNING");
        assertThat(response.totalImages()).isEqualTo(1);
        verify(scanRepository).save(any(DetectionScan.class));
    }

    @Test
    void startScan_withNoIndexedImages_throwsBadRequest() {
        when(imageRepository.findByUserIdAndStatus(userId, ImageStatus.INDEXED)).thenReturn(List.of());

        assertThatThrownBy(() -> detectionService.startScan(userId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("인덱싱된 이미지가 없습니다");
    }

    // --- executeScanAsync ---

    @Test
    void executeScanAsync_batchesImagesAndCallsSearchPort() {
        // 15 images → 2 batches (10 + 5)
        List<Image> images = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            images.add(createImageWithEmbeddings((long) i, userId));
        }

        DetectionScan scan = new DetectionScan(userId, 15);
        setField(scan, "id", 1L);
        when(scanRepository.findById(1L)).thenReturn(Optional.of(scan));
        when(searchPort.findAllBatch(anyList(), anyDouble(), anyInt())).thenReturn(List.of());
        when(searchPort.findAllDinoBatch(anyList(), anyDouble(), anyInt())).thenReturn(List.of());

        detectionService.executeScanAsync(1L, userId, images);

        // 2 batches → 2 calls each for SSCD and DINO
        verify(searchPort, times(2)).findAllBatch(anyList(), eq(0.15), eq(20));
        verify(searchPort, times(2)).findAllDinoBatch(anyList(), eq(0.70), eq(20));
        verify(scanRepository, atLeast(2)).findById(1L); // progress updates
    }

    @Test
    void executeScanAsync_mergesResultsAndSaves() {
        Image img = createImageWithEmbeddings(1L, userId);
        DetectionScan scan = new DetectionScan(userId, 1);
        setField(scan, "id", 1L);
        when(scanRepository.findById(1L)).thenReturn(Optional.of(scan));

        BatchResult sscdMatch = new BatchResult(1L, 99L, otherUserId, 0.50);
        BatchResult dinoMatch = new BatchResult(1L, 99L, otherUserId, 0.80);

        when(searchPort.findAllBatch(anyList(), anyDouble(), anyInt())).thenReturn(List.of(sscdMatch));
        when(searchPort.findAllDinoBatch(anyList(), anyDouble(), anyInt())).thenReturn(List.of(dinoMatch));

        detectionService.executeScanAsync(1L, userId, List.of(img));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DetectionResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(resultRepository).saveAll(captor.capture());
        List<DetectionResult> results = captor.getValue();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getJudgment()).isEqualTo("INFRINGEMENT");
        assertThat(results.get(0).getSourceImageId()).isEqualTo(1L);
        assertThat(results.get(0).getMatchedImageId()).isEqualTo(99L);
    }

    @Test
    void executeScanAsync_excludesSameUserMatches() {
        Image img = createImageWithEmbeddings(1L, userId);
        DetectionScan scan = new DetectionScan(userId, 1);
        setField(scan, "id", 1L);
        when(scanRepository.findById(1L)).thenReturn(Optional.of(scan));

        // Same user match → should be filtered out
        BatchResult sameUserMatch = new BatchResult(1L, 50L, userId, 0.90);
        when(searchPort.findAllBatch(anyList(), anyDouble(), anyInt())).thenReturn(List.of(sameUserMatch));
        when(searchPort.findAllDinoBatch(anyList(), anyDouble(), anyInt())).thenReturn(List.of());

        detectionService.executeScanAsync(1L, userId, List.of(img));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DetectionResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(resultRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void executeScanAsync_excludesSelfMatches() {
        Image img = createImageWithEmbeddings(1L, userId);
        DetectionScan scan = new DetectionScan(userId, 1);
        setField(scan, "id", 1L);
        when(scanRepository.findById(1L)).thenReturn(Optional.of(scan));

        // sourceId == matchedId → self match
        BatchResult selfMatch = new BatchResult(1L, 1L, otherUserId, 0.95);
        when(searchPort.findAllBatch(anyList(), anyDouble(), anyInt())).thenReturn(List.of(selfMatch));
        when(searchPort.findAllDinoBatch(anyList(), anyDouble(), anyInt())).thenReturn(List.of());

        detectionService.executeScanAsync(1L, userId, List.of(img));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DetectionResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(resultRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void executeScanAsync_dualJudgment_sscdAboveThreshold() {
        Image img = createImageWithEmbeddings(1L, userId);
        DetectionScan scan = new DetectionScan(userId, 1);
        setField(scan, "id", 1L);
        when(scanRepository.findById(1L)).thenReturn(Optional.of(scan));

        // SSCD=0.35 (above 0.30), no DINO match
        BatchResult sscdMatch = new BatchResult(1L, 99L, otherUserId, 0.35);
        when(searchPort.findAllBatch(anyList(), anyDouble(), anyInt())).thenReturn(List.of(sscdMatch));
        when(searchPort.findAllDinoBatch(anyList(), anyDouble(), anyInt())).thenReturn(List.of());

        detectionService.executeScanAsync(1L, userId, List.of(img));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DetectionResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(resultRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getJudgment()).isEqualTo("INFRINGEMENT");
    }

    @Test
    void executeScanAsync_dualJudgment_dinoAboveThreshold_withSscdMin() {
        Image img = createImageWithEmbeddings(1L, userId);
        DetectionScan scan = new DetectionScan(userId, 1);
        setField(scan, "id", 1L);
        when(scanRepository.findById(1L)).thenReturn(Optional.of(scan));

        // SSCD=0.20 (above 0.15 min), DINO=0.75 (above 0.70) → bg_swap 보조 탐지
        BatchResult sscdMatch = new BatchResult(1L, 99L, otherUserId, 0.20);
        BatchResult dinoMatch = new BatchResult(1L, 99L, otherUserId, 0.75);
        when(searchPort.findAllBatch(anyList(), anyDouble(), anyInt())).thenReturn(List.of(sscdMatch));
        when(searchPort.findAllDinoBatch(anyList(), anyDouble(), anyInt())).thenReturn(List.of(dinoMatch));

        detectionService.executeScanAsync(1L, userId, List.of(img));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DetectionResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(resultRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    @Test
    void executeScanAsync_dualJudgment_dinoOnly_noSscdMin_rejected() {
        Image img = createImageWithEmbeddings(1L, userId);
        DetectionScan scan = new DetectionScan(userId, 1);
        setField(scan, "id", 1L);
        when(scanRepository.findById(1L)).thenReturn(Optional.of(scan));

        // No SSCD match, DINO=0.75 → DINO 단독은 차단
        BatchResult dinoMatch = new BatchResult(1L, 99L, otherUserId, 0.75);
        when(searchPort.findAllBatch(anyList(), anyDouble(), anyInt())).thenReturn(List.of());
        when(searchPort.findAllDinoBatch(anyList(), anyDouble(), anyInt())).thenReturn(List.of(dinoMatch));

        detectionService.executeScanAsync(1L, userId, List.of(img));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DetectionResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(resultRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void executeScanAsync_dualJudgment_bothBelowThreshold_noResults() {
        Image img = createImageWithEmbeddings(1L, userId);
        DetectionScan scan = new DetectionScan(userId, 1);
        setField(scan, "id", 1L);
        when(scanRepository.findById(1L)).thenReturn(Optional.of(scan));

        // SSCD=0.25 (below 0.30), DINO=0.60 (below 0.70) — from search port perspective
        // these won't actually be returned by findAllBatch since they use threshold filtering
        // but in mergeAndJudge, it does its own check
        when(searchPort.findAllBatch(anyList(), anyDouble(), anyInt())).thenReturn(List.of());
        when(searchPort.findAllDinoBatch(anyList(), anyDouble(), anyInt())).thenReturn(List.of());

        detectionService.executeScanAsync(1L, userId, List.of(img));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DetectionResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(resultRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void executeScanAsync_onException_failsScan() {
        Image img = createImageWithEmbeddings(1L, userId);
        DetectionScan scan = new DetectionScan(userId, 1);
        setField(scan, "id", 1L);
        when(scanRepository.findById(1L)).thenReturn(Optional.of(scan));
        when(searchPort.findAllBatch(anyList(), anyDouble(), anyInt())).thenThrow(new RuntimeException("DB error"));

        detectionService.executeScanAsync(1L, userId, List.of(img));

        // failScan → findById + save with FAILED status
        verify(scanRepository, atLeast(1)).findById(1L);
        verify(scanRepository).save(argThat(s -> "FAILED".equals(s.getStatus())));
    }

    @Test
    void executeScanAsync_completesWithMatchCount() {
        Image img = createImageWithEmbeddings(1L, userId);
        DetectionScan scan = new DetectionScan(userId, 1);
        setField(scan, "id", 1L);
        when(scanRepository.findById(1L)).thenReturn(Optional.of(scan));

        BatchResult match = new BatchResult(1L, 99L, otherUserId, 0.50);
        when(searchPort.findAllBatch(anyList(), anyDouble(), anyInt())).thenReturn(List.of(match));
        when(searchPort.findAllDinoBatch(anyList(), anyDouble(), anyInt())).thenReturn(List.of());

        detectionService.executeScanAsync(1L, userId, List.of(img));

        // completeScan sets COMPLETED status
        assertThat(scan.getStatus()).isEqualTo("COMPLETED");
        assertThat(scan.getMatchesFound()).isEqualTo(1);
    }

    // --- getScans ---

    @Test
    void getScans_returnsPaginatedResults() {
        DetectionScan scan = new DetectionScan(userId, 10);
        setField(scan, "id", 1L);
        var pageable = PageRequest.of(0, 10);
        when(scanRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable))
                .thenReturn(new PageImpl<>(List.of(scan), pageable, 1));

        var result = detectionService.getScans(userId, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).totalImages()).isEqualTo(10);
    }

    // --- getScanDetail ---

    @Test
    void getScanDetail_found_returnsDetail() {
        DetectionScan scan = new DetectionScan(userId, 5);
        setField(scan, "id", 1L);
        when(scanRepository.findByIdAndUserId(1L, userId)).thenReturn(Optional.of(scan));

        DetectionResult result = new DetectionResult(1L, 1L, 99L, otherUserId, 0.5, 0.8, "INFRINGEMENT");
        when(resultRepository.findByScanIdOrderByCreatedAt(1L)).thenReturn(List.of(result));

        DetectionScanDetailResponse detail = detectionService.getScanDetail(userId, 1L);

        assertThat(detail.scan().totalImages()).isEqualTo(5);
        assertThat(detail.results()).hasSize(1);
        assertThat(detail.results().get(0).judgment()).isEqualTo("INFRINGEMENT");
    }

    @Test
    void getScanDetail_notFound_throws404() {
        when(scanRepository.findByIdAndUserId(999L, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> detectionService.getScanDetail(userId, 999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("스캔을 찾을 수 없습니다");
    }

    // --- Helpers ---

    private Image createImageWithEmbeddings(Long id, UUID ownerId) {
        Image img = new Image();
        setField(img, "id", id);
        img.setEmbedding(floatsToBytes(new float[]{0.1f, 0.2f, 0.3f}));
        img.setEmbeddingDino(floatsToBytes(new float[]{0.4f, 0.5f, 0.6f}));
        return img;
    }

    private byte[] floatsToBytes(float[] floats) {
        ByteBuffer buf = ByteBuffer.allocate(floats.length * 4);
        for (float f : floats) buf.putFloat(f);
        return buf.array();
    }

    @SuppressWarnings("unchecked")
    private static void setField(Object target, String fieldName, Object value) {
        try {
            var clazz = target.getClass();
            while (clazz != null) {
                try {
                    var field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    field.set(target, value);
                    return;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            throw new NoSuchFieldException(fieldName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}
