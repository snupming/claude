package com.ownpic.detection.adapter;

import com.ownpic.detection.port.ImageCaptionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * Claude Vision API를 사용하여 이미지에서 검색용 키워드를 자동 생성하는 어댑터.
 * API 키가 설정되지 않으면 null을 반환하여 다른 fallback이 작동하도록 한다.
 */
@Component
public class ClaudeImageCaptionAdapter implements ImageCaptionPort {

    private static final Logger log = LoggerFactory.getLogger(ClaudeImageCaptionAdapter.class);
    private static final String API_URL = "https://api.anthropic.com/v1/messages";

    private final String apiKey;
    private final HttpClient httpClient;

    public ClaudeImageCaptionAdapter(
            @Value("${ownpic.anthropic.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String generateKeywords(byte[] imageBytes) {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("Anthropic API key not configured, skipping caption generation");
            return null;
        }
        if (imageBytes == null || imageBytes.length == 0) {
            return null;
        }

        try {
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String mediaType = detectMediaType(imageBytes);

            String requestBody = """
                    {
                      "model": "claude-sonnet-4-20250514",
                      "max_tokens": 100,
                      "messages": [{
                        "role": "user",
                        "content": [
                          {
                            "type": "image",
                            "source": {
                              "type": "base64",
                              "media_type": "%s",
                              "data": "%s"
                            }
                          },
                          {
                            "type": "text",
                            "text": "이 상품 이미지를 네이버 이미지 검색에서 찾기 위한 한국어 검색 키워드를 생성해주세요. 상품명, 카테고리, 특징을 포함하여 공백으로 구분된 키워드만 출력하세요. 설명 없이 키워드만 출력."
                          }
                        ]
                      }]
                    }
                    """.formatted(mediaType, base64Image);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Claude API returned status {}: {}", response.statusCode(), response.body());
                return null;
            }

            return extractText(response.body());
        } catch (Exception e) {
            log.warn("Failed to generate caption via Claude API: {}", e.getMessage());
            return null;
        }
    }

    private String extractText(String responseJson) {
        // 간단한 JSON 파싱 — "text" 필드 추출
        int textIdx = responseJson.indexOf("\"text\"");
        if (textIdx < 0) return null;

        int colonIdx = responseJson.indexOf(':', textIdx);
        if (colonIdx < 0) return null;

        int startQuote = responseJson.indexOf('"', colonIdx + 1);
        if (startQuote < 0) return null;

        int endQuote = startQuote + 1;
        while (endQuote < responseJson.length()) {
            char c = responseJson.charAt(endQuote);
            if (c == '\\') {
                endQuote += 2; // skip escaped char
                continue;
            }
            if (c == '"') break;
            endQuote++;
        }

        String text = responseJson.substring(startQuote + 1, endQuote);
        // unescape
        text = text.replace("\\n", " ").replace("\\\"", "\"").replace("\\\\", "\\");
        return text.trim().isEmpty() ? null : text.trim();
    }

    private String detectMediaType(byte[] bytes) {
        if (bytes.length >= 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) {
            return "image/jpeg";
        }
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50) {
            return "image/png";
        }
        if (bytes.length >= 4 && bytes[0] == 0x52 && bytes[1] == 0x49) {
            return "image/webp";
        }
        return "image/jpeg"; // default
    }
}
