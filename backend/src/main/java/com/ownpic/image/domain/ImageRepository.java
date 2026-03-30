package com.ownpic.image.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ImageRepository extends JpaRepository<Image, Long> {

    @Query(value = "SELECT nextval('images_id_seq')", nativeQuery = true)
    Long allocateId();

    Page<Image> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Image> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, ImageStatus status, Pageable pageable);

    Page<Image> findByUserIdAndSourceTypeOrderByCreatedAtDesc(UUID userId, SourceType sourceType, Pageable pageable);

    Optional<Image> findByUserIdAndSha256(UUID userId, String sha256);

    long countByUserId(UUID userId);

    boolean existsByUserIdAndSha256(UUID userId, String sha256);
}
