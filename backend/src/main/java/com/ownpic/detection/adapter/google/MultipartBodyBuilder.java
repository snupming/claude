package com.ownpic.detection.adapter.google;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class MultipartBodyBuilder {

    private MultipartBodyBuilder() {}

    static Result build(byte[] imageBytes, String filename) {
        String boundary = "----WebKitFormBoundary" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String contentType = detectContentType(imageBytes);

        List<byte[]> parts = new ArrayList<>();

        // encoded_image part — 구글 searchbyimage 업로드 필드명
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"encoded_image\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n";
        parts.add(header.getBytes(StandardCharsets.UTF_8));
        parts.add(imageBytes);
        parts.add("\r\n".getBytes(StandardCharsets.UTF_8));

        // closing boundary
        parts.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        int totalLen = parts.stream().mapToInt(b -> b.length).sum();
        byte[] body = new byte[totalLen];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, body, offset, part.length);
            offset += part.length;
        }

        return new Result(
                HttpRequest.BodyPublishers.ofByteArray(body),
                "multipart/form-data; boundary=" + boundary
        );
    }

    private static String detectContentType(byte[] bytes) {
        if (bytes.length >= 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) {
            return "image/jpeg";
        }
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50) {
            return "image/png";
        }
        if (bytes.length >= 12 && bytes[0] == 0x52 && bytes[1] == 0x49) {
            return "image/webp";
        }
        return "image/jpeg"; // default
    }

    record Result(HttpRequest.BodyPublisher bodyPublisher, String contentType) {}
}
