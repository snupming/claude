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

        List<byte[]> parts = new ArrayList<>();

        // encoded_image part
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"encoded_image\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
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

    record Result(HttpRequest.BodyPublisher bodyPublisher, String contentType) {}
}
