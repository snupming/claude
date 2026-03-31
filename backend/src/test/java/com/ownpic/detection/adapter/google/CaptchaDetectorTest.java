package com.ownpic.detection.adapter.google;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CaptchaDetectorTest {

    private final CaptchaDetector detector = new CaptchaDetector();

    @Test
    void isCaptcha_status429_returnsTrue() {
        assertThat(detector.isCaptchaResponse("<html></html>", "https://google.com", 429)).isTrue();
    }

    @Test
    void isCaptcha_sorryGoogleUrl_returnsTrue() {
        assertThat(detector.isCaptchaResponse("<html></html>", "https://sorry.google.com/sorry", 200)).isTrue();
    }

    @Test
    void isCaptcha_recaptchaId_returnsTrue() {
        String html = "<html><body><div id=\"recaptcha\"></div></body></html>";
        assertThat(detector.isCaptchaResponse(html, "https://google.com", 200)).isTrue();
    }

    @Test
    void isCaptcha_recaptchaClass_returnsTrue() {
        String html = "<html><body><div class=\"g-recaptcha\"></div></body></html>";
        assertThat(detector.isCaptchaResponse(html, "https://google.com", 200)).isTrue();
    }

    @Test
    void isCaptcha_unusualTraffic_returnsTrue() {
        String html = "<html><body>Our systems have detected unusual traffic from your computer network.</body></html>";
        assertThat(detector.isCaptchaResponse(html, "https://google.com", 200)).isTrue();
    }

    @Test
    void isCaptcha_automatedQueries_returnsTrue() {
        String html = "<html><body>automated queries</body></html>";
        assertThat(detector.isCaptchaResponse(html, "https://google.com", 200)).isTrue();
    }

    @Test
    void isCaptcha_normalPage_returnsFalse() {
        String html = "<html><body><div>Normal search results with lots of content here for testing purposes" +
                " and making sure the page is long enough to not trigger the short page check.</div>" +
                "<div>More content here to pad the length.</div>" +
                "<div>Even more content.</div>" +
                "<div>And some more for good measure.</div>" +
                "<div>This should be well over 1000 characters now with all this padding content.</div>" +
                "<div>Adding extra padding to make absolutely sure we exceed the threshold.</div>" +
                "<div>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor.</div>" +
                "<div>Final padding to ensure we're definitely over 1000 chars in total.</div>" +
                "</body></html>";
        assertThat(detector.isCaptchaResponse(html, "https://google.com/search", 200)).isFalse();
    }

    @Test
    void isCaptcha_nullHtml_returnsFalse() {
        assertThat(detector.isCaptchaResponse(null, "https://google.com", 200)).isFalse();
    }

    @Test
    void isCaptcha_status200_noIndicators_returnsFalse() {
        String html = "<html><body>" + "x".repeat(2000) + "</body></html>";
        assertThat(detector.isCaptchaResponse(html, "https://lens.google.com/results", 200)).isFalse();
    }
}
