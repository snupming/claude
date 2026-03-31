package com.ownpic.detection.adapter.google;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("google")
public class CaptchaDetector {

    public boolean isCaptchaResponse(String html, String finalUrl, int statusCode) {
        if (statusCode == 429) return true;
        if (finalUrl != null && finalUrl.contains("sorry.google.com")) return true;
        if (html == null) return false;
        if (html.contains("id=\"recaptcha\"") || html.contains("class=\"g-recaptcha\"")) return true;
        if (html.contains("unusual traffic") || html.contains("automated queries")) return true;
        return html.length() < 1000 && html.contains("<form") && html.contains("captcha");
    }
}
