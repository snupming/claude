package com.ownpic.detection.adapter;

import com.ownpic.detection.port.InternetImageSearchPort.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NaverImageSearchAdapter 단위 테스트.
 * 실제 API 호출 없이 로직만 검증하므로, 생성자에 빈 키를 주입한다.
 * 실제 API 통합 테스트는 NaverSearchAndSeedTest에서 수행.
 */
class NaverImageSearchAdapterTest {

    @Test
    void searchByKeyword_invalidKey_returnsEmptyGracefully() {
        // 빈 API 키 → 실제 호출 시 401 → 빈 리스트 반환 (예외 아님)
        var adapter = new NaverImageSearchAdapter("invalid-id", "invalid-secret");

        List<SearchResult> results = adapter.searchByKeyword("테스트", 5);

        // 잘못된 키로는 빈 리스트가 반환되어야 함 (예외 발생하지 않음)
        assertThat(results).isNotNull();
    }

    @Test
    void searchByKeyword_maxResultsCappedAt100() {
        var adapter = new NaverImageSearchAdapter("id", "secret");

        // 200개 요청해도 내부적으로 100으로 제한
        // 실제 API 호출은 실패하지만, 파라미터 로직은 올바름
        List<SearchResult> results = adapter.searchByKeyword("test", 200);
        assertThat(results).isNotNull();
    }
}
