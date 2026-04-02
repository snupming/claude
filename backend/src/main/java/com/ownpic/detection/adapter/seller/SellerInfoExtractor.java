package com.ownpic.detection.adapter.seller;

import com.ownpic.detection.adapter.seller.PlatformDetector.Platform;
import com.ownpic.detection.adapter.seller.PlatformHint.Hint;
import com.ownpic.detection.domain.InternetDetectionResult;
import com.ownpic.detection.domain.InternetDetectionResultRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 판매자/침해자 정보 추출 엔진.
 *
 * 한국 전자상거래법 제13조에 의해 모든 온라인 판매자는 사업자 정보를 공개해야 함.
 * 3단계 전략:
 *   1. 플랫폼별 CSS 셀렉터로 영역 특정
 *   2. dt/dd, th/td 구조 파싱
 *   3. 정규식으로 fallback 추출
 */
@Service
public class SellerInfoExtractor {

    private static final Logger log = LoggerFactory.getLogger(SellerInfoExtractor.class);

    private final InternetDetectionResultRepository resultRepository;

    // 정규식 패턴 — 한국 사업자 정보 공통
    private static final Pattern BIZ_NUMBER = Pattern.compile("(\\d{3}-\\d{2}-\\d{5})");
    private static final Pattern REPRESENTATIVE = Pattern.compile("대표[자]?\\s*[：:.]?\\s*([가-힣]{2,4})");
    private static final Pattern PHONE = Pattern.compile("(0\\d{1,2}[-.)\\s]\\d{3,4}[-.)\\s]\\d{4})");
    private static final Pattern EMAIL = Pattern.compile("([\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,})");
    private static final Pattern STORE_NAME = Pattern.compile("(?:상호[명]?|회사[명]?|업체[명]?)\\s*[：:.]?\\s*([^\\n<,]{2,50})");
    private static final Pattern ADDRESS = Pattern.compile("(?:소재지|사업장[\\s]*주소|주소)\\s*[：:.]?\\s*([^\\n<]{5,100})");

    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    );

    public SellerInfoExtractor(InternetDetectionResultRepository resultRepository) {
        this.resultRepository = resultRepository;
    }

    /**
     * 탐지 결과 목록에서 판매자 정보를 비동기로 추출하여 DB에 저장.
     */
    @Async
    public void extractAndSave(List<InternetDetectionResult> results) {
        for (var result : results) {
            try {
                // sourcePageUrl 우선, 없으면 foundImageUrl에서 도메인 추출
                String targetUrl = result.getSourcePageUrl();
                if (targetUrl == null || targetUrl.isBlank()) {
                    targetUrl = result.getFoundImageUrl();
                }

                SellerInfo info = extract(targetUrl);
                applyToResult(result, info);
                resultRepository.save(result);
                log.info("[SellerExtractor] {} → {} ({})",
                        targetUrl, info.sellerName(), info.platformType());
            } catch (Exception e) {
                log.warn("[SellerExtractor] 추출 실패: {} — {}", result.getSourcePageUrl(), e.getMessage());
                // 플랫폼 타입이라도 저장
                String fallbackUrl = result.getSourcePageUrl() != null ? result.getSourcePageUrl() : result.getFoundImageUrl();
                Platform platform = PlatformDetector.detect(fallbackUrl);
                result.setPlatformType(platform.type());
                resultRepository.save(result);
            }
        }
    }

    /**
     * URL에서 판매자 정보 추출.
     */
    public SellerInfo extract(String url) {
        if (url == null || url.isBlank()) {
            return SellerInfo.empty("GENERIC", "GENERIC");
        }

        Platform platform = PlatformDetector.detect(url);
        Hint hint = PlatformHint.get(platform.type());

        // SNS는 프로필만 추출
        if ("SNS".equals(platform.category())) {
            return extractSnsProfile(url, platform, hint);
        }

        // 해외는 도메인명만
        if ("OVERSEAS".equals(platform.category())) {
            return extractOverseas(url, platform);
        }

        // 마켓플레이스/버티컬/자사몰: 사업자 정보 추출
        return extractBusinessInfo(url, platform, hint);
    }

    private SellerInfo extractBusinessInfo(String url, Platform platform, Hint hint) {
        try {
            Document doc = fetchPage(url);

            // 1단계: CSS 셀렉터로 영역 특정
            String targetText = "";
            if (hint.businessInfoSelector() != null) {
                Elements els = doc.select(hint.businessInfoSelector());
                targetText = els.text();
            }

            // 2단계: dt/dd 구조 파싱 (가장 정확)
            SellerInfo dtddResult = parseDtDd(doc, platform);
            if (dtddResult != null && dtddResult.hasBusinessInfo()) {
                return dtddResult;
            }

            // 3단계: 정규식 fallback (footer 전체 텍스트)
            if (targetText.isBlank()) {
                Elements footer = doc.select("footer, #footer, .footer, [class*=footer]");
                targetText = footer.isEmpty() ? doc.body().text() : footer.text();
            }

            return new SellerInfo(
                    platform.type(),
                    platform.category(),
                    extractFirst(STORE_NAME, targetText),
                    extractFirst(BIZ_NUMBER, targetText),
                    extractFirst(REPRESENTATIVE, targetText),
                    extractFirst(ADDRESS, targetText),
                    extractFirst(PHONE, targetText),
                    extractFirst(EMAIL, targetText),
                    extractStoreUrl(url, platform)
            );
        } catch (Exception e) {
            log.debug("[SellerExtractor] 사업자 정보 추출 실패: {}", e.getMessage());
            return SellerInfo.empty(platform.type(), platform.category());
        }
    }

    /**
     * dt/dd 또는 th/td 구조에서 사업자 정보 파싱.
     * 네이버 스마트스토어, 대부분의 한국 쇼핑몰이 이 구조 사용.
     */
    private SellerInfo parseDtDd(Document doc, Platform platform) {
        String sellerName = null, bizNumber = null, representative = null;
        String address = null, phone = null, email = null;

        // dt/dd 쌍 파싱
        Elements dts = doc.select("dt");
        for (Element dt : dts) {
            String label = dt.text().trim();
            Element dd = dt.nextElementSibling();
            if (dd == null || !"dd".equals(dd.tagName())) continue;
            String value = dd.text().trim();
            if (value.isEmpty()) continue;

            if (label.contains("상호") || label.contains("회사") || label.contains("업체")) sellerName = value;
            else if (label.contains("사업자") && label.contains("번호")) bizNumber = value;
            else if (label.contains("대표")) representative = value;
            else if (label.contains("소재지") || label.contains("주소")) address = value;
            else if (label.contains("전화") || label.contains("연락")) phone = value;
            else if (label.contains("이메일") || label.contains("E-mail") || label.contains("email")) email = value;
        }

        // th/td 쌍 파싱 (테이블 구조)
        Elements ths = doc.select("th");
        for (Element th : ths) {
            String label = th.text().trim();
            Element td = th.nextElementSibling();
            if (td == null || !"td".equals(td.tagName())) continue;
            String value = td.text().trim();
            if (value.isEmpty()) continue;

            if (sellerName == null && (label.contains("상호") || label.contains("회사"))) sellerName = value;
            else if (bizNumber == null && label.contains("사업자") && label.contains("번호")) bizNumber = value;
            else if (representative == null && label.contains("대표")) representative = value;
            else if (address == null && (label.contains("소재지") || label.contains("주소"))) address = value;
            else if (phone == null && (label.contains("전화") || label.contains("연락"))) phone = value;
            else if (email == null && (label.contains("이메일") || label.contains("mail"))) email = value;
        }

        if (sellerName == null && bizNumber == null && representative == null) {
            return null;
        }

        return new SellerInfo(platform.type(), platform.category(),
                sellerName, bizNumber, representative, address, phone, email,
                extractStoreUrl(null, platform));
    }

    private SellerInfo extractSnsProfile(String url, Platform platform, Hint hint) {
        try {
            Document doc = fetchPage(url);
            String profileName = null;

            if (hint.profileSelector() != null) {
                Elements els = doc.select(hint.profileSelector());
                if (!els.isEmpty()) {
                    profileName = els.first().text().trim();
                    if (profileName.length() > 100) profileName = profileName.substring(0, 100);
                }
            }

            // fallback: title 태그에서 추출
            if (profileName == null || profileName.isBlank()) {
                profileName = doc.title();
            }

            // URL에서 계정명 추출
            String accountId = extractAccountFromUrl(url, platform.type());

            return new SellerInfo(
                    platform.type(), platform.category(),
                    profileName != null ? profileName : accountId,
                    null, null, null, null, null, url
            );
        } catch (Exception e) {
            String accountId = extractAccountFromUrl(url, platform.type());
            return new SellerInfo(platform.type(), platform.category(),
                    accountId, null, null, null, null, null, url);
        }
    }

    private SellerInfo extractOverseas(String url, Platform platform) {
        // 해외 플랫폼은 크롤링 제한이 심하므로 도메인명만 추출
        String storeName = switch (platform.type()) {
            case "OVERSEAS_ALI" -> "알리익스프레스 판매자";
            case "OVERSEAS_TEMU" -> "테무 판매자";
            case "OVERSEAS_SHEIN" -> "쉬인 판매자";
            case "OVERSEAS_AMAZON" -> "아마존 판매자";
            default -> "해외 판매자";
        };
        return new SellerInfo(platform.type(), platform.category(),
                storeName, null, null, null, null, null, url);
    }

    private String extractAccountFromUrl(String url, String platformType) {
        try {
            String path = new java.net.URI(url).getPath();
            if (path == null || path.equals("/")) return null;
            String[] parts = path.split("/");
            for (String part : parts) {
                if (!part.isBlank() && !part.equals("p") && !part.equals("reel")
                        && !part.equals("status") && !part.equals("watch")) {
                    return "@" + part;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String extractStoreUrl(String url, Platform platform) {
        if ("NAVER_SMARTSTORE".equals(platform.type()) && url != null) {
            try {
                String path = new java.net.URI(url).getPath();
                String[] parts = path.split("/");
                if (parts.length >= 2) {
                    return "https://smartstore.naver.com/" + parts[1];
                }
            } catch (Exception ignored) {}
        }
        return url;
    }

    private Document fetchPage(String url) throws Exception {
        String ua = USER_AGENTS.get((int) (Math.random() * USER_AGENTS.size()));
        return Jsoup.connect(url)
                .userAgent(ua)
                .timeout(10_000)
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .get();
    }

    private String extractFirst(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private void applyToResult(InternetDetectionResult result, SellerInfo info) {
        result.setPlatformType(info.platformType());
        result.setSellerName(info.sellerName());
        result.setBusinessRegNumber(info.businessRegNumber());
        result.setRepresentativeName(info.representativeName());
        result.setBusinessAddress(info.businessAddress());
        result.setContactPhone(info.contactPhone());
        result.setContactEmail(info.contactEmail());
        result.setStoreUrl(info.storeUrl());
    }
}
