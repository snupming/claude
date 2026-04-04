package com.ownpic.detection.adapter.seller;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 네이버 스마트스토어 전용 판매자 정보 추출기.
 *
 * 상품 URL에서 스토어명 추출 → 스토어 메인 페이지 접속 → footer 파싱.
 * CAPTCHA 없이 가능한 정보: 상호명 + 대표자명
 * __NEXT_DATA__ JSON에서 companyInfo가 있으면 추가 정보 추출.
 */
@Component
public class NaverSmartStoreExtractor {

    private static final Logger log = LoggerFactory.getLogger(NaverSmartStoreExtractor.class);

    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    );

    // 스마트스토어 판매자 라인 패턴: "주식회사 토리토미  이승훈  인증"
    // 또는 "(주)OO  홍길동  인증"
    private static final Pattern SELLER_LINE = Pattern.compile(
            "(?:주식회사|\\(주\\)|\\(유\\))\\s*([가-힣a-zA-Z0-9\\s]{2,20})\\s+([가-힣]{2,4})\\s+인증");

    // __NEXT_DATA__ JSON 내 companyInfo 키
    private static final Pattern JSON_COMPANY_NAME = Pattern.compile(
            "\"companyName\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern JSON_REPRESENTATIVE = Pattern.compile(
            "\"representativeName\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern JSON_BIZ_NUMBER = Pattern.compile(
            "\"businessRegistrationNumber\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern JSON_ADDRESS = Pattern.compile(
            "\"address\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern JSON_PHONE = Pattern.compile(
            "\"phoneNumber\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern JSON_EMAIL = Pattern.compile(
            "\"email\"\\s*:\\s*\"([^\"]+)\"");

    /**
     * 스마트스토어 URL에서 판매자 정보 추출.
     */
    public SellerInfo extract(String url) {
        if (url == null || url.isBlank()) return null;

        try {
            String storeName = extractStoreName(url);
            if (storeName == null) {
                log.info("[SmartStore] 스토어명 추출 실패: {}", url);
                return null;
            }

            String storeUrl = "https://smartstore.naver.com/" + storeName;
            log.info("[SmartStore] 스토어 메인 접속: {} (storeName={})", storeUrl, storeName);

            Document doc = Jsoup.connect(storeUrl)
                    .userAgent(USER_AGENTS.get((int) (Math.random() * USER_AGENTS.size())))
                    .timeout(10_000)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .get();

            // 1차: __NEXT_DATA__ JSON에서 companyInfo 추출
            SellerInfo jsonInfo = extractFromNextData(doc, storeUrl);
            if (jsonInfo != null && jsonInfo.hasBusinessInfo()) {
                log.info("[SmartStore] __NEXT_DATA__에서 추출 성공: {} / {}", jsonInfo.sellerName(), jsonInfo.representativeName());
                return jsonInfo;
            }

            // 2차: footer 판매자 라인 파싱
            SellerInfo footerInfo = extractFromFooter(doc, storeUrl);
            if (footerInfo != null && footerInfo.sellerName() != null) {
                log.info("[SmartStore] footer에서 추출 성공: {} / {}", footerInfo.sellerName(), footerInfo.representativeName());
                return footerInfo;
            }

            // 3차: 페이지 title에서 스토어명 추출 (최후 fallback)
            String title = doc.title();
            if (title != null && !title.isBlank()) {
                // "lovbong - 네이버" → "lovbong"
                String cleanTitle = title.split("[-–|]")[0].trim();
                log.info("[SmartStore] title fallback: {}", cleanTitle);
                return new SellerInfo("NAVER_SMARTSTORE", "MARKETPLACE",
                        cleanTitle, null, null, null, null, null, storeUrl);
            }

            return null;
        } catch (Exception e) {
            log.warn("[SmartStore] 추출 실패: {} — {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * __NEXT_DATA__ JSON에서 companyInfo 추출.
     * 스마트스토어는 Next.js 기반이라 서버 렌더링 데이터가 JSON으로 포함됨.
     */
    private SellerInfo extractFromNextData(Document doc, String storeUrl) {
        Elements scripts = doc.select("script#__NEXT_DATA__");
        if (scripts.isEmpty()) {
            // id가 없는 경우 내용으로 탐색
            scripts = doc.select("script");
        }

        for (Element script : scripts) {
            String text = script.html();
            if (!text.contains("companyName") && !text.contains("businessRegistration")) continue;

            String companyName = extractJsonValue(JSON_COMPANY_NAME, text);
            String representative = extractJsonValue(JSON_REPRESENTATIVE, text);
            String bizNumber = extractJsonValue(JSON_BIZ_NUMBER, text);
            String address = extractJsonValue(JSON_ADDRESS, text);
            String phone = extractJsonValue(JSON_PHONE, text);
            String email = extractJsonValue(JSON_EMAIL, text);

            if (companyName != null || bizNumber != null) {
                return new SellerInfo("NAVER_SMARTSTORE", "MARKETPLACE",
                        companyName, bizNumber, representative, address, phone, email, storeUrl);
            }
        }
        return null;
    }

    /**
     * footer 영역에서 판매자 라인 파싱.
     * 패턴: "주식회사 토리토미  이승훈  인증  [판매자 상세정보 확인]"
     */
    private SellerInfo extractFromFooter(Document doc, String storeUrl) {
        // footer 바로 위 영역 탐색
        String bodyText = doc.body().text();

        // "주식회사 OO  홍길동  인증" 패턴 매칭
        Matcher m = SELLER_LINE.matcher(bodyText);
        if (m.find()) {
            String sellerName = m.group(1).trim();
            String representative = m.group(2).trim();
            return new SellerInfo("NAVER_SMARTSTORE", "MARKETPLACE",
                    "주식회사 " + sellerName, null, representative, null, null, null, storeUrl);
        }

        // "(주)OO" 패턴 없이 "OO  홍길동  인증" 형태
        Pattern simplePattern = Pattern.compile("([가-힣a-zA-Z0-9]{2,20})\\s+([가-힣]{2,4})\\s+인증");
        Matcher sm = simplePattern.matcher(bodyText);
        if (sm.find()) {
            // "인증" 앞의 이름이 대표자, 그 앞이 상호
            return new SellerInfo("NAVER_SMARTSTORE", "MARKETPLACE",
                    sm.group(1).trim(), null, sm.group(2).trim(), null, null, null, storeUrl);
        }

        return null;
    }

    /**
     * URL에서 스토어명(path 첫 번째 세그먼트) 추출.
     * smartstore.naver.com/lovbong/products/123 → "lovbong"
     * brand.naver.com/toritomi/products/456 → "toritomi"
     */
    public String extractStoreName(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null) return null;

            // smartstore.naver.com 또는 brand.naver.com
            if (!host.contains("smartstore.naver.com") && !host.contains("brand.naver.com")) {
                return null;
            }

            String path = uri.getPath();
            if (path == null || path.equals("/")) return null;

            String[] segments = path.split("/");
            // segments[0] = "", segments[1] = storeName
            if (segments.length >= 2 && !segments[1].isBlank()) {
                return segments[1];
            }
        } catch (Exception e) {
            log.warn("[SmartStore] URL 파싱 실패: {}", url);
        }
        return null;
    }

    private String extractJsonValue(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }
}
