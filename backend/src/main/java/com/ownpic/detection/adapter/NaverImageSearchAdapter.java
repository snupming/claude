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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 네이버 검색 API (이미지 검색) 어댑터.
 * 키워드 기반으로 네이버 이미지 검색 결과를 반환한다.
 *
 * 네이버 오픈 API: https://developers.naver.com/docs/serviceapi/search/image/image.md
 * - 무료: 일 25,000회
 * - 인증: Client-ID + Client-Secret 헤더
 */
@Component
@Profile("naver")
public class NaverImageSearchAdapter implements InternetImageSearchPort {

    private static final Logger log = LoggerFactory.getLogger(NaverImageSearchAdapter.class);
    private static final String NAVER_API_URL = "https://openapi.naver.com/v1/search/image";

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
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
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
                for (JsonNode item : items) {
                    String imageUrl = textOrNull(item, "link");
                    String thumbnail = textOrNull(item, "thumbnail");
                    String title = stripHtml(textOrNull(item, "title"));

                    if (imageUrl != null) {
                        results.add(new SearchResult(imageUrl, thumbnail, title));
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

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && !child.isNull()) ? child.asText() : null;
    }

    private static String stripHtml(String text) {
        if (text == null) return null;
        return text.replaceAll("<[^>]+>", "").trim();
    }
}
