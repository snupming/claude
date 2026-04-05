package com.ownpic.detection.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ownpic.detection.port.InternetImageSearchPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * 네이버 검색 API (이미지 검색) 어댑터.
 * 키워드 기반으로 네이버 이미지 검색 결과를 반환한다.
 * 네이버 오픈 API: <a href="https://developers.naver.com/docs/serviceapi/search/shopping/shopping.md"/>
 * - 무료: 일 25,000회
 * - 인증: Client-ID + Client-Secret 헤더
 */
@Component
@Profile("naver")
public class NaverImageSearchAdapter implements InternetImageSearchPort {

    private static final Logger log = LoggerFactory.getLogger(NaverImageSearchAdapter.class);
    private static final String NAVER_API_URL = "https://openapi.naver.com/v1/search/shop.json";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String clientId;
    private final String clientSecret;

    public NaverImageSearchAdapter(
            @Value("${ownpic.naver.client-id}") String clientId,
            @Value("${ownpic.naver.client-secret}") String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.objectMapper = new ObjectMapper();
        this.httpClient = createTrustingHttpClient();
    }

    @Override
    public List<SearchResult> searchByKeyword(String keyword, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        int display = Math.min(maxResults, 100); // 네이버 API 최대 100

        try {
            String encodedQuery = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String url = NAVER_API_URL + "?query=" + encodedQuery + "&display=" + display;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("X-Naver-Client-Id", clientId)
                    .header("X-Naver-Client-Secret", clientSecret)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("[Naver] ===== API 응답 RAW 시작 (keyword='{}') =====", keyword);
            log.info("[Naver] HTTP Status: {}", response.statusCode());
            log.info("[Naver] Response Body ({}자): {}", response.body().length(),
                    response.body().length() > 2000 ? response.body().substring(0, 2000) + "..." : response.body());
            log.info("[Naver] ===== API 응답 RAW 끝 =====");

            if (response.statusCode() != 200) {
                log.warn("[Naver] API 에러 status={}: {}", response.statusCode(), response.body());
                return results;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode items = root.get("items");

            if (items != null && items.isArray()) {
                // mallName별 그룹핑
                Map<String, List<ParsedItem>> groups = new LinkedHashMap<>();
                int itemIndex = 0;
                for (JsonNode item : items) {
                    String imageUrl = textOrNull(item, "image");
                    String link = textOrNull(item, "link");
                    String title = stripHtml(textOrNull(item, "title"));
                    String mallName = textOrNull(item, "mallName");

                    if (imageUrl == null) continue;

                    // 그룹 키: mallName 있으면 mallName, 없으면 item 인덱스 (개별 처리)
                    String groupKey = (mallName != null && !mallName.isBlank())
                            ? mallName : "__no_mall_" + (itemIndex++);
                    groups.computeIfAbsent(groupKey, k -> new ArrayList<>())
                            .add(new ParsedItem(imageUrl, link, title, mallName));
                }

                // 그룹별 대표 link 선택 + SearchResult 생성
                for (var entry : groups.entrySet()) {
                    List<ParsedItem> group = entry.getValue();
                    String bestLink = selectBestLink(group);
                    ParsedItem first = group.get(0);
                    String title = first.title;
                    String mallName = first.mallName;

                    if (mallName != null && title != null && !title.contains(mallName)) {
                        title = title + " [" + mallName + "]";
                    } else if (mallName != null && title == null) {
                        title = mallName;
                    }

                    results.add(new SearchResult(first.imageUrl, bestLink, title, mallName));

                    if (group.size() > 1) {
                        log.info("[Naver] 그룹핑: '{}' — {}개 상품 → 대표 link={}", entry.getKey(), group.size(), bestLink);
                    }
                }
            }

            log.info("[Naver] keyword='{}' → {}건 결과", keyword, results.size());
            for (int i = 0; i < Math.min(5, results.size()); i++) {
                var sr = results.get(i);
                log.info("[Naver]   [{}] img={} page={} title={}", i, sr.imageUrl(), sr.sourcePageUrl(), sr.title());
            }
        } catch (Exception e) {
            log.error("Naver image search failed for keyword '{}'", keyword, e);
        }

        return results;
    }

    private static HttpClient createTrustingHttpClient() {
        try {
            TrustManager[] trustAll = { new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }};
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, new SecureRandom());
            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        } catch (Exception e) {
            return HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && !child.isNull()) ? child.asText() : null;
    }

    private static String stripHtml(String text) {
        if (text == null) return null;
        return text.replaceAll("<[^>]+>", "").trim();
    }

    /**
     * 같은 mallName 그룹 내에서 최적 link URL 선택.
     * 우선순위: smartstore.naver.com > link.coupang.com > link.gmarket.co.kr > 자체몰 > outlink
     */
    private static String selectBestLink(List<ParsedItem> group) {
        String smartStore = null, coupang = null, gmarket = null, outlink = null, other = null;

        for (ParsedItem item : group) {
            String link = item.link;
            if (link == null) continue;
            if (link.contains("smartstore.naver.com/main")) {
                smartStore = link;
            } else if (link.contains("link.coupang.com")) {
                coupang = link;
            } else if (link.contains("link.gmarket.co.kr")) {
                gmarket = link;
            } else if (link.contains("shopping.naver.com/outlink")) {
                outlink = link;
            } else {
                other = link;
            }
        }

        // 우선순위: smartstore > coupang > gmarket > 자체몰(other) > outlink
        if (smartStore != null) return smartStore;
        if (coupang != null) return coupang;
        if (gmarket != null) return gmarket;
        if (other != null) return other;
        if (outlink != null) return outlink;
        return group.get(0).link;
    }

    private record ParsedItem(String imageUrl, String link, String title, String mallName) {}
}
