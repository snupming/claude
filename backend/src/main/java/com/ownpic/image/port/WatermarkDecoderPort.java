package com.ownpic.image.port;

public interface WatermarkDecoderPort {
    DecodeResult decode(byte[] imageBytes);

    record DecodeResult(boolean detected, String payload) {
        public static DecodeResult detected(String payload) {
            return new DecodeResult(true, payload);
        }

        public static DecodeResult notDetected() {
            return new DecodeResult(false, null);
        }
    }
}
