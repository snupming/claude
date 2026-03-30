package com.ownpic.shared.ml;

/**
 * pgvector 유틸리티 — float[] → pgvector 문자열 변환.
 */
public final class PgvectorUtils {

    private PgvectorUtils() {}

    /**
     * float 배열을 pgvector 문자열 포맷으로 변환.
     * 예: [0.1, 0.2, 0.3] → "[0.1,0.2,0.3]"
     */
    public static String toVectorString(float[] embedding) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
