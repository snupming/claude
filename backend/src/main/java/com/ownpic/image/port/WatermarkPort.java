package com.ownpic.image.port;

public interface WatermarkPort {
    WatermarkResult encode(byte[] imageBytes, String payload);

    record WatermarkResult(byte[] watermarkedImage, String payload) {}
}
