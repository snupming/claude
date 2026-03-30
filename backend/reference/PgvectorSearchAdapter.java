package com.ownpic.detection.adapter;

import com.ownpic.detection.port.SimilarImageSearchPort;
import com.ownpic.shared.ml.PgvectorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * pgvector 코사인 유사도 검색 어댑터 — 듀얼 파이프라인.
 *
 * <p>SSCD (embedding, 512-dim) + DINOv2 (embedding_dino, 384-dim).
 * <p>HNSW 인덱스 사용 (R4 검증: P95 15ms @ 100K rows).
 * {@code <=>} 연산자 = 코사인 거리 (1 - 코사인 유사도).
 */
@Component
class PgvectorSearchAdapter implements SimilarImageSearchPort {

    private static final Logger log = LoggerFactory.getLogger(PgvectorSearchAdapter.class);

    private final JdbcTemplate jdbcTemplate;

    PgvectorSearchAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ==================================================================
    // SSCD (embedding column, 512-dim)
    // ==================================================================

    @Override
    public List<SimilarImage> findByUser(float[] embedding, Long userId, double threshold, int limit) {
        return searchByColumn("embedding", embedding, userId, threshold, limit);
    }

    @Override
    public List<SimilarImage> findAll(float[] embedding, double threshold, int limit) {
        return searchAllByColumn("embedding", embedding, threshold, limit);
    }

    // ==================================================================
    // DINOv2 (embedding_dino column, 384-dim)
    // ==================================================================

    @Override
    public List<SimilarImage> findByUserDino(float[] embedding, Long userId, double threshold, int limit) {
        return searchByColumn("embedding_dino", embedding, userId, threshold, limit);
    }

    @Override
    public List<SimilarImage> findAllDino(float[] embedding, double threshold, int limit) {
        return searchAllByColumn("embedding_dino", embedding, threshold, limit);
    }

    // ==================================================================
    // 공통 쿼리 (컬럼명만 다름)
    // ==================================================================

    private List<SimilarImage> searchByColumn(String column, float[] embedding,
                                               Long userId, double threshold, int limit) {
        double maxDistance = 1.0 - threshold;
        String vectorStr = PgvectorUtils.toVectorString(embedding);

        // 컬럼명은 코드에서 결정되는 상수이므로 SQL injection 위험 없음
        String sql = """
                WITH q AS (SELECT ?::vector AS vec)
                SELECT i.id, i.user_id, 1 - (i.%s <=> q.vec) AS similarity
                FROM images i, q
                WHERE i.%s IS NOT NULL
                  AND i.user_id = ?
                  AND (i.%s <=> q.vec) < ?
                ORDER BY i.%s <=> q.vec
                LIMIT ?
                """.formatted(column, column, column, column);

        List<SimilarImage> results = jdbcTemplate.query(sql,
                (rs, rowNum) -> new SimilarImage(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getDouble("similarity")
                ),
                vectorStr, userId, maxDistance, limit
        );

        log.debug("[pgvector] {} findByUser: userId={}, threshold={}, found={}",
                column, userId, threshold, results.size());
        return results;
    }

    private List<SimilarImage> searchAllByColumn(String column, float[] embedding,
                                                  double threshold, int limit) {
        double maxDistance = 1.0 - threshold;
        String vectorStr = PgvectorUtils.toVectorString(embedding);

        String sql = """
                WITH q AS (SELECT ?::vector AS vec)
                SELECT i.id, i.user_id, 1 - (i.%s <=> q.vec) AS similarity
                FROM images i, q
                WHERE i.%s IS NOT NULL
                  AND (i.%s <=> q.vec) < ?
                ORDER BY i.%s <=> q.vec
                LIMIT ?
                """.formatted(column, column, column, column);

        List<SimilarImage> results = jdbcTemplate.query(sql,
                (rs, rowNum) -> new SimilarImage(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getDouble("similarity")
                ),
                vectorStr, maxDistance, limit
        );

        log.debug("[pgvector] {} findAll: threshold={}, found={}",
                column, threshold, results.size());
        return results;
    }
}
