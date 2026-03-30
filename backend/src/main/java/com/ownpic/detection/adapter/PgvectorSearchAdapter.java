package com.ownpic.detection.adapter;

import com.ownpic.detection.port.SimilarImageSearchPort;
import com.ownpic.shared.ml.PgvectorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Profile("pgvector")
class PgvectorSearchAdapter implements SimilarImageSearchPort {

    private static final Logger log = LoggerFactory.getLogger(PgvectorSearchAdapter.class);

    private final JdbcTemplate jdbcTemplate;

    PgvectorSearchAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<SimilarImage> findByUser(float[] embedding, UUID userId, double threshold, int limit) {
        return searchByColumn("embedding", embedding, userId, threshold, limit);
    }

    @Override
    public List<SimilarImage> findAll(float[] embedding, double threshold, int limit) {
        return searchAllByColumn("embedding", embedding, threshold, limit);
    }

    @Override
    public List<SimilarImage> findByUserDino(float[] embedding, UUID userId, double threshold, int limit) {
        return searchByColumn("embedding_dino", embedding, userId, threshold, limit);
    }

    @Override
    public List<SimilarImage> findAllDino(float[] embedding, double threshold, int limit) {
        return searchAllByColumn("embedding_dino", embedding, threshold, limit);
    }

    private List<SimilarImage> searchByColumn(String column, float[] embedding,
                                               UUID userId, double threshold, int limit) {
        double maxDistance = 1.0 - threshold;
        String vectorStr = PgvectorUtils.toVectorString(embedding);

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

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new SimilarImage(
                        rs.getLong("id"),
                        UUID.fromString(rs.getString("user_id")),
                        rs.getDouble("similarity")),
                vectorStr, userId.toString(), maxDistance, limit);
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

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new SimilarImage(
                        rs.getLong("id"),
                        UUID.fromString(rs.getString("user_id")),
                        rs.getDouble("similarity")),
                vectorStr, maxDistance, limit);
    }
}
