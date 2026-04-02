package com.ownpic.detection.adapter.seller;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * URL → 플랫폼/카테고리 판별.
 * 한국 이커머스 30+ 플랫폼 지원.
 */
public final class PlatformDetector {

    public record Platform(String type, String category) {}

    // 도메인 패턴 → (플랫폼타입, 카테고리)
    // LinkedHashMap: 순서 유지 (구체적 도메인 우선 매칭)
    private static final Map<String, Platform> DOMAIN_MAP = new LinkedHashMap<>();

    static {
        // === SNS / 소셜 (사업자 정보 없음 — 프로필만) ===
        put("instagram.com",        "SNS_INSTAGRAM",    "SNS");
        put("blog.naver.com",       "SNS_NAVER_BLOG",   "SNS");
        put("post.naver.com",       "SNS_NAVER_POST",   "SNS");
        put("tistory.com",          "SNS_TISTORY",      "SNS");
        put("youtube.com",          "SNS_YOUTUBE",       "SNS");
        put("youtu.be",             "SNS_YOUTUBE",       "SNS");
        put("tiktok.com",           "SNS_TIKTOK",        "SNS");
        put("twitter.com",          "SNS_X",             "SNS");
        put("x.com",                "SNS_X",             "SNS");
        put("daangn.com",           "SNS_DANGGEUN",      "SNS");
        put("talk.kakao.com",       "SNS_KAKAO",         "SNS");
        put("facebook.com",         "SNS_FACEBOOK",      "SNS");
        put("pinterest.com",        "SNS_PINTEREST",     "SNS");
        put("threads.net",          "SNS_THREADS",       "SNS");

        // === 네이버 커머스 ===
        put("smartstore.naver.com", "NAVER_SMARTSTORE",  "MARKETPLACE");
        put("brand.naver.com",      "NAVER_BRAND",       "MARKETPLACE");
        put("shopping.naver.com",   "NAVER_SHOPPING",    "MARKETPLACE");

        // === 종합 오픈마켓 ===
        put("coupang.com",          "MARKET_COUPANG",    "MARKETPLACE");
        put("gmarket.co.kr",        "MARKET_GMARKET",    "MARKETPLACE");
        put("auction.co.kr",        "MARKET_AUCTION",    "MARKETPLACE");
        put("11st.co.kr",           "MARKET_11ST",       "MARKETPLACE");
        put("ssg.com",              "MARKET_SSG",        "MARKETPLACE");
        put("wemakeprice.com",      "MARKET_WEMAKEPRICE","MARKETPLACE");
        put("tmon.co.kr",           "MARKET_TMON",       "MARKETPLACE");

        // === 소셜 쇼핑 (하이브리드) ===
        put("store.kakao.com",      "SOCIAL_KAKAO",      "SOCIAL");
        put("gift.kakao.com",       "SOCIAL_KAKAO",      "SOCIAL");
        put("toss.im",              "SOCIAL_TOSS",       "SOCIAL");

        // === 패션/뷰티 버티컬 ===
        put("musinsa.com",          "VERTICAL_MUSINSA",    "VERTICAL");
        put("a-bly.com",            "VERTICAL_ABLY",       "VERTICAL");
        put("kakaostyle.com",       "VERTICAL_ZIGZAG",     "VERTICAL");
        put("zigzag.kr",            "VERTICAL_ZIGZAG",     "VERTICAL");
        put("brandi.co.kr",         "VERTICAL_BRANDI",     "VERTICAL");
        put("hiver.co.kr",          "VERTICAL_HIVER",      "VERTICAL");
        put("queenit.co.kr",        "VERTICAL_QUEENIT",    "VERTICAL");
        put("oliveyoung.co.kr",     "VERTICAL_OLIVEYOUNG", "VERTICAL");
        put("wconcept.co.kr",       "VERTICAL_WCONCEPT",   "VERTICAL");
        put("29cm.co.kr",           "VERTICAL_29CM",       "VERTICAL");

        // === 리빙/식품/홈쇼핑 ===
        put("ohou.se",              "VERTICAL_TODAYHOUSE", "VERTICAL");
        put("kurly.com",            "VERTICAL_KURLY",      "VERTICAL");
        put("marketkurly.com",      "VERTICAL_KURLY",      "VERTICAL");
        put("gsshop.com",           "VERTICAL_GSSHOP",     "VERTICAL");
        put("cjonstyle.com",        "VERTICAL_CJONSTYLE",  "VERTICAL");
        put("lotteimall.com",       "VERTICAL_LOTTE",      "VERTICAL");
        put("lotteon.com",          "VERTICAL_LOTTE",      "VERTICAL");
        put("hmall.com",            "VERTICAL_HYUNDAI",    "VERTICAL");

        // === 해외 직구 ===
        put("aliexpress.com",       "OVERSEAS_ALI",    "OVERSEAS");
        put("aliexpress.co.kr",     "OVERSEAS_ALI",    "OVERSEAS");
        put("temu.com",             "OVERSEAS_TEMU",   "OVERSEAS");
        put("shein.com",            "OVERSEAS_SHEIN",  "OVERSEAS");
        put("amazon.com",           "OVERSEAS_AMAZON", "OVERSEAS");
        put("amazon.co.jp",         "OVERSEAS_AMAZON", "OVERSEAS");
    }

    private static void put(String domain, String type, String category) {
        DOMAIN_MAP.put(domain, new Platform(type, category));
    }

    public static Platform detect(String url) {
        if (url == null || url.isBlank()) {
            return new Platform("GENERIC", "GENERIC");
        }

        try {
            String host = URI.create(url).getHost();
            if (host == null) return new Platform("GENERIC", "GENERIC");
            host = host.toLowerCase();
            if (host.startsWith("www.")) host = host.substring(4);
            if (host.startsWith("m.")) host = host.substring(2);

            // 정확 매칭
            Platform exact = DOMAIN_MAP.get(host);
            if (exact != null) return exact;

            // 서브도메인 매칭 (예: shop.musinsa.com → musinsa.com)
            for (var entry : DOMAIN_MAP.entrySet()) {
                if (host.endsWith("." + entry.getKey()) || host.equals(entry.getKey())) {
                    return entry.getValue();
                }
            }

            return new Platform("GENERIC", "GENERIC");
        } catch (Exception e) {
            return new Platform("GENERIC", "GENERIC");
        }
    }
}
