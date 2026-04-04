package com.ownpic.detection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ownpic.detection.domain.DetectionScan;
import com.ownpic.detection.domain.DetectionScanRepository;
import com.ownpic.detection.domain.InternetDetectionResult;
import com.ownpic.detection.domain.InternetDetectionResultRepository;
import com.ownpic.detection.dto.DetectionScanResponse;
import com.ownpic.detection.port.DinoEmbeddingPort;
import com.ownpic.detection.port.ExternalImageDownloadPort;
import com.ownpic.detection.port.InternetImageSearchPort;
import com.ownpic.detection.port.InternetImageSearchPort.SearchResult;
import com.ownpic.detection.port.SscdEmbeddingPort;
import com.ownpic.image.domain.Image;
import com.ownpic.image.domain.ImageRepository;
import com.ownpic.image.domain.ImageStatus;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class InternetDetectionService {

    private static final Logger log = LoggerFactory.getLogger(InternetDetectionService.class);

    private static final double SSCD_THRESHOLD = 0.30;
    private static final double DINO_THRESHOLD = 0.70;
    private static final double SSCD_MIN_FOR_DINO = 0.15;
    private static final int DOWNLOAD_TIMEOUT_MS = 10_000;
    private static final int MAX_SEARCH_RESULTS = 50;
    private static final int MAX_MATCHES_PER_IMAGE = 20;
    private static final Pattern PRELOADED_STATE_PATTERN = Pattern.compile(
            "window\\.__PRELOADED_STATE__\\s*=\\s*(\\{.+?\\})\\s*;?\\s*</script>",
            Pattern.DOTALL);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final DetectionScanRepository scanRepository;
    private final InternetDetectionResultRepository resultRepository;
    private final ImageRepository imageRepository;
    private final InternetImageSearchPort searchPort;
    private final ExternalImageDownloadPort downloadPort;
    private final SscdEmbeddingPort sscdPort;
    private final DinoEmbeddingPort dinoPort;

    @Value("${ownpic.google.selenium-enabled:true}")
    private boolean seleniumEnabled;

    private WebDriver driver;
    private boolean loggedIn = false;

    public InternetDetectionService(DetectionScanRepository scanRepository,
                                    InternetDetectionResultRepository resultRepository,
                                    ImageRepository imageRepository,
                                    InternetImageSearchPort searchPort,
                                    ExternalImageDownloadPort downloadPort,
                                    SscdEmbeddingPort sscdPort,
                                    DinoEmbeddingPort dinoPort) {
        this.scanRepository = scanRepository;
        this.resultRepository = resultRepository;
        this.imageRepository = imageRepository;
        this.searchPort = searchPort;
        this.downloadPort = downloadPort;
        this.sscdPort = sscdPort;
        this.dinoPort = dinoPort;
    }

    @PostConstruct
    public void initSelenium() {
        if (!seleniumEnabled) {
            log.info("[Selenium] 비활성 — ownpic.google.selenium-enabled=false");
            return;
        }
        try {
            String profileDir = System.getProperty("user.home")
                    + java.io.File.separator + ".ownpic-chrome-profile";

            ChromeOptions options = new ChromeOptions();
            options.addArguments(
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--disable-gpu",
                    "--disable-blink-features=AutomationControlled",
                    "--lang=ko-KR",
                    "--window-size=1920,1080",
                    "--user-data-dir=" + profileDir,
                    "--remote-debugging-port=0"
            );
            options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
            options.setExperimentalOption("useAutomationExtension", false);

            driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));

            // navigator.webdriver 감지 방지
            ((JavascriptExecutor) driver).executeScript(
                    "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");

            // 네이버 로그인 상태 확인
            driver.get("https://nid.naver.com/nidlogin.login");
            Thread.sleep(2000);
            String currentUrl = driver.getCurrentUrl();
            if (currentUrl.contains("nidlogin")) {
                log.warn("[Selenium] 네이버 미로그인 상태 — chrome-profile에서 수동 로그인 필요");
                log.warn("[Selenium] headless 제거 후 수동 로그인하면 세션이 chrome-profile에 저장됩니다");
            } else {
                loggedIn = true;
                log.info("[Selenium] 네이버 로그인 세션 확인 — 로그인 상태 유지중");
            }

            log.info("[Selenium] Chrome 브라우저 초기화 완료 (user-data-dir={})", profileDir);
        } catch (Exception e) {
            log.warn("[Selenium] 초기화 실패: {}", e.getMessage());
            seleniumEnabled = false;
        }
    }

    @PreDestroy
    public void cleanupSelenium() {
        if (driver != null) {
            try { driver.quit(); } catch (Exception ignored) {}
        }
    }

    @Transactional
    public DetectionScanResponse startInternetScan(UUID userId) {
        List<Image> indexed = imageRepository.findByUserIdAndStatus(userId, ImageStatus.INDEXED);
        if (indexed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "인덱싱된 이미지가 없습니다.");
        }

        var scan = new DetectionScan(userId, indexed.size());
        scan.setScanType("INTERNET");
        scan = scanRepository.save(scan);

        executeInternetScanAsync(scan.getId(), indexed);

        return DetectionScanResponse.from(scan);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeInternetScanAsync(Long scanId, List<Image> images) {
        try {
            List<InternetDetectionResult> allResults = new ArrayList<>();
            int scannedCount = 0;

            for (Image image : images) {
                log.info("[Scan:{}] 이미지 {} 처리 시작 — name={}, gcsPath={}",
                        scanId, image.getId(), image.getName(), image.getGcsPath());

                float[] sourceSSCD = bytesToFloats(image.getEmbedding());
                float[] sourceDINO = bytesToFloats(image.getEmbeddingDino());
                log.info("[Scan:{}] 이미지 {} 임베딩 — SSCD={}, DINO={}",
                        scanId, image.getId(),
                        sourceSSCD != null ? sourceSSCD.length + "dim" : "null",
                        sourceDINO != null ? sourceDINO.length + "dim" : "null");

                List<InternetDetectionResult> imageResults = new ArrayList<>();
                String keyword = image.getKeywords() == null ? image.getName() : image.getKeywords();

                // 1단계: 네이버 키워드 검색 (키워드가 있을 때만)
                log.info("[Scan:{}] 이미지 {} 키워드 — '{}'", scanId, image.getId(), keyword);

                if (keyword != null && !keyword.isBlank()) {
                    List<SearchResult> keywordResults = searchPort.searchByKeyword(keyword, MAX_SEARCH_RESULTS);
                    log.info("[Scan:{}] 이미지 {} 네이버 검색 — {}건 발견 (keyword='{}')", scanId, image.getId(), keywordResults.size(), keyword);
                    for (int i = 0; i < Math.min(5, keywordResults.size()); i++) {
                        var sr = keywordResults.get(i);
                        log.info("[Scan:{}] 이미지 {} 네이버 결과[{}] — img={} page={} title={}",
                                scanId, image.getId(), i, sr.imageUrl(),
                                sr.sourcePageUrl() != null ? sr.sourcePageUrl() : "(없음)",
                                sr.title() != null ? sr.title() : "(없음)");
                    }

                    int naverMatches = 0;
                    for (SearchResult sr : keywordResults) {
                        InternetDetectionResult result = processSearchResult(
                                scanId, image.getId(), sr, sourceSSCD, sourceDINO);
                        if (result != null) {
                            imageResults.add(result);
                            naverMatches++;
                        }
                    }
                    log.info("[Scan:{}] 이미지 {} 네이버 매칭 — {}건 (임계값 통과)", scanId, image.getId(), naverMatches);
                } else {
                    log.info("[Scan:{}] 이미지 {} 키워드 없음 — 네이버 검색 스킵", scanId, image.getId());
                }

                // SSCD 스코어 높은 순 정렬 → 상위 20개만 유지
                imageResults.sort((a, b) -> {
                    double scoreA = a.getSscdSimilarity() != null ? a.getSscdSimilarity() : 0;
                    double scoreB = b.getSscdSimilarity() != null ? b.getSscdSimilarity() : 0;
                    return Double.compare(scoreB, scoreA);
                });
                if (imageResults.size() > MAX_MATCHES_PER_IMAGE) {
                    imageResults = imageResults.subList(0, MAX_MATCHES_PER_IMAGE);
                }
                log.info("[Scan:{}] 이미지 {} 최종 — {}건 (상위 {}개 제한)",
                        scanId, image.getId(), imageResults.size(), MAX_MATCHES_PER_IMAGE);
                allResults.addAll(imageResults);

                scannedCount++;
                updateProgress(scanId, scannedCount);
            }

            resultRepository.saveAll(allResults);

            completeScan(scanId, allResults.size());

            log.info("[Scan:{}] ===== 스캔 완료 ===== {}개 이미지 검사, {}건 도용 의심",
                    scanId, images.size(), allResults.size());
        } catch (Exception e) {
            log.error("Internet scan {} failed", scanId, e);
            failScan(scanId);
        }
    }

    private InternetDetectionResult processSearchResult(
            Long scanId, Long sourceImageId, SearchResult sr,
            float[] sourceSSCD, float[] sourceDINO) {

        // 1. 외부 이미지 다운로드
        log.info("[Scan:{}][{}] 다운로드 시도: {}", scanId, "NAVER", sr.imageUrl());
        byte[] foundBytes = downloadPort.download(sr.imageUrl(), DOWNLOAD_TIMEOUT_MS);
        if (foundBytes == null) {
            log.info("[Scan:{}][{}] 다운로드 실패: {}", scanId, "NAVER", sr.imageUrl());
            return null;
        }
        log.info("[Scan:{}][{}] 다운로드 완료: {}KB — pageUrl={}, title={}",
                scanId, "NAVER", foundBytes.length / 1024, sr.sourcePageUrl(), sr.title());

        // 2. 임베딩 생성 + 코사인 유사도 비교
        Double sscdSim = null;
        Double dinoSim = null;

        try {
            if (sourceSSCD != null) {
                float[] foundSSCD = sscdPort.generateEmbedding(foundBytes);
                if (foundSSCD != null) {
                    sscdSim = cosineSimilarity(sourceSSCD, foundSSCD);
                }
            }

            if (sourceDINO != null) {
                float[] foundDINO = dinoPort.generateEmbedding(foundBytes);
                if (foundDINO != null) {
                    dinoSim = cosineSimilarity(sourceDINO, foundDINO);
                }
            }
        } catch (Exception e) {
            log.info("[Scan:{}][{}] 임베딩 실패: {} — {}", scanId, "NAVER", sr.imageUrl(), e.getMessage());
            return null;
        }

        // 3. 듀얼 판정 — SSCD 메인, DINO 보조 (bg_swap 등 변형 탐지)
        boolean sscdMatch = sscdSim != null && sscdSim >= SSCD_THRESHOLD;
        boolean dinoAssist = sscdSim != null && sscdSim >= SSCD_MIN_FOR_DINO
                && dinoSim != null && dinoSim >= DINO_THRESHOLD;

        String sscdStr = sscdSim != null ? String.format("%.1f%%", sscdSim * 100) : "null";
        String dinoStr = dinoSim != null ? String.format("%.1f%%", dinoSim * 100) : "null";

        if (!sscdMatch && !dinoAssist) {
            log.info("[Scan:{}][{}] 임계값 미달 — SSCD={} DINO={} — {}",
                    scanId, "NAVER", sscdStr, dinoStr, sr.imageUrl());
            return null; // 임계값 미달 → 스킵
        }

        log.info("[Scan:{}][{}] ★ 도용 의심 — SSCD={} DINO={} — imgUrl={} pageUrl={} title={}",
                scanId, "NAVER", sscdStr, dinoStr,
                sr.imageUrl(),
                sr.sourcePageUrl() != null ? sr.sourcePageUrl() : "(없음)",
                sr.title() != null ? sr.title() : "(없음)");

        // 4. 리다이렉트 추적 + 판매자 정보 추출
        String resolvedUrl = resolveRedirectUrl(sr.sourcePageUrl());
        if (resolvedUrl != null && !resolvedUrl.equals(sr.sourcePageUrl())) {
            log.info("[Scan:{}] 리다이렉트: {} → {}", scanId, sr.sourcePageUrl(), resolvedUrl);
        }
        String finalPageUrl = resolvedUrl != null ? resolvedUrl : sr.sourcePageUrl();

        var result = new InternetDetectionResult(
                scanId, sourceImageId,
                sr.imageUrl(), finalPageUrl, sr.title(),
                sscdSim, dinoSim, "INFRINGEMENT", "NAVER");

        extractSellerInfo(finalPageUrl, result);

        return result;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(Long scanId, int scannedImages) {
        scanRepository.findById(scanId).ifPresent(scan -> {
            scan.setScannedImages(scannedImages);
            scanRepository.save(scan);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeScan(Long scanId, int matchesFound) {
        scanRepository.findById(scanId).ifPresent(scan -> {
            scan.setStatus("COMPLETED");
            scan.setMatchesFound(matchesFound);
            scan.setScannedImages(scan.getTotalImages());
            scan.setCompletedAt(Instant.now());
            scanRepository.save(scan);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failScan(Long scanId) {
        scanRepository.findById(scanId).ifPresent(scan -> {
            scan.setStatus("FAILED");
            scan.setCompletedAt(Instant.now());
            scanRepository.save(scan);
        });
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double deno = Math.sqrt(normA) * Math.sqrt(normB);
        return deno == 0 ? 0 : dot / deno;
    }

    private static float[] bytesToFloats(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        float[] result = new float[bytes.length / 4];
        for (int i = 0; i < result.length; i++) {
            result[i] = buf.getFloat();
        }
        return result;
    }

    /**
     * HTTP 리다이렉트를 수동으로 따라가서 최종 URL 반환.
     */
    private String resolveRedirectUrl(String originalUrl) {
        if (originalUrl == null || originalUrl.isBlank()) return originalUrl;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            String currentUrl = originalUrl;
            for (int i = 0; i < 10; i++) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(currentUrl))
                        .timeout(Duration.ofSeconds(5))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .build();

                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                int status = response.statusCode();

                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = response.headers().firstValue("Location").orElse(null);
                    if (location == null) break;
                    // 상대 경로 처리
                    if (location.startsWith("/")) {
                        URI base = URI.create(currentUrl);
                        location = base.getScheme() + "://" + base.getHost() + location;
                    }
                    log.info("[Redirect] {} → {} ({})", currentUrl, location, status);
                    currentUrl = location;
                } else {
                    break;
                }
            }
            return currentUrl;
        } catch (Exception e) {
            log.warn("[Redirect] 추적 실패: {} — {}", originalUrl, e.getMessage());
            return originalUrl;
        }
    }

    /**
     * Selenium(네이버 로그인 상태)으로 페이지 접속 후
     * window.__PRELOADED_STATE__ JSON에서 판매자 정보 추출.
     */
    private void extractSellerInfo(String pageUrl, InternetDetectionResult result) {
        if (pageUrl == null || pageUrl.isBlank()) return;
        if (driver == null || !seleniumEnabled) {
            log.info("[SellerInfo] Selenium 비활성 — 스킵: {}", pageUrl);
            return;
        }
        try {
            driver.get(pageUrl);
            Thread.sleep(2000);

            String pageSource = driver.getPageSource();

            // window.__PRELOADED_STATE__ = { ... }; 찾기
            Matcher m = PRELOADED_STATE_PATTERN.matcher(pageSource);
            if (!m.find()) {
                log.info("[SellerInfo] __PRELOADED_STATE__ 없음: {}", pageUrl);
                return;
            }

            String jsonStr = m.group(1);
            // JavaScript undefined → JSON null 변환
            jsonStr = jsonStr.replaceAll(":\\s*undefined", ": null")
                             .replaceAll(",\\s*undefined", ", null");
            JsonNode root = objectMapper.readTree(jsonStr);

            // channel 객체 내 사업자 정보 추출
            String representName = findJsonValue(root, "representName");
            String identity = findJsonValue(root, "identity");
            String representativeName = findJsonValue(root, "representativeName");
            String mailOrderNumber = findJsonValue(root, "declaredToOnlineMarkettingNumber");
            String fullAddress = findNestedJsonValue(root, "businessAddressInfo", "fullAddressInfo");

            if (representName != null || identity != null) {
                result.setSellerName(representName);
                result.setBusinessRegNumber(formatBizNumber(identity));
                result.setRepresentativeName(representativeName);
                result.setMailOrderNumber(mailOrderNumber);
                result.setBusinessAddress(fullAddress);
                result.setStoreUrl(extractStoreUrl(pageUrl));
                result.setPlatformType("NAVER_SMARTSTORE");

                log.info("[SellerInfo] 추출 성공 — 상호:{} / 사업자:{} / 대표:{} / 통신판매:{} / 주소:{}",
                        representName, formatBizNumber(identity), representativeName,
                        mailOrderNumber, fullAddress);
            }
        } catch (Exception e) {
            log.warn("[SellerInfo] 파싱 실패: {} — {}", pageUrl, e.getMessage());
        }
    }

    /**
     * JSON 트리에서 특정 필드명을 재귀 탐색.
     */
    private static String findJsonValue(JsonNode node, String fieldName) {
        if (node == null) return null;
        if (node.has(fieldName) && node.get(fieldName).isTextual()) {
            return node.get(fieldName).asText();
        }
        for (JsonNode child : node) {
            String found = findJsonValue(child, fieldName);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * JSON 트리에서 parentField 아래의 childField 값을 재귀 탐색.
     */
    private static String findNestedJsonValue(JsonNode node, String parentField, String childField) {
        if (node == null) return null;
        if (node.has(parentField) && node.get(parentField).isObject()) {
            JsonNode parent = node.get(parentField);
            if (parent.has(childField) && parent.get(childField).isTextual()) {
                return parent.get(childField).asText();
            }
        }
        for (JsonNode child : node) {
            String found = findNestedJsonValue(child, parentField, childField);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * 사업자번호 포맷: "1858100504" → "185-81-00504"
     */
    private static String formatBizNumber(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() == 10) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 5) + "-" + digits.substring(5);
        }
        return raw;
    }

    /**
     * 스마트스토어 URL에서 스토어 메인 URL 추출.
     * https://smartstore.naver.com/lovbong/products/123 → https://smartstore.naver.com/lovbong
     */
    private static String extractStoreUrl(String pageUrl) {
        if (pageUrl == null) return null;
        try {
            URI uri = URI.create(pageUrl);
            String host = uri.getHost();
            if (host == null) return pageUrl;
            String path = uri.getPath();
            if (path == null || path.equals("/")) return pageUrl;
            String[] segments = path.split("/");
            if (segments.length >= 2 && !segments[1].isBlank()) {
                return "https://" + host + "/" + segments[1];
            }
        } catch (Exception ignored) {}
        return pageUrl;
    }
}
