package com.ownpic.detection.adapter.google;

import javax.net.ssl.SSLContext;
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

    private TrustingHttpClientFactory() {}

    static HttpClient create(int timeoutSeconds) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new TrustAllManager()}, new java.security.SecureRandom());

            return HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                    .sslContext(sslContext)
                    .build();
        } catch (Exception e) {
            // SSLContext 생성 실패 시 기본 HttpClient 사용
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
