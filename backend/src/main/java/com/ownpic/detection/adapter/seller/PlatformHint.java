package com.ownpic.detection.adapter.seller;

import java.util.Map;

/**
 * 플랫폼별 CSS 셀렉터 힌트.
 * 사업자 정보가 있는 HTML 영역을 특정하기 위한 힌트.
 */
public final class PlatformHint {

    public record Hint(
            String businessInfoSelector,
            String profileSelector,
            String storeUrlPattern
    ) {}

    private static final Map<String, Hint> HINTS = Map.ofEntries(
            // 네이버 스마트스토어: footer 영역의 dl/dt/dd 구조
            Map.entry("NAVER_SMARTSTORE", new Hint(
                    "footer, [class*=footer], [class*=Footer]", null, null)),

            // 네이버 블로그: 프로필 영역
            Map.entry("SNS_NAVER_BLOG", new Hint(
                    null, "[class*=profile], [class*=nick], .blog_author", null)),

            // 쿠팡: JS 렌더링 필요하여 footer fallback
            Map.entry("MARKET_COUPANG", new Hint(
                    "[class*=vendor], [class*=seller], footer", null, null)),

            // G마켓/옥션: 판매자 정보 영역
            Map.entry("MARKET_GMARKET", new Hint(
                    "[class*=seller], [class*=company], footer", null, null)),
            Map.entry("MARKET_AUCTION", new Hint(
                    "[class*=seller], [class*=company], footer", null, null)),

            // 11번가
            Map.entry("MARKET_11ST", new Hint(
                    "[class*=seller], [class*=biz_info], footer", null, null)),

            // SSG닷컴
            Map.entry("MARKET_SSG", new Hint(
                    "[class*=seller], footer", null, null)),

            // 무신사
            Map.entry("VERTICAL_MUSINSA", new Hint(
                    "[class*=seller], [class*=brand], footer", null, null)),

            // 에이블리
            Map.entry("VERTICAL_ABLY", new Hint(
                    "[class*=seller], [class*=store], footer", null, null)),

            // 오늘의집
            Map.entry("VERTICAL_TODAYHOUSE", new Hint(
                    "[class*=seller], [class*=store], footer", null, null)),

            // 마켓컬리
            Map.entry("VERTICAL_KURLY", new Hint(
                    "[class*=seller], [class*=company], footer", null, null)),

            // 인스타그램
            Map.entry("SNS_INSTAGRAM", new Hint(
                    null, "header, [class*=profile]", null)),

            // 티스토리
            Map.entry("SNS_TISTORY", new Hint(
                    null, "[class*=profile], [class*=author], .sidebar", null))
    );

    /** 기본 힌트: footer 영역 */
    private static final Hint DEFAULT_HINT = new Hint(
            "footer, #footer, .footer, [class*=footer], [class*=Footer], [class*=bottom_info], [class*=company_info]",
            null, null);

    public static Hint get(String platformType) {
        return HINTS.getOrDefault(platformType, DEFAULT_HINT);
    }
}
