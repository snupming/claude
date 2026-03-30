package com.ownpic.image.adapter;

import java.util.zip.CRC32;

/**
 * TrustMark 100비트 페이로드 구성 유틸리티.
 *
 * <p>페이로드 구조:
 * <pre>
 * userId(32bit) + imageId(32bit) + timestamp(24bit) + checksum(12bit) = 100bit
 * </pre>
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

    public static String build(long userId, long imageId) {
        var sb = new StringBuilder(TOTAL_BITS);

        appendBits(sb, userId, USER_ID_BITS);
        appendBits(sb, imageId, IMAGE_ID_BITS);

        long minutesSinceEpoch = (System.currentTimeMillis() - EPOCH_OFFSET_MILLIS) / 60_000;
        long clampedMinutes = minutesSinceEpoch & 0xFFFFFFL;
        appendBits(sb, clampedMinutes, TIMESTAMP_BITS);

        String dataBits = sb.toString();
        int crc12 = computeCrc12(dataBits);
        appendBits(sb, crc12, CHECKSUM_BITS);

        return sb.toString();
    }

    public static long extractUserId(String payload) {
        if (payload.length() != TOTAL_BITS) {
            throw new IllegalArgumentException("Payload must be " + TOTAL_BITS + " bits");
        }
        return parseBits(payload, 0, USER_ID_BITS);
    }

    public static long extractImageId(String payload) {
        if (payload.length() != TOTAL_BITS) {
            throw new IllegalArgumentException("Payload must be " + TOTAL_BITS + " bits");
        }
        return parseBits(payload, USER_ID_BITS, IMAGE_ID_BITS);
    }

    public static boolean verifyChecksum(String payload) {
        if (payload.length() != TOTAL_BITS) return false;
        String dataBits = payload.substring(0, USER_ID_BITS + IMAGE_ID_BITS + TIMESTAMP_BITS);
        int expected = computeCrc12(dataBits);
        int actual = (int) parseBits(payload, USER_ID_BITS + IMAGE_ID_BITS + TIMESTAMP_BITS, CHECKSUM_BITS);
        return expected == actual;
    }

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
        return (int) (crc.getValue() & 0xFFF);
    }
}
