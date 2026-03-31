package com.ownpic.detection.adapter.google;

public class GoogleSearchException extends Exception {

    private final boolean captcha;
    private final int statusCode;

    public GoogleSearchException(String message, boolean captcha, int statusCode) {
        super(message);
        this.captcha = captcha;
        this.statusCode = statusCode;
    }

    public GoogleSearchException(String message, Throwable cause) {
        super(message, cause);
        this.captcha = false;
        this.statusCode = -1;
    }

    public boolean isCaptcha() {
        return captcha;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
