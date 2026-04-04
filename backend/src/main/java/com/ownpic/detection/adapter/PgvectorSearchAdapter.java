package com.ownpic.detection.adapter;

import com.ownpic.detection.port.SimilarImageSearchPort;
import com.ownpic.shared.ml.PgvectorUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Profile("pgvector")
class PgvectorSearchAdapter implements SimilarImageSearchPort {

    private final JdbcTemplate jdbcTemplate;

    PgvectorSearchAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<SimilarImage> findByUser(float[] embedding, UUID userId, double threshold, int limit) {
        return searchByColumn("embedding_sscd", embedding, userId, threshold, limit);
    }

    @Override
    public List<SimilarImage> findAll(float[] embedding, double threshold, int limit) {
        return searchAllByColumn("embedding_sscd", embedding, threshold, limit);
    }

    @Override
    public List<SimilarImage> findByUserDino(float[] embedding, UUID userId, double threshold, int limit) {
        return searchByColumn("embedding_dino_vec", embedding, userId, threshold, limit);
    }

    @Override
    public List<SimilarImage> findAllDino(float[] embedding, double threshold, int limit) {
        return searchAllByColumn("embedding_dino_vec", embedding, threshold, limit);
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
                (rs, _) -> new SimilarImage(
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
                (rs, _) -> new SimilarImage(
                        rs.getLong("id"),
                        UUID.fromString(rs.getString("user_id")),
                        rs.getDouble("similarity")),
                vectorStr, maxDistance, limit);
    }

    @Override
    public List<BatchResult> findAllBatch(List<ImageEmbedding> embeddings, double threshold, int limitPerImage) {
        return batchSearchByColumn("embedding_sscd", embeddings, threshold, limitPerImage);
    }

    @Override
    public List<BatchResult> findAllDinoBatch(List<ImageEmbedding> embeddings, double threshold, int limitPerImage) {
        return batchSearchByColumn("embedding_dino_vec", embeddings, threshold, limitPerImage);
    }

    private List<BatchResult> batchSearchByColumn(String column, List<ImageEmbedding> embeddings,
                                                   double threshold, int limitPerImage) {
        if (embeddings.isEmpty()) return List.of();

        double maxDistance = 1.0 - threshold;

        // VALUES 절 동적 구성: (source_id, vec::vector), ...
        var values = new StringBuilder();
        var params = new java.util.ArrayList<>();
        for (int idx = 0; idx < embeddings.size(); idx++) {
            if (idx > 0) values.append(", ");
            values.append("(?, ?::vector)");
            params.add(embeddings.get(idx).imageId());
            params.add(PgvectorUtils.toVectorString(embeddings.get(idx).embedding()));
        }
        params.add(maxDistance);
        params.add(limitPerImage);

        String sql = """
                WITH source_embeddings(source_id, vec) AS (VALUES %s)
                SELECT s.source_id, sub.id AS matched_id, sub.user_id AS matched_user_id, sub.similarity
                FROM source_embeddings s
                CROSS JOIN LATERAL (
                    SELECT i.id, i.user_id, 1 - (i.%s <=> s.vec) AS similarity
                    FROM images i
                    WHERE i.%s IS NOT NULL
                      AND (i.%s <=> s.vec) < ?
                    ORDER BY i.%s <=> s.vec
                    LIMIT ?
                ) sub
                """.formatted(values, column, column, column, column);

        return jdbcTemplate.query(sql,
                (rs, _) -> new BatchResult(
                        rs.getLong("source_id"),
                        rs.getLong("matched_id"),
                        UUID.fromString(rs.getString("matched_user_id")),
                        rs.getDouble("similarity")),
                params.toArray());
    }
}
