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
    private final NaverSmartStoreExtractor smartStoreExtractor;
    private final CaptchaSolver captchaSolver;

    // 통신판매중개자 본사 사업자번호 — 이 번호가 추출되면 중개자 정보이므로 무시
    private static final Set<String> INTERMEDIARY_BIZ_NUMBERS = Set.of(
            "220-81-62517",   // 네이버(주)
            "120-88-00767",   // 쿠팡(주)
            "107-86-01737",   // (주)이베이코리아 (G마켓/옥션)
            "107-87-83297",   // (주)십일번가
            "104-81-53914"    // (주)에쓰에스지닷컴 (SSG)
    );

    // 정규식 패턴 — 한국 사업자 정보 공통
    private static final Pattern BIZ_NUMBER = Pattern.compile("(\\d{3}-\\d{2}-\\d{5})");
    // 대표자: "대표자 : 홍길동" — "대표이사" 같은 직함은 제외
    private static final Pattern REPRESENTATIVE = Pattern.compile(
            "대표[자]?\\s*[：:.)]?\\s*(?!이사|이사\\s)([가-힣]{2,4})(?=\\s|\\)|,|$|\\d|사업|전화|주소|소재|통신)");
    private static final Pattern PHONE = Pattern.compile("(0\\d{1,2}[-.)\\s]\\d{3,4}[-.)\\s]\\d{4})");
    private static final Pattern EMAIL = Pattern.compile("([\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,})");
    // 상호명 추출 패턴 (우선순위 순)
    // 1. "(주)OO" 또는 "주식회사 OO" 패턴 — "대표이사" 등 직함 제외
    private static final Pattern COMPANY_CORP = Pattern.compile(
            "(?:\\(주\\)|주식회사|\\(유\\))\\s*([가-힣a-zA-Z0-9\\s]{2,20}?)(?=\\s*\\(|\\s*대표|$)");
    // 2. "상호 : OO패션" 라벨 패턴
    private static final Pattern STORE_NAME = Pattern.compile(
            "(?:상호[명]?|회사[명]?)\\s*[：:.)\\s]\\s*([가-힣a-zA-Z0-9\\s]{2,20})");
    private static final Pattern ADDRESS = Pattern.compile(
            "(?:소재지|사업장[\\s]*주소|주소)\\s*[：:.]?\\s*([^\\n<]{5,100})");

    // 에러 페이지 감지 키워드
    private static final Set<String> ERROR_PAGE_KEYWORDS = Set.of(
            "access denied", "403 forbidden", "404 not found", "page not found",
            "서비스 점검", "잠시만 기다려", "접근이 제한", "페이지를 찾을 수 없");

    // 크롤링 스킵 도메인 (CDN + 플랫폼 본사 페이지)
    private static final Set<String> SKIP_DOMAINS = Set.of(
            // CDN
            "ssgcdn.com", "pstatic.net", "naver.net", "ohousecdn.com",
            "coupangcdn.com", "cloudfront.net", "akamaized.net",
            "cloudinary.com", "imgix.net", "fastly.net",
            "digitalcontent.marksandspencer.app", "susercontent.com",
            // 플랫폼 본사/프로모션 페이지 (통신판매중개자 본사 정보만 있음)
            "pages.coupang.com", "display.coupang.com",
            "campaign.naver.com", "adcr.naver.com",
            "ads.google.com", "doubleclick.net");
    // 통신판매번호: 제YYYY-지역-NNNN호
    private static final Pattern MAIL_ORDER_NUMBER = Pattern.compile("제?\\d{4}-[가-힣]{2,5}-\\d{4}호?");

    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    );

    public SellerInfoExtractor(InternetDetectionResultRepository resultRepository,
                               SeleniumLensAdapter seleniumAdapter,
                               NaverSmartStoreExtractor smartStoreExtractor,
                               CaptchaSolver captchaSolver) {
        this.resultRepository = resultRepository;
        this.seleniumAdapter = seleniumAdapter;
        this.smartStoreExtractor = smartStoreExtractor;
        this.captchaSolver = captchaSolver;
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

                // 중개자 본사 정보 필터
                if (info.businessRegNumber() != null && INTERMEDIARY_BIZ_NUMBERS.contains(info.businessRegNumber())) {
                    log.info("[SellerExtractor] 중개자 본사 정보 감지 — 무시: {} ({})",
                            info.businessRegNumber(), info.sellerName());
                    info = new SellerInfo(info.platformType(), info.platformCategory(),
                            null, null, null, null, null, null, info.storeUrl());
                }

                applyToResult(result, info);
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

        // ★ 스마트스토어 전용 추출기 우선 사용
        if ("NAVER_SMARTSTORE".equals(platform.type()) || "NAVER_BRAND".equals(platform.type())) {
            SellerInfo smartStoreInfo = smartStoreExtractor.extract(url);
            if (smartStoreInfo != null && smartStoreInfo.sellerName() != null) {
                log.info("[SellerExtractor] 스마트스토어 전용 추출 성공: {} / {}",
                        smartStoreInfo.sellerName(), smartStoreInfo.representativeName());

                // CAPTCHA 풀기로 사업자번호 추가 확보 시도
                if (smartStoreInfo.businessRegNumber() == null && seleniumAdapter.isEnabled()) {
                    log.info("[SellerExtractor] CAPTCHA 풀기 시도: {}", url);
                    String captchaPage = seleniumAdapter.solveCaptchaAndGetSellerInfo(url, captchaSolver);
                    if (captchaPage != null) {
                        Document captchaDoc = Jsoup.parse(captchaPage);
                        SellerInfo captchaInfo = parseBusinessInfo(captchaDoc, url, platform, hint);
                        if (captchaInfo.businessRegNumber() != null
                                && !INTERMEDIARY_BIZ_NUMBERS.contains(captchaInfo.businessRegNumber())) {
                            // CAPTCHA 풀어서 얻은 사업자번호를 합침
                            return new SellerInfo(smartStoreInfo.platformType(), smartStoreInfo.platformCategory(),
                                    smartStoreInfo.sellerName(), captchaInfo.businessRegNumber(),
                                    smartStoreInfo.representativeName(),
                                    captchaInfo.businessAddress(), captchaInfo.contactPhone(),
                                    captchaInfo.contactEmail(), smartStoreInfo.storeUrl());
                        }
                    }
                }
                return smartStoreInfo;
            }
        }

        // 1차: Selenium으로 접속 (JS 동적 로딩 대응 — 쿠팡 등)
        String pageSource = null;
        if (seleniumAdapter.isEnabled()) {
            log.info("[SellerExtractor] Selenium으로 접속: {} ({})", url, platform.type());
            pageSource = seleniumAdapter.fetchPageWithScroll(url);
            if (pageSource != null) {
                log.info("[SellerExtractor] Selenium 페이지 로드 완료: {}자", pageSource.length());
            } else {
                log.warn("[SellerExtractor] Selenium 페이지 로드 실패: {}", url);
            }
        } else {
            log.info("[SellerExtractor] Selenium 비활성 — JSoup fallback: {}", url);
        }

        // 2차: Selenium 실패 시 JSoup fallback
        Document doc;
        if (pageSource != null) {
            doc = Jsoup.parse(pageSource);
        } else {
            try {
                doc = fetchPageJsoup(url);
            } catch (Exception e) {
                log.info("[SellerExtractor] 페이지 접속 실패: {}", e.getMessage());
                return SellerInfo.empty(platform.type(), platform.category());
            }
        }

        // 에러 페이지 감지
        if (isErrorPage(doc)) {
            log.info("[SellerExtractor] 에러 페이지 감지 — 스킵: {}", url);
            return SellerInfo.empty(platform.type(), platform.category());
        }

        return parseBusinessInfo(doc, url, platform, hint);
    }

    /** 에러 페이지 감지 (Access Denied, 404 등) */
    private boolean isErrorPage(Document doc) {
        String title = doc.title() != null ? doc.title().toLowerCase() : "";
        String bodyText = doc.body() != null ? doc.body().text().toLowerCase() : "";
        // 본문이 너무 짧으면 에러 페이지
        if (bodyText.length() < 100) return true;
        for (String keyword : ERROR_PAGE_KEYWORDS) {
            if (title.contains(keyword) || bodyText.substring(0, Math.min(500, bodyText.length())).contains(keyword)) {
                return true;
            }
        }
        return false;
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
            log.info("[SellerExtractor] 통신판매중개자 — 상품 페이지 내 판매자 정보 섹션 탐색: {}", platform.type());

            // 1차: 플랫폼별 CSS 셀렉터로 판매자 섹션 특정
            if (hint.sellerSectionSelector() != null) {
                Elements sellerSection = doc.select(hint.sellerSectionSelector());
                if (!sellerSection.isEmpty()) {
                    targetText = sellerSection.text();
                    log.info("[SellerExtractor] 판매자 섹션 발견: {}자", targetText.length());
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

        // 상호명 추출: (주)OO / 주식회사 OO 우선, 없으면 "상호:" 라벨 패턴
        String sellerName = extractFirst(COMPANY_CORP, targetText);
        if (sellerName == null) {
            sellerName = extractFirst(STORE_NAME, targetText);
        }
        // title은 상호명으로 사용하지 않음 (상품명+플랫폼명이라 부적절)

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

        // dt/dd + th/td 공통 파싱 (라벨-값 쌍)
        List<Element> labelElements = new ArrayList<>();
        labelElements.addAll(searchScope.select("dt"));
        labelElements.addAll(searchScope.select("th"));

        for (Element labelEl : labelElements) {
            String label = labelEl.text().trim().toLowerCase();
            Element valueEl = labelEl.nextElementSibling();
            if (valueEl == null) continue;
            String sibTag = valueEl.tagName();
            if (!"dd".equals(sibTag) && !"td".equals(sibTag)) continue;
            String value = valueEl.text().trim();
            if (value.isEmpty()) continue;

            // "상호/대표자: OO / 홍길동" 복합 라벨 처리 (쿠팡 등)
            if (label.contains("상호") && label.contains("대표")) {
                String[] parts = value.split("[/,]", 2);
                if (sellerName == null) sellerName = parts[0].trim();
                if (representative == null && parts.length > 1) representative = parts[1].trim();
            }
            // 상호명
            else if (sellerName == null && (label.contains("상호") || label.contains("회사")
                    || label.contains("업체") || label.contains("판매자") || label.contains("seller"))) {
                sellerName = value;
            }
            // 사업자번호
            else if (bizNumber == null && (label.contains("사업자") || label.contains("business"))) {
                bizNumber = value;
            }
            // 대표자
            else if (representative == null && label.contains("대표")) {
                representative = value;
            }
            // 주소
            else if (address == null && (label.contains("소재지") || label.contains("주소") || label.contains("address"))) {
                address = value;
            }
            // 연락처
            else if (phone == null && (label.contains("전화") || label.contains("연락") || label.contains("고객센터")
                    || label.contains("연락처") || label.equals("tel"))) {
                phone = value;
            }
            // 이메일
            else if (email == null && (label.contains("이메일") || label.contains("mail") || label.contains("e-mail"))) {
                email = value;
            }
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

            // CDN 도메인이면 크롤링 스킵
            if (isSkipDomain(host)) {
                log.info("[SellerExtractor] CDN 도메인 스킵: {}", host);
                return null;
            }

            return "https://" + host + "/";
        } catch (Exception e) {
            return null;
        }
    }

    /** 크롤링 스킵 도메인 판별 (CDN + 플랫폼 본사 프로모션) */
    private boolean isSkipDomain(String host) {
        for (String domain : SKIP_DOMAINS) {
            if (host.contains(domain)) return true;
        }
        return host.startsWith("cdn.") || host.startsWith("img.") || host.startsWith("image.")
                || host.startsWith("images.") || host.startsWith("static.")
                || host.startsWith("media.") || host.startsWith("thumbnail")
                || host.startsWith("assets.") || host.startsWith("res.")
                || host.startsWith("down-") || host.startsWith("pages.");
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
                log.info("[SellerExtractor] JSON 데이터에서 사업자 정보 발견: {}", extracted);
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
