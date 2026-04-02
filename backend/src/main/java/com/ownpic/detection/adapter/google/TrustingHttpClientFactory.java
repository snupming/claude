package com.ownpic.detection.adapter.google;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.cert.X509Certificate;
import java.time.Duration;

/**
 * Google 스크래핑용 HttpClient 팩토리.
 * JVM cacerts에 Google CA가 없는 환경을 위해 SSL 검증을 완화한다.
 */
final class TrustingHttpClientFactory {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(TrustingHttpClientFactory.class);

    private TrustingHttpClientFactory() {}

    static HttpClient create(int timeoutSeconds) {
        return create(timeoutSeconds, true);
    }

    static HttpClient create(int timeoutSeconds, boolean followRedirects) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new TrustAllManager()}, new java.security.SecureRandom());

            SSLParameters sslParams = new SSLParameters();
            sslParams.setEndpointIdentificationAlgorithm(null);

            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(followRedirects ? HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER)
                    .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                    .sslContext(sslContext)
                    .sslParameters(sslParams)
                    .build();

            log.info("TrustingHttpClient created — SSL 완화, followRedirects={}", followRedirects);
            return client;
        } catch (Exception e) {
            log.error("TrustingHttpClient 생성 실패 — 기본 HttpClient 사용: {}", e.getMessage());
            return HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                    .build();
        }
    }

    private static class TrustAllManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
