package com.ownpic.image.port;

public class WatermarkException extends RuntimeException {
    public WatermarkException(String message) {
        super(message);
    }

    public WatermarkException(String message, Throwable cause) {
        super(message, cause);
    }
}
