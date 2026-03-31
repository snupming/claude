package com.ownpic.detection.adapter.google;

import com.ownpic.detection.port.ReverseImageSearchPort.ReverseSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoogleReverseImageSearchAdapterTest {

    @Mock GoogleSearchByImageStrategy searchByImage;
    @Mock GoogleLensUploadStrategy lensUpload;

    GoogleReverseImageSearchAdapter adapter;

    @BeforeEach
    void setUp() {
        var props = new GoogleScraperProperties(100, 0, 0, 3, 30);
        var rateLimiter = new ScraperRateLimiter(props);
        adapter = new GoogleReverseImageSearchAdapter(searchByImage, lensUpload, rateLimiter, props);
    }

    @Test
    void searchByImage_primarySucceeds_returnsResults() throws Exception {
        when(searchByImage.name()).thenReturn("SEARCH_BY_IMAGE");
        var expected = List.of(new ReverseSearchResult("http://img.com/a.jpg", "http://page.com", "Title"));
        when(searchByImage.search(any(), anyInt())).thenReturn(expected);

        List<ReverseSearchResult> results = adapter.searchByImage(new byte[]{1, 2, 3}, 10);

        assertThat(results).hasSize(1);
        verify(lensUpload, never()).search(any(), anyInt());
    }

    @Test
    void searchByImage_primaryCaptcha3Times_switchesToFallback() throws Exception {
        when(searchByImage.name()).thenReturn("SEARCH_BY_IMAGE");
        when(lensUpload.name()).thenReturn("LENS_UPLOAD");
        when(searchByImage.search(any(), anyInt()))
                .thenThrow(new GoogleSearchException("CAPTCHA", true, 429));
        var fallbackResults = List.of(new ReverseSearchResult("http://img.com/b.jpg", null, "Lens"));
        when(lensUpload.search(any(), anyInt())).thenReturn(fallbackResults);

        byte[] img = new byte[]{1, 2, 3};

        // 1번째, 2번째 CAPTCHA → 빈 리스트 (threshold=3 미달)
        assertThat(adapter.searchByImage(img, 10)).isEmpty();
        assertThat(adapter.searchByImage(img, 10)).isEmpty();
        // 3번째 → threshold 도달 → fallback 전환 → fallback 결과 반환
        List<ReverseSearchResult> results = adapter.searchByImage(img, 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).imageUrl()).contains("b.jpg");
    }

    @Test
    void searchByImage_nonCaptchaError_returnsEmptyWithoutSwitch() throws Exception {
        when(searchByImage.name()).thenReturn("SEARCH_BY_IMAGE");
        when(searchByImage.search(any(), anyInt()))
                .thenThrow(new GoogleSearchException("Network error", false, 500));

        List<ReverseSearchResult> results = adapter.searchByImage(new byte[]{1}, 10);

        assertThat(results).isEmpty();
        // 전략 전환 없이 같은 전략 유지
        verify(lensUpload, never()).search(any(), anyInt());
    }

    @Test
    void searchByImage_dailyLimitReached_returnsEmpty() throws Exception {
        var props = new GoogleScraperProperties(1, 0, 0, 3, 30);
        var rateLimiter = new ScraperRateLimiter(props);
        adapter = new GoogleReverseImageSearchAdapter(searchByImage, lensUpload, rateLimiter, props);

        when(searchByImage.name()).thenReturn("SEARCH_BY_IMAGE");
        when(searchByImage.search(any(), anyInt())).thenReturn(List.of());

        // 1번째: 성공
        adapter.searchByImage(new byte[]{1}, 10);
        // 2번째: 한도 초과
        List<ReverseSearchResult> results = adapter.searchByImage(new byte[]{1}, 10);
        assertThat(results).isEmpty();
    }

    @Test
    void searchByImage_bothStrategiesFail_returnsEmpty() throws Exception {
        when(searchByImage.name()).thenReturn("SEARCH_BY_IMAGE");
        when(lensUpload.name()).thenReturn("LENS_UPLOAD");
        when(searchByImage.search(any(), anyInt()))
                .thenThrow(new GoogleSearchException("CAPTCHA", true, 429));
        when(lensUpload.search(any(), anyInt()))
                .thenThrow(new GoogleSearchException("CAPTCHA too", true, 429));

        byte[] img = new byte[]{1};

        // 3번 실패 → 전략 전환 시도 → fallback도 실패
        adapter.searchByImage(img, 10);
        adapter.searchByImage(img, 10);
        List<ReverseSearchResult> results = adapter.searchByImage(img, 10);
        assertThat(results).isEmpty();
    }
}
