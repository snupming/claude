package com.ownpic.image.adapter;

import java.util.zip.CRC32;

/**
 * TrustMark 100비트 페이로드 구성 유틸리티.
 *
 * <p>페이로드 구조 (Phase B 설계서 섹션 2.5):
 * <pre>
 * userId(32bit) + imageId(32bit) + timestamp(24bit) + checksum(12bit) = 100bit
 * </pre>
 *
 * <ul>
 *   <li>{@code userId}: 셀러 내부 PK (최대 2^32 - 1 = ~42억)</li>
 *   <li>{@code imageId}: 이미지 내부 PK (최대 2^32 - 1)</li>
 *   <li>{@code timestamp}: epoch 분 단위 (2^24분 ≈ 31년). 2024-01-01 기준 오프셋</li>
 *   <li>{@code checksum}: 앞 88비트의 CRC-12 (무결성 검증)</li>
 * </ul>
 *
 * <p>도용 이미지에서 TrustMark 디코딩 성공 시, 페이로드에서 userId와 imageId를
 * 추출하여 출처를 자동 증명합니다.
 */
public final class TrustMarkPayloadBuilder {

    private static final int TOTAL_BITS = 100;
    private static final int USER_ID_BITS = 32;
    private static final int IMAGE_ID_BITS = 32;
    private static final int TIMESTAMP_BITS = 24;
    private static final int CHECKSUM_BITS = 12;

    /** timestamp 기준점: 2024-01-01T00:00:00Z (epoch millis) */
    private static final long EPOCH_OFFSET_MILLIS = 1704067200000L;

    private TrustMarkPayloadBuilder() {
    }

    /**
     * 100비트 페이로드 문자열을 생성합니다.
     *
     * @param userId  셀러 내부 PK
     * @param imageId 이미지 내부 PK (시퀀스에서 미리 할당)
     * @return "0"과 "1"로 구성된 길이 100의 문자열
     */
    public static String build(long userId, long imageId) {
        var sb = new StringBuilder(TOTAL_BITS);

        // userId (32bit, unsigned)
        appendBits(sb, userId, USER_ID_BITS);

        // imageId (32bit, unsigned)
        appendBits(sb, imageId, IMAGE_ID_BITS);

        // timestamp (24bit, 분 단위 오프셋)
        long minutesSinceEpoch = (System.currentTimeMillis() - EPOCH_OFFSET_MILLIS) / 60_000;
        long clampedMinutes = minutesSinceEpoch & 0xFFFFFFL; // 24bit mask
        appendBits(sb, clampedMinutes, TIMESTAMP_BITS);

        // checksum (12bit, CRC-32의 하위 12bit)
        String dataBits = sb.toString(); // 88 bits so far
        int crc12 = computeCrc12(dataBits);
        appendBits(sb, crc12, CHECKSUM_BITS);

        return sb.toString();
    }

    /**
     * 페이로드에서 userId를 추출합니다 (디코딩 시 사용).
     */
    public static long extractUserId(String payload) {
        if (payload.length() != TOTAL_BITS) {
            throw new IllegalArgumentException("Payload must be " + TOTAL_BITS + " bits");
        }
        return parseBits(payload, 0, USER_ID_BITS);
    }

    /**
     * 페이로드에서 imageId를 추출합니다 (디코딩 시 사용).
     */
    public static long extractImageId(String payload) {
        if (payload.length() != TOTAL_BITS) {
            throw new IllegalArgumentException("Payload must be " + TOTAL_BITS + " bits");
        }
        return parseBits(payload, USER_ID_BITS, IMAGE_ID_BITS);
    }

    /**
     * checksum을 검증합니다.
     */
    public static boolean verifyChecksum(String payload) {
        if (payload.length() != TOTAL_BITS) return false;
        String dataBits = payload.substring(0, USER_ID_BITS + IMAGE_ID_BITS + TIMESTAMP_BITS);
        int expected = computeCrc12(dataBits);
        int actual = (int) parseBits(payload, USER_ID_BITS + IMAGE_ID_BITS + TIMESTAMP_BITS, CHECKSUM_BITS);
        return expected == actual;
    }

    // --- Internal ---

    private static void appendBits(StringBuilder sb, long value, int bits) {
        for (int i = bits - 1; i >= 0; i--) {
            sb.append((value >> i) & 1);
        }
    }

    private static long parseBits(String bits, int offset, int length) {
        long result = 0;
        for (int i = 0; i < length; i++) {
            result = (result << 1) | (bits.charAt(offset + i) - '0');
        }
        return result;
    }

    private static int computeCrc12(String bits) {
        var crc = new CRC32();
        byte[] bytes = bits.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        crc.update(bytes);
        return (int) (crc.getValue() & 0xFFF); // 하위 12bit
    }
}
