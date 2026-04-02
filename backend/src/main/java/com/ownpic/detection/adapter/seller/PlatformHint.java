package com.ownpic.detection.adapter.seller;

import java.util.Map;

/**
 * 플랫폼별 CSS 셀렉터 힌트.
 *
 * isMarketplace=true인 플랫폼은 통신판매중개자.
 * → footer = 플랫폼 본사 정보 (쓸모없음)
 * → 상품 페이지 내 "판매자 정보" 섹션을 크롤링해야 실제 판매자 정보를 얻을 수 있음.
 */
public final class PlatformHint {

    public record Hint(
            boolean isMarketplace,        // 통신판매중개자 여부
            String sellerSectionSelector, // 상품 페이지 내 판매자 정보 영역 (오픈마켓용)
            String businessInfoSelector,  // footer/사업자 정보 영역 (자사몰용)
            String profileSelector        // SNS 프로필 영역
    ) {}

    private static final Map<String, Hint> HINTS = Map.ofEntries(
            // === 통신판매중개자 (오픈마켓) — 상품 페이지 내 판매자 정보 크롤링 ===

            // 쿠팡: 상품 페이지 하단 '상품정보 고시' 아래 판매자 정보
            // class="prod-seller-info" 또는 class="seller-package-info" 내부 table
            Map.entry("MARKET_COUPANG", new Hint(true,
                    ".prod-seller-info, .seller-package-info, [class*=vendor-info], [class*=seller-info]",
                    null, null)),

            // G마켓: 판매자 ID 클릭 → 팝업 또는 상세페이지 '판매자 정보' 탭
            // class="section__footer-info" 또는 address 태그
            Map.entry("MARKET_GMARKET", new Hint(true,
                    ".section__footer-info, [class*=seller_info], [class*=seller-info], address",
                    null, null)),

            // 옥션: G마켓과 동일 계열 (신세계)
            Map.entry("MARKET_AUCTION", new Hint(true,
                    ".section__footer-info, [class*=seller_info], [class*=seller-info], address",
                    null, null)),

            // 11번가: 상세페이지 하단 '반품/교환정보' 탭 — iframe 내부일 수 있음
            // Selenium에서 iframe 전환 후 탐색 필요
            Map.entry("MARKET_11ST", new Hint(true,
                    "[class*=seller], [class*=biz_info], .c_product_seller, #sellerInfo",
                    null, null)),

            // SSG닷컴: 판매자 정보
            Map.entry("MARKET_SSG", new Hint(true,
                    "[class*=seller], [class*=vendor], .cdtl_seller_info",
                    null, null)),

            // 위메프
            Map.entry("MARKET_WEMAKEPRICE", new Hint(true,
                    "[class*=seller], [class*=vendor]",
                    null, null)),

            // 티몬
            Map.entry("MARKET_TMON", new Hint(true,
                    "[class*=seller], [class*=partner]",
                    null, null)),

            // === 통신판매중개자 (버티컬 마켓플레이스) ===

            // 무신사: 브랜드/셀러 정보
            Map.entry("VERTICAL_MUSINSA", new Hint(true,
                    "[class*=seller], [class*=brand-info], .product_article_seller",
                    null, null)),

            // 에이블리: 셀러 정보
            Map.entry("VERTICAL_ABLY", new Hint(true,
                    "[class*=seller], [class*=store-info], [class*=shop-info]",
                    null, null)),

            // 지그재그
            Map.entry("VERTICAL_ZIGZAG", new Hint(true,
                    "[class*=seller], [class*=store], [class*=shop]",
                    null, null)),

            // 브랜디
            Map.entry("VERTICAL_BRANDI", new Hint(true,
                    "[class*=seller], [class*=shop]",
                    null, null)),

            // 오늘의집: 파트너 정보
            Map.entry("VERTICAL_TODAYHOUSE", new Hint(true,
                    "[class*=seller], [class*=store], [class*=partner]",
                    null, null)),

            // 마켓컬리: 판매자 정보
            Map.entry("VERTICAL_KURLY", new Hint(true,
                    "[class*=seller], [class*=company-info]",
                    null, null)),

            // === 직접 판매자 (자사몰) — footer = 판매자 정보 ===

            // 네이버 스마트스토어: footer + seller_info 영역 + origin_info(해외구매대행)
            Map.entry("NAVER_SMARTSTORE", new Hint(false,
                    null,
                    ".seller_info, .origin_info, footer, [class*=footer], [class*=Footer]", null)),

            // 네이버 브랜드스토어
            Map.entry("NAVER_BRAND", new Hint(false,
                    null,
                    "footer, [class*=footer]", null)),

            // 카카오 선물하기/톡스토어
            Map.entry("SOCIAL_KAKAO", new Hint(true,
                    "[class*=seller], [class*=brand]",
                    null, null)),

            // === SNS (프로필만) ===

            Map.entry("SNS_NAVER_BLOG", new Hint(false,
                    null, null,
                    "[class*=profile], [class*=nick], .blog_author")),
            Map.entry("SNS_INSTAGRAM", new Hint(false,
                    null, null,
                    "header, [class*=profile]")),
            Map.entry("SNS_TISTORY", new Hint(false,
                    null, null,
                    "[class*=profile], [class*=author], .sidebar"))
    );

    /** 기본 힌트: 자사몰로 간주, footer 크롤링 */
    private static final Hint DEFAULT_HINT = new Hint(false,
            null,
            "footer, #footer, .footer, [class*=footer], [class*=Footer], [class*=bottom_info], [class*=company_info]",
            null);

    public static Hint get(String platformType) {
        return HINTS.getOrDefault(platformType, DEFAULT_HINT);
    }
}
