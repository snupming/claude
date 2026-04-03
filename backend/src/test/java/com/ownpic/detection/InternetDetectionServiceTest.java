package com.ownpic.detection;

import com.ownpic.detection.domain.*;
import com.ownpic.detection.port.*;
import com.ownpic.detection.port.InternetImageSearchPort.SearchResult;
import com.ownpic.image.domain.Image;
import com.ownpic.image.domain.ImageRepository;
import com.ownpic.image.domain.ImageStatus;
import com.ownpic.image.port.ImageStoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InternetDetectionServiceTest {

    @Mock DetectionScanRepository scanRepository;
    @Mock InternetDetectionResultRepository resultRepository;
    @Mock ImageRepository imageRepository;
    @Mock InternetImageSearchPort searchPort;
    @Mock ReverseImageSearchPort reverseSearchPort;
    @Mock ExternalImageDownloadPort downloadPort;
    @Mock ImageStoragePort storagePort;
    @Mock SscdEmbeddingPort sscdPort;
    @Mock DinoEmbeddingPort dinoPort;

    InternetDetectionService service;
    UUID userId;

    @BeforeEach
    void setUp() {
        service = new InternetDetectionService(
                scanRepository, resultRepository, imageRepository,
                searchPort, reverseSearchPort, downloadPort, storagePort,
                sscdPort, dinoPort,  null);
        userId = UUID.randomUUID();
    }

    private Image createIndexedImage(Long id, String keywords) {
        Image img = mock(Image.class);
        when(img.getId()).thenReturn(id);
        when(img.getKeywords()).thenReturn(keywords);
        when(img.getEmbedding()).thenReturn(floatsToBytes(new float[]{0.5f, 0.5f}));
        when(img.getEmbeddingDino()).thenReturn(floatsToBytes(new float[]{0.7f, 0.7f}));
        when(img.getGcsPath()).thenReturn("path/img.png");
        return img;
    }

    private byte[] floatsToBytes(float[] floats) {
        ByteBuffer buf = ByteBuffer.allocate(floats.length * 4);
        for (float f : floats) buf.putFloat(f);
        return buf.array();
    }

    @Test
    void startInternetScan_noIndexedImages_throwsBadRequest() {
        when(imageRepository.findByUserIdAndStatus(userId, ImageStatus.INDEXED))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.startInternetScan(userId))
                .hasMessageContaining("인덱싱된 이미지가 없습니다");
    }

    @Test
    void startInternetScan_hasImages_createsScanAndStartsAsync() {
        Image img = createIndexedImage(1L, "신발");
        when(imageRepository.findByUserIdAndStatus(userId, ImageStatus.INDEXED))
                .thenReturn(List.of(img));

        DetectionScan scan = new DetectionScan(userId, 1);
        when(scanRepository.save(any())).thenReturn(scan);

        service.startInternetScan(userId);

        verify(scanRepository).save(any(DetectionScan.class));
    }

    @Test
    void executeAsync_keywordSearchFindsMatch_savesResult() {
        Image img = createIndexedImage(1L, "나이키 신발");
        var searchResult = new SearchResult("http://img.com/shoe.jpg", "http://shop.com", "나이키");
        when(searchPort.searchByKeyword("나이키 신발", 50)).thenReturn(List.of(searchResult));

        // 다운로드된 이미지 → 동일 임베딩 (높은 유사도)
        when(downloadPort.download(anyString(), anyInt())).thenReturn(new byte[]{1, 2, 3});
        when(sscdPort.generateEmbedding(any())).thenReturn(new float[]{0.5f, 0.5f});
        when(dinoPort.generateEmbedding(any())).thenReturn(new float[]{0.7f, 0.7f});
        when(scanRepository.findById(any())).thenReturn(Optional.of(new DetectionScan(userId, 1)));

        service.executeInternetScanAsync(1L, userId, List.of(img));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InternetDetectionResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(resultRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getSearchEngine()).isEqualTo("NAVER");
    }

    @Test
    void executeAsync_keywordSearchEmpty_fallsBackToGoogle() {
        Image img = createIndexedImage(1L, "특수이미지");
        when(searchPort.searchByKeyword(anyString(), anyInt())).thenReturn(List.of());
        when(storagePort.load("path/img.png")).thenReturn(new byte[]{1, 2, 3});

        var reverseResult = new ReverseImageSearchPort.ReverseSearchResult(
                "http://found.com/img.jpg", "http://found.com", "Found");
        when(reverseSearchPort.searchByImage(any(), anyInt())).thenReturn(List.of(reverseResult));

        when(downloadPort.download(anyString(), anyInt())).thenReturn(new byte[]{1, 2, 3});
        when(sscdPort.generateEmbedding(any())).thenReturn(new float[]{0.5f, 0.5f});
        when(dinoPort.generateEmbedding(any())).thenReturn(new float[]{0.7f, 0.7f});
        when(scanRepository.findById(any())).thenReturn(Optional.of(new DetectionScan(userId, 1)));

        service.executeInternetScanAsync(1L, userId, List.of(img));

        verify(reverseSearchPort).searchByImage(any(), eq(50));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InternetDetectionResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(resultRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getSearchEngine()).isEqualTo("GOOGLE");
    }

    @Test
    void executeAsync_downloadFails_skipsResult() {
        Image img = createIndexedImage(1L, "신발");
        var sr = new SearchResult("http://img.com/bad.jpg", "http://shop.com", "제목");
        when(searchPort.searchByKeyword(anyString(), anyInt())).thenReturn(List.of(sr));
        when(downloadPort.download(anyString(), anyInt())).thenReturn(null);
        when(scanRepository.findById(any())).thenReturn(Optional.of(new DetectionScan(userId, 1)));

        service.executeInternetScanAsync(1L, userId, List.of(img));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InternetDetectionResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(resultRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void executeAsync_lowSimilarity_noMatch() {
        Image img = createIndexedImage(1L, "신발");
        var sr = new SearchResult("http://img.com/other.jpg", "http://shop.com", "다른이미지");
        when(searchPort.searchByKeyword(anyString(), anyInt())).thenReturn(List.of(sr));
        when(downloadPort.download(anyString(), anyInt())).thenReturn(new byte[]{1, 2, 3});
        // 매우 다른 임베딩 → 낮은 유사도
        when(sscdPort.generateEmbedding(any())).thenReturn(new float[]{-0.9f, -0.9f});
        when(dinoPort.generateEmbedding(any())).thenReturn(new float[]{-0.9f, -0.9f});
        when(scanRepository.findById(any())).thenReturn(Optional.of(new DetectionScan(userId, 1)));

        service.executeInternetScanAsync(1L, userId, List.of(img));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InternetDetectionResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(resultRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void executeAsync_exceptionThrown_failsScan() {
        Image img = createIndexedImage(1L, "신발");
        when(searchPort.searchByKeyword(anyString(), anyInt())).thenThrow(new RuntimeException("API error"));
        when(scanRepository.findById(1L)).thenReturn(Optional.of(new DetectionScan(userId, 1)));

        service.executeInternetScanAsync(1L, userId, List.of(img));

        verify(scanRepository).save(argThat(s -> "FAILED".equals(s.getStatus())));
    }
}
