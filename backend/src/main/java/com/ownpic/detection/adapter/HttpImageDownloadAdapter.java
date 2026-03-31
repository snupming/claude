package com.ownpic.detection.adapter;

import com.ownpic.detection.port.ExternalImageDownloadPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

@Component
public class HttpImageDownloadAdapter implements ExternalImageDownloadPort {

    private static final Logger log = LoggerFactory.getLogger(HttpImageDownloadAdapter.class);
    private static final long MAX_SIZE = 15 * 1024 * 1024; // 15MB
    private static final Set<String> IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/avif", "image/gif");

    private final HttpClient httpClient;

    public HttpImageDownloadAdapter() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public byte[] download(String imageUrl, int timeoutMs) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(imageUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) return null;

            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (IMAGE_TYPES.stream().noneMatch(contentType::startsWith)) return null;
            if (response.body().length > MAX_SIZE) return null;

            return response.body();
        } catch (Exception e) {
            log.debug("Image download failed: {}", imageUrl);
            return null;
        }
    }
}
