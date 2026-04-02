package com.ownpic.detection.adapter.seller;

import com.ownpic.detection.adapter.google.SeleniumLensAdapter;
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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 판매자/침해자 정보 추출 엔진.
 *
 * 전략:
 * 1. sourcePageUrl이 있으면 → 해당 페이지 직접 접속 → 사업자 정보 크롤링
 * 2. sourcePageUrl이 없으면 → foundImageUrl 도메인 추출 → 메인 페이지 시도
 * 3. Selenium 사용 가능하면 → JS 동적 로딩 페이지도 대응
 * 4. 정규식 + dt/dd 파싱으로 사업자번호/대표자/주소/연락처 추출
 */
@Service
public class SellerInfoExtractor {

    private static final Logger log = LoggerFactory.getLogger(SellerInfoExtractor.class);

    private final InternetDetectionResultRepository resultRepository;
    private final SeleniumLensAdapter seleniumAdapter;

    // 정규식 패턴 — 한국 사업자 정보 공통
    private static final Pattern BIZ_NUMBER = Pattern.compile("(\\d{3}-\\d{2}-\\d{5})");
    private static final Pattern REPRESENTATIVE = Pattern.compile("대표[자]?\\s*[：:.]?\\s*([가-힣]{2,4})");
    private static final Pattern PHONE = Pattern.compile("(0\\d{1,2}[-.)\\s]\\d{3,4}[-.)\\s]\\d{4})");
    private static final Pattern EMAIL = Pattern.compile("([\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,})");
    private static final Pattern STORE_NAME = Pattern.compile("(?:상호[명]?|회사[명]?|업체[명]?)\\s*[：:.]?\\s*([^\\n<,]{2,50})");
    private static final Pattern ADDRESS = Pattern.compile("(?:소재지|사업장[\\s]*주소|주소)\\s*[：:.]?\\s*([^\\n<]{5,100})");
    // 통신판매번호: 제YYYY-지역-NNNN호
    private static final Pattern MAIL_ORDER_NUMBER = Pattern.compile("제?\\d{4}-[가-힣]{2,5}-\\d{4}호?");

    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    );

    public SellerInfoExtractor(InternetDetectionResultRepository resultRepository,
                               SeleniumLensAdapter seleniumAdapter) {
        this.resultRepository = resultRepository;
        this.seleniumAdapter = seleniumAdapter;
    }

    @Async
    public void extractAndSave(List<InternetDetectionResult> results) {
        Map<String, SellerInfo> domainCache = new HashMap<>();

        for (var result : results) {
            try {
                // 크롤링 대상 URL 결정
                String targetUrl = result.getSourcePageUrl();
                if (targetUrl == null || targetUrl.isBlank()) {
                    // sourcePageUrl 없으면 이미지 URL 도메인의 메인 페이지 시도
                    targetUrl = deriveMainPageUrl(result.getFoundImageUrl());
                }

                String domain = extractDomain(targetUrl);
                SellerInfo info;
                if (domain != null && domainCache.containsKey(domain)) {
                    info = domainCache.get(domain);
                } else {
                    info = extractFromPage(targetUrl);
                    if (domain != null) domainCache.put(domain, info);
                }

                applyToResult(result, info);

                // bestGuessLabel, detectedEntity 저장 (Vision API에서 전달받은 것)
                resultRepository.save(result);
                log.info("[SellerExtractor] {} → 상호:{} / 사업자:{} / 대표:{} ({})",
                        targetUrl, info.sellerName(), info.businessRegNumber(),
                        info.representativeName(), info.platformType());
            } catch (Exception e) {
                log.warn("[SellerExtractor] 추출 실패: {} — {}", result.getFoundImageUrl(), e.getMessage());
                Platform platform = PlatformDetector.detect(
                        result.getSourcePageUrl() != null ? result.getSourcePageUrl() : result.getFoundImageUrl());
                result.setPlatformType(platform.type());
                resultRepository.save(result);
            }
        }
    }

    /**
     * 페이지 URL에 접속하여 사업자 정보 추출.
     * Selenium 사용 가능하면 JS 동적 로딩 대응, 아니면 JSoup fallback.
     */
    public SellerInfo extractFromPage(String url) {
        if (url == null || url.isBlank()) {
            return SellerInfo.empty("GENERIC", "GENERIC");
        }

        Platform platform = PlatformDetector.detect(url);
        Hint hint = PlatformHint.get(platform.type());

        if ("SNS".equals(platform.category())) {
            return extractSnsProfile(url, platform);
        }
        if ("OVERSEAS".equals(platform.category())) {
            return extractOverseas(url, platform);
        }

        // 1차: Selenium으로 접속 (JS 동적 로딩 대응)
        String pageSource = null;
        if (seleniumAdapter.isEnabled()) {
            pageSource = seleniumAdapter.fetchPageWithScroll(url);
        }

        // 2차: Selenium 실패 시 JSoup fallback
        Document doc;
        if (pageSource != null) {
            doc = Jsoup.parse(pageSource);
        } else {
            try {
                doc = fetchPageJsoup(url);
            } catch (Exception e) {
                log.debug("[SellerExtractor] 페이지 접속 실패: {}", e.getMessage());
                return SellerInfo.empty(platform.type(), platform.category());
            }
        }

        return parseBusinessInfo(doc, url, platform, hint);
    }

    /**
     * HTML Document에서 사업자 정보 파싱.
     *
     * 통신판매중개자(오픈마켓): 상품 페이지 내 "판매자 정보" 섹션 크롤링
     *   → footer는 플랫폼 본사 정보(쿠팡주식회사 등)이므로 무시
     * 자사몰/스마트스토어: footer 크롤링
     *   → footer = 실제 판매자 정보
     */
    private SellerInfo parseBusinessInfo(Document doc, String url, Platform platform, Hint hint) {
        String targetText = "";

        if (hint.isMarketplace()) {
            // ★ 오픈마켓: 상품 페이지 내 판매자 정보 섹션 크롤링 (footer 무시)
            log.debug("[SellerExtractor] 통신판매중개자 — 상품 페이지 내 판매자 정보 섹션 탐색: {}", platform.type());

            // 1차: 플랫폼별 CSS 셀렉터로 판매자 섹션 특정
            if (hint.sellerSectionSelector() != null) {
                Elements sellerSection = doc.select(hint.sellerSectionSelector());
                if (!sellerSection.isEmpty()) {
                    targetText = sellerSection.text();
                    log.debug("[SellerExtractor] 판매자 섹션 발견: {}자", targetText.length());
                }
            }

            // 2차: 범용 셀렉터로 판매자 정보 영역 탐색
            if (targetText.isBlank()) {
                Elements sellerAreas = doc.select(
                        "[class*=seller], [class*=vendor], [class*=partner], " +
                        "[id*=seller], [id*=vendor], [data-seller], " +
                        "[class*=shop-info], [class*=store-info]");
                if (!sellerAreas.isEmpty()) {
                    targetText = sellerAreas.text();
                }
            }

            // 3차: "판매자 정보", "사업자 정보" 텍스트가 있는 영역 탐색
            if (targetText.isBlank()) {
                for (Element el : doc.getAllElements()) {
                    String text = el.ownText();
                    if (text.contains("판매자 정보") || text.contains("사업자정보") || text.contains("판매자정보")) {
                        // 해당 요소의 부모 또는 다음 형제에서 정보 추출
                        Element parent = el.parent();
                        if (parent != null) {
                            targetText = parent.text();
                            break;
                        }
                    }
                }
            }
        } else {
            // 자사몰/스마트스토어: footer = 판매자 정보
            if (hint.businessInfoSelector() != null) {
                Elements els = doc.select(hint.businessInfoSelector());
                targetText = els.text();
            }
            if (targetText.isBlank()) {
                Elements footer = doc.select("footer, #footer, .footer, [class*=footer], [class*=Footer]");
                targetText = footer.isEmpty() ? "" : footer.text();
            }
        }

        // dt/dd, th/td 구조 파싱 (판매자 섹션 또는 footer 내)
        SellerInfo structured = parseStructured(doc, platform, hint.isMarketplace());
        if (structured != null && structured.hasBusinessInfo()) {
            return new SellerInfo(platform.type(), platform.category(),
                    structured.sellerName(), structured.businessRegNumber(),
                    structured.representativeName(), structured.businessAddress(),
                    structured.contactPhone(), structured.contactEmail(), url);
        }

        // 4차: HTML 소스 내 JSON 데이터에서 사업자 정보 추출
        // window.__INITIAL_STATE__, __NEXT_DATA__ 등에 JSON으로 박혀있는 경우
        if (targetText.isBlank() || !targetText.matches(".*\\d{3}-\\d{2}-\\d{5}.*")) {
            String jsonSellerInfo = extractFromEmbeddedJson(doc);
            if (jsonSellerInfo != null && !jsonSellerInfo.isBlank()) {
                targetText = jsonSellerInfo + " " + targetText;
            }
        }

        // 5차: 키워드 스캔 — "사업자등록번호" 포함 영역 집중 파싱
        if (targetText.isBlank() || !targetText.matches(".*\\d{3}-\\d{2}-\\d{5}.*")) {
            for (Element el : doc.getAllElements()) {
                if (el.ownText().contains("사업자등록번호") || el.ownText().contains("사업자 등록번호")) {
                    Element container = el.parent() != null ? el.parent() : el;
                    targetText = container.text();
                    break;
                }
            }
        }

        // 정규식 fallback
        if (targetText.isBlank()) {
            targetText = doc.body().text();
        }

        String sellerName = extractFirst(STORE_NAME, targetText);
        if (sellerName == null || sellerName.isBlank()) {
            sellerName = doc.title();
            if (sellerName != null && sellerName.length() > 50) sellerName = sellerName.substring(0, 50);
        }

        return new SellerInfo(
                platform.type(), platform.category(),
                sellerName,
                extractFirst(BIZ_NUMBER, targetText),
                extractFirst(REPRESENTATIVE, targetText),
                extractFirst(ADDRESS, targetText),
                extractFirst(PHONE, targetText),
                extractFirst(EMAIL, targetText),
                url
        );
    }

    /**
     * dt/dd, th/td 구조에서 사업자 정보 파싱.
     * 오픈마켓이면 판매자 섹션 내부만, 자사몰이면 전체 문서에서 탐색.
     */
    private SellerInfo parseStructured(Document doc, Platform platform, boolean isMarketplace) {
        String sellerName = null, bizNumber = null, representative = null;
        String address = null, phone = null, email = null;

        // 오픈마켓이면 판매자 섹션만 탐색 (footer 제외)
        Elements searchScope;
        if (isMarketplace) {
            searchScope = doc.select("[class*=seller], [class*=vendor], [class*=partner], [class*=shop-info], [class*=store-info]");
            if (searchScope.isEmpty()) searchScope = doc.select("body"); // fallback
        } else {
            searchScope = doc.select("body");
        }

        // dt/dd 쌍
        for (Element dt : searchScope.select("dt")) {
            String label = dt.text().trim();
            Element dd = dt.nextElementSibling();
            if (dd == null || !"dd".equals(dd.tagName())) continue;
            String value = dd.text().trim();
            if (value.isEmpty()) continue;
            mapLabelValue(label, value, m -> {
                // 이 방식은 final 제약이 있어서 배열로 우회
            });
            if (label.contains("상호") || label.contains("회사") || label.contains("업체")) sellerName = value;
            else if (label.contains("사업자") && (label.contains("번호") || label.contains("등록"))) bizNumber = value;
            else if (label.contains("대표")) representative = value;
            else if (label.contains("소재지") || label.contains("주소")) address = value;
            else if (label.contains("전화") || label.contains("연락") || label.contains("고객센터")) phone = value;
            else if (label.contains("이메일") || label.contains("mail")) email = value;
        }

        // th/td 쌍
        for (Element th : searchScope.select("th")) {
            String label = th.text().trim();
            Element td = th.nextElementSibling();
            if (td == null || !"td".equals(td.tagName())) continue;
            String value = td.text().trim();
            if (value.isEmpty()) continue;

            if (sellerName == null && (label.contains("상호") || label.contains("회사"))) sellerName = value;
            else if (bizNumber == null && label.contains("사업자")) bizNumber = value;
            else if (representative == null && label.contains("대표")) representative = value;
            else if (address == null && (label.contains("소재지") || label.contains("주소"))) address = value;
            else if (phone == null && (label.contains("전화") || label.contains("연락"))) phone = value;
            else if (email == null && (label.contains("이메일") || label.contains("mail"))) email = value;
        }

        if (sellerName == null && bizNumber == null && representative == null) return null;
        return new SellerInfo(null, null, sellerName, bizNumber, representative, address, phone, email, null);
    }

    private SellerInfo extractSnsProfile(String url, Platform platform) {
        String accountId = extractAccountFromUrl(url);
        try {
            Document doc = fetchPageJsoup(url);
            String profileName = doc.title();
            return new SellerInfo(platform.type(), platform.category(),
                    profileName != null ? profileName : accountId,
                    null, null, null, null, null, url);
        } catch (Exception e) {
            return new SellerInfo(platform.type(), platform.category(),
                    accountId, null, null, null, null, null, url);
        }
    }

    private SellerInfo extractOverseas(String url, Platform platform) {
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

    /**
     * 이미지 URL에서 메인 페이지 URL 추론.
     * CDN 하드코딩 없이 동적으로 도메인 추출.
     */
    private String deriveMainPageUrl(String imageUrl) {
        if (imageUrl == null) return null;
        try {
            java.net.URI uri = new java.net.URI(imageUrl);
            String host = uri.getHost();
            if (host == null) return null;
            host = host.toLowerCase();

            // CDN 접두사 동적 제거
            if (isCdnDomain(host)) {
                String stripped = stripCdnPrefix(host);
                if (stripped != null) host = stripped;
            }

            return "https://" + host + "/";
        } catch (Exception e) {
            return null;
        }
    }

    /** CDN 도메인인지 동적 판별 */
    private boolean isCdnDomain(String host) {
        return host.startsWith("cdn.") || host.startsWith("img.") || host.startsWith("image.")
                || host.startsWith("images.") || host.startsWith("static.")
                || host.startsWith("media.") || host.startsWith("thumbnail")
                || host.contains("cloudfront.net") || host.contains("akamai")
                || host.contains("cloudinary.com") || host.contains("imgix.net")
                || host.contains("pstatic.net") || host.contains("ssgcdn.com");
    }

    /** CDN 접두사 제거 → 원본 도메인 추론 */
    private String stripCdnPrefix(String host) {
        // 범용 CDN (원본 도메인 추론 불가)
        if (host.contains("cloudfront.net") || host.contains("akamai")
                || host.contains("cloudinary.com") || host.contains("imgix.net")
                || host.contains("pstatic.net") || host.contains("ssgcdn.com")) {
            return null; // 추론 불가 → 스킵
        }

        // cdn.example.com → example.com
        String stripped = host.replaceFirst("^(cdn|img|image|images|static|media|thumbnail)\\d*\\.", "");
        if (!stripped.equals(host) && stripped.contains(".")) {
            return stripped;
        }
        return null;
    }

    /**
     * HTML 소스 내 script 태그에 포함된 JSON 데이터에서 사업자 정보 추출.
     * 많은 SPA 프레임워크(Next.js, Nuxt 등)가 window.__INITIAL_STATE__, __NEXT_DATA__ 등에
     * 사업자 정보를 JSON으로 포함시킴.
     */
    private String extractFromEmbeddedJson(Document doc) {
        for (Element script : doc.select("script")) {
            String scriptText = script.html();
            if (scriptText.length() < 100) continue;

            // 사업자등록번호 패턴이 있는 script만 처리
            if (!BIZ_NUMBER.matcher(scriptText).find()) continue;

            // JSON에서 관련 키워드 주변 텍스트 추출
            StringBuilder extracted = new StringBuilder();

            // 사업자번호
            Matcher bizMatcher = BIZ_NUMBER.matcher(scriptText);
            if (bizMatcher.find()) extracted.append("사업자등록번호: ").append(bizMatcher.group()).append(" ");

            // 대표자
            Matcher repMatcher = Pattern.compile("\"(?:ceo|representative|대표[자]?)[\"\\s:]+\"([^\"]+)\"").matcher(scriptText);
            if (repMatcher.find()) extracted.append("대표자: ").append(repMatcher.group(1)).append(" ");

            // 상호명
            Matcher nameMatcher = Pattern.compile("\"(?:companyName|storeName|상호[명]?|sellerName)[\"\\s:]+\"([^\"]+)\"").matcher(scriptText);
            if (nameMatcher.find()) extracted.append("상호: ").append(nameMatcher.group(1)).append(" ");

            // 연락처
            Matcher phoneMatcher = PHONE.matcher(scriptText);
            if (phoneMatcher.find()) extracted.append("전화: ").append(phoneMatcher.group()).append(" ");

            // 이메일
            Matcher emailMatcher = EMAIL.matcher(scriptText);
            if (emailMatcher.find()) extracted.append("이메일: ").append(emailMatcher.group()).append(" ");

            if (!extracted.isEmpty()) {
                log.debug("[SellerExtractor] JSON 데이터에서 사업자 정보 발견: {}", extracted);
                return extracted.toString();
            }
        }
        return null;
    }

    private String extractAccountFromUrl(String url) {
        try {
            String path = new java.net.URI(url).getPath();
            if (path == null || path.equals("/")) return null;
            String[] parts = path.split("/");
            for (String part : parts) {
                if (!part.isBlank() && !part.equals("p") && !part.equals("reel")) {
                    return "@" + part;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String extractDomain(String url) {
        if (url == null) return null;
        try { return new java.net.URI(url).getHost(); }
        catch (Exception e) { return null; }
    }

    private Document fetchPageJsoup(String url) throws Exception {
        String ua = USER_AGENTS.get((int) (Math.random() * USER_AGENTS.size()));
        return Jsoup.connect(url)
                .userAgent(ua).timeout(10_000)
                .followRedirects(true).ignoreHttpErrors(true).get();
    }

    private String extractFirst(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private void mapLabelValue(String label, String value, java.util.function.Consumer<String> noop) {
        // placeholder for structured mapping — actual logic in parseStructured
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
