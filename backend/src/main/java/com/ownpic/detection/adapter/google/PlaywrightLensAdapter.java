package com.ownpic.detection.adapter.google;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.ownpic.detection.port.ReverseImageSearchPort;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Playwright 헤드리스 브라우저로 Google Lens 리버스 이미지 검색.
 * JS 렌더링이 필요한 Google Lens 결과를 정상적으로 크롤링한다.
 *
 * 활성화: SPRING_PROFILES_ACTIVE=google
 * 최초 실행 시 Chromium 자동 설치: npx playwright install chromium
 */
@Component
@Profile("google")
public class PlaywrightLensAdapter implements ReverseImageSearchPort {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightLensAdapter.class);
    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile(
            "(https?://[^\"'\\s]+\\.(?:jpg|jpeg|png|webp|gif)(?:\\?[^\"'\\s]*)?)",
            Pattern.CASE_INSENSITIVE);

    private Playwright playwright;
    private Browser browser;

    @PostConstruct
    public void init() {
        try {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(List.of(
                            "--no-sandbox",
                            "--disable-setuid-sandbox",
                            "--disable-dev-shm-usage",
                            "--disable-gpu",
                            "--lang=ko-KR"
                    )));
            log.info("[Playwright] Chromium 브라우저 시작 완료");
        } catch (Exception e) {
            log.error("[Playwright] 브라우저 시작 실패 — Chromium 설치 필요: npx playwright install chromium", e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
        log.info("[Playwright] 브라우저 종료");
    }

    @Override
    public List<ReverseSearchResult> searchByImage(byte[] imageBytes, int maxResults) {
        if (browser == null) {
            log.warn("[Playwright] 브라우저 미초기화 — 크롤링 스킵");
            return List.of();
        }

        log.info("[Playwright] Google Lens 검색 시작 — imageBytes={}KB", imageBytes.length / 1024);

        BrowserContext context = null;
        try {
            context = browser.newContext(new Browser.NewContextOptions()
                    .setLocale("ko-KR")
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                    .setViewportSize(1707, 906));

            Page page = context.newPage();

            // 1. 임시 파일로 이미지 저장
            Path tempFile = Files.createTempFile("lens_", ".jpg");
            Files.write(tempFile, imageBytes);

            try {
                // 2. Google 이미지 검색 페이지 열기
                page.navigate("https://images.google.com/?hl=ko");
                page.waitForLoadState(LoadState.NETWORKIDLE);
                log.info("[Playwright] Google 이미지 검색 페이지 로드 완료");

                // 3. 카메라 아이콘 (이미지로 검색) 클릭
                Locator cameraButton = page.locator("[aria-label='이미지로 검색'], [aria-label='Search by image'], div.nDcEnd, svg.Gdd5U").first();
                if (cameraButton.isVisible()) {
                    cameraButton.click();
                    page.waitForTimeout(1000);
                    log.info("[Playwright] 이미지 검색 버튼 클릭");
                }

                // 4. 파일 업로드
                Locator fileInput = page.locator("input[type='file']").first();
                if (fileInput.count() > 0) {
                    fileInput.setInputFiles(tempFile);
                    log.info("[Playwright] 이미지 파일 업로드");
                } else {
                    // 대체: 드래그 영역에 직접 업로드
                    page.locator("[aria-label='파일 업로드'], [aria-label='upload a file']").first().click();
                    page.waitForTimeout(500);
                    page.locator("input[type='file']").first().setInputFiles(tempFile);
                    log.info("[Playwright] 대체 경로로 파일 업로드");
                }

                // 5. 검색 결과 대기 (URL 변경 또는 결과 로드)
                page.waitForURL("**/search?*", new Page.WaitForURLOptions().setTimeout(30000));
                page.waitForLoadState(LoadState.NETWORKIDLE);
                page.waitForTimeout(3000); // 추가 JS 렌더링 대기

                String currentUrl = page.url();
                log.info("[Playwright] 결과 페이지 URL: {}", currentUrl);

                // 6. 결과 파싱
                List<ReverseSearchResult> results = extractResults(page, maxResults);
                log.info("[Playwright] 결과 추출 — {} 건", results.size());

                return results;

            } finally {
                Files.deleteIfExists(tempFile);
            }

        } catch (Exception e) {
            log.error("[Playwright] Google Lens 검색 실패", e);
            return List.of();
        } finally {
            if (context != null) context.close();
        }
    }

    private List<ReverseSearchResult> extractResults(Page page, int maxResults) {
        List<ReverseSearchResult> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // 방법 1: 검색 결과 링크에서 이미지 + 출처 추출
        List<ElementHandle> resultElements = page.querySelectorAll("a[href*='imgres?'], div[data-ri] a, div.isv-r a");
        log.info("[Playwright] imgres/grid 링크: {}개", resultElements.size());

        for (ElementHandle el : resultElements) {
            if (results.size() >= maxResults) break;
            try {
                String href = el.getAttribute("href");
                if (href != null && href.contains("imgurl=")) {
                    String imgUrl = extractUrlParam(href, "imgurl");
                    String refUrl = extractUrlParam(href, "imgrefurl");
                    String title = el.textContent();
                    if (imgUrl != null && seen.add(imgUrl)) {
                        results.add(new ReverseSearchResult(imgUrl, refUrl, title != null ? title.trim() : ""));
                    }
                }
            } catch (Exception ignored) {}
        }

        // 방법 2: 외부 이미지 src 추출
        if (results.isEmpty()) {
            List<ElementHandle> images = page.querySelectorAll("img[src^='http']");
            log.info("[Playwright] 외부 이미지: {}개", images.size());

            for (ElementHandle img : images) {
                if (results.size() >= maxResults) break;
                try {
                    String src = img.getAttribute("src");
                    if (src == null || src.contains("google.") || src.contains("gstatic.")) continue;
                    String alt = img.getAttribute("alt");
                    ElementHandle parentLink = img.querySelector("xpath=ancestor::a[@href]");
                    String pageUrl = parentLink != null ? parentLink.getAttribute("href") : null;
                    if (pageUrl != null && pageUrl.contains("google.")) pageUrl = null;
                    if (seen.add(src)) {
                        results.add(new ReverseSearchResult(src, pageUrl, alt != null ? alt : ""));
                    }
                } catch (Exception ignored) {}
            }
        }

        // 방법 3: 페이지 HTML에서 이미지 URL 정규식 추출
        if (results.isEmpty()) {
            String html = page.content();
            Matcher matcher = IMAGE_URL_PATTERN.matcher(html);
            while (matcher.find() && results.size() < maxResults) {
                String url = matcher.group(1);
                if (url.contains("google.") || url.contains("gstatic.") || url.contains("youtube.")) continue;
                if (seen.add(url)) {
                    results.add(new ReverseSearchResult(url, null, ""));
                }
            }
            log.info("[Playwright] 정규식 추출: {}개", results.size());
        }

        return results;
    }

    private String extractUrlParam(String url, String param) {
        int start = url.indexOf(param + "=");
        if (start < 0) return null;
        start += param.length() + 1;
        int end = url.indexOf('&', start);
        String value = end > 0 ? url.substring(start, end) : url.substring(start);
        try {
            return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }
}
