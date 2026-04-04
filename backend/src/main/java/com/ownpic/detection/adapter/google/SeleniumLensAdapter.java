package com.ownpic.detection.adapter.google;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.openqa.selenium.*;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Selenium으로 Google Lens 이미지 검색을 수행하여
 * sourcePageUrl이 없는 결과에 대해 판매 페이지 URL을 보완.
 *
 * Vision API의 pagesWithMatchingImages로 못 잡은 결과를 커버.
 */
@Component
@Profile("google")
public class SeleniumLensAdapter {

    private static final Logger log = LoggerFactory.getLogger(SeleniumLensAdapter.class);

    @Value("${ownpic.google.selenium-enabled:false}")
    private boolean enabled;

    private WebDriver driver;

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("[SeleniumLens] DISABLED (ownpic.google.selenium-enabled=false)");
            return;
        }
        try {
            ChromeOptions options = new ChromeOptions();
            options.addArguments(
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--disable-gpu",
                    "--disable-blink-features=AutomationControlled",
                    "--lang=ko-KR",
                    "--window-size=1920,1080"
            );
            // navigator.webdriver 감지 방지
            options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
            options.setExperimentalOption("useAutomationExtension", false);
            // headless 모드 (창 안 뜸)
            options.addArguments("--headless=new");

            driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            log.info("[SeleniumLens] Chrome 브라우저 초기화 완료");
        } catch (Exception e) {
            log.warn("[SeleniumLens] Chrome 초기화 실패 — Selenium 기능 비활성: {}", e.getMessage());
            enabled = false;
        }
    }

    @PreDestroy
    public void cleanup() {
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Google Lens로 이미지 검색하여 판매 페이지 URL 목록 반환.
     *
     * @param imageBytes 검색할 이미지 바이트
     * @return 발견된 판매 페이지 URL 목록 (도메인, 페이지 URL, 제목)
     */
    public List<LensResult> searchByImage(byte[] imageBytes) {
        if (!enabled || driver == null) {
            return List.of();
        }

        Path tempFile = null;
        try {
            // 이미지 임시 파일 저장
            tempFile = Files.createTempFile("lens_", ".jpg");
            Files.write(tempFile, imageBytes);

            // 랜덤 지연 (3~7초)
            randomDelay();

            // Google 이미지 검색 접속
            driver.get("https://images.google.com");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

            // '이미지로 검색' 버튼 클릭
            WebElement lensButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("div[aria-label='이미지로 검색'], div[aria-label='Search by image']")));
            lensButton.click();

            randomDelay(1000, 3000);

            // 파일 업로드
            WebElement fileInput = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//input[@type='file']")));
            fileInput.sendKeys(tempFile.toAbsolutePath().toString());

            // 결과 페이지 대기
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.presenceOfElementLocated(By.id("search")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("[data-ri]")),
                    ExpectedConditions.urlContains("lens")
            ));

            randomDelay(2000, 5000);

            // 결과에서 링크 수집
            List<LensResult> results = new ArrayList<>();
            List<WebElement> links = driver.findElements(By.cssSelector("a[href]"));

            Set<String> seen = new HashSet<>();
            for (WebElement link : links) {
                try {
                    String href = link.getAttribute("href");
                    if (href == null || href.isBlank()) continue;
                    if (!href.startsWith("http")) continue;
                    if (href.contains("google.com")) continue; // 구글 자체 링크 제외

                    String domain = new java.net.URI(href).getHost();
                    if (domain == null) continue;

                    if (seen.add(href)) {
                        String text = link.getText();
                        results.add(new LensResult(href, domain, text));
                    }
                } catch (Exception ignored) {}
            }

            log.info("[SeleniumLens] 검색 완료 — {}개 외부 링크 발견", results.size());
            return results;

        } catch (Exception e) {
            log.warn("[SeleniumLens] Google Lens 검색 실패: {}", e.getMessage());
            return List.of();
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
            }
        }
    }

    /**
     * Selenium으로 페이지에 접속하여 끝까지 스크롤 후 페이지 소스 반환.
     * 네이버 스마트스토어/쿠팡 등 JS 동적 로딩 대응.
     */
    public String fetchPageWithScroll(String url) {
        if (!enabled || driver == null) return null;

        try {
            randomDelay(1000, 3000);
            driver.get(url);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

            // 페이지 끝까지 스크롤 (동적 로딩 대응)
            JavascriptExecutor js = (JavascriptExecutor) driver;
            long lastHeight = (long) js.executeScript("return document.body.scrollHeight");
            for (int i = 0; i < 5; i++) {
                js.executeScript("window.scrollTo(0, document.body.scrollHeight)");
                randomDelay(1000, 2000);
                long newHeight = (long) js.executeScript("return document.body.scrollHeight");
                if (newHeight == lastHeight) break;
                lastHeight = newHeight;
            }

            // navigator.webdriver 제거 (쿠팡 등 감지 방지)
            js.executeScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");

            String mainPageSource = driver.getPageSource();

            // iframe 내 판매자 정보 탐색 (11번가 등)
            List<WebElement> iframes = driver.findElements(By.tagName("iframe"));
            StringBuilder iframeContent = new StringBuilder();
            for (WebElement iframe : iframes) {
                try {
                    String iframeId = iframe.getAttribute("id");
                    String iframeSrc = iframe.getAttribute("src");
                    // 판매자 정보 관련 iframe만 탐색
                    if ((iframeId != null && iframeId.toLowerCase().contains("seller"))
                            || (iframeSrc != null && iframeSrc.toLowerCase().contains("seller"))) {
                        driver.switchTo().frame(iframe);
                        iframeContent.append(driver.getPageSource());
                        driver.switchTo().defaultContent();
                    }
                } catch (Exception ignored) {
                    try { driver.switchTo().defaultContent(); } catch (Exception e2) { /* ignore */ }
                }
            }

            // iframe 내용이 있으면 메인 페이지 소스에 합침
            if (!iframeContent.isEmpty()) {
                mainPageSource += "\n<!-- IFRAME_SELLER_INFO -->\n" + iframeContent;
            }

            return mainPageSource;
        } catch (Exception e) {
            log.debug("[SeleniumLens] 페이지 접속 실패: {} — {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * CAPTCHA 팝업을 풀고 판매자 상세정보 페이지 소스를 반환.
     *
     * 1. 해당 페이지 접속 → 스크롤
     * 2. "판매자 상세정보 확인" 버튼 클릭
     * 3. CAPTCHA 팝업 감지 → 이미지 캡처
     * 4. CaptchaSolver로 답 계산
     * 5. 답 입력 → 확인 → 판매자 정보 페이지 소스 반환
     *
     * @param pageUrl 판매자 정보가 있는 페이지 URL
     * @param captchaSolver CAPTCHA 풀기 컴포넌트
     * @return 판매자 상세정보가 포함된 HTML (실패 시 null)
     */
    public String solveCaptchaAndGetSellerInfo(String pageUrl,
                                                com.ownpic.detection.adapter.seller.CaptchaSolver captchaSolver) {
        if (!enabled || driver == null) return null;

        try {
            randomDelay(1000, 3000);
            driver.get(pageUrl);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

            // 페이지 끝까지 스크롤
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("window.scrollTo(0, document.body.scrollHeight)");
            randomDelay(2000, 3000);

            // "판매자 상세정보 확인" 버튼 찾기
            WebElement detailButton = null;
            for (String selector : List.of(
                    "a[contains(text(),'상세정보')]", // XPath
                    "button[contains(text(),'상세정보')]")) {
                try {
                    detailButton = driver.findElement(By.xpath("//*[contains(text(),'상세정보 확인')]"));
                    break;
                } catch (Exception ignored) {}
            }

            if (detailButton == null) {
                // CSS 셀렉터로 재시도
                try {
                    detailButton = driver.findElement(By.cssSelector(
                            "[class*=detail], [class*=seller_detail], a[href*=seller]"));
                } catch (Exception ignored) {}
            }

            if (detailButton == null) {
                log.info("[SeleniumLens] '판매자 상세정보 확인' 버튼을 찾을 수 없음: {}", pageUrl);
                return driver.getPageSource();
            }

            detailButton.click();
            randomDelay(1500, 3000);

            // CAPTCHA 팝업 감지
            WebElement captchaImage = null;
            try {
                // CAPTCHA 이미지 찾기 (팝업 내 img 태그)
                captchaImage = wait.until(ExpectedConditions.presenceOfElementLocated(
                        By.cssSelector("[class*=captcha] img, [class*=modal] img, [class*=popup] img, [class*=dialog] img")));
            } catch (Exception e) {
                log.info("[SeleniumLens] CAPTCHA 팝업 없음 — 이미 판매자 정보가 표시됨");
                return driver.getPageSource();
            }

            // CAPTCHA 이미지 스크린샷
            byte[] captchaBytes = captchaImage.getScreenshotAs(OutputType.BYTES);
            log.info("[SeleniumLens] CAPTCHA 이미지 캡처 완료: {}KB", captchaBytes.length / 1024);

            // Vision API OCR로 답 계산
            int answer = captchaSolver.solveReceiptCaptcha(captchaBytes);
            if (answer <= 0) {
                log.warn("[SeleniumLens] CAPTCHA 풀기 실패 — 답: {}", answer);
                return null;
            }

            log.info("[SeleniumLens] CAPTCHA 답: {}", answer);

            // 답 입력
            WebElement inputField = driver.findElement(By.cssSelector(
                    "input[type=text], input[type=number], input[placeholder*='정답'], input[placeholder*='입력']"));
            inputField.clear();
            inputField.sendKeys(String.valueOf(answer));

            // "확인" 버튼 클릭
            randomDelay(500, 1000);
            WebElement confirmButton = driver.findElement(By.xpath(
                    "//button[contains(text(),'확인')] | //button[contains(@class,'confirm')]"));
            confirmButton.click();

            randomDelay(2000, 4000);

            // 결과 페이지 소스 반환
            String result = driver.getPageSource();
            log.info("[SeleniumLens] CAPTCHA 풀기 성공 — 판매자 정보 페이지 로드 완료");
            return result;

        } catch (Exception e) {
            log.warn("[SeleniumLens] CAPTCHA 풀기 과정 실패: {} — {}", pageUrl, e.getMessage());
            return null;
        }
    }

    public boolean isEnabled() {
        return enabled && driver != null;
    }

    private void randomDelay() {
        randomDelay(3000, 7000);
    }

    private void randomDelay(int minMs, int maxMs) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(minMs, maxMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record LensResult(String pageUrl, String domain, String title) {}
}
