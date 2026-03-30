package com.ownpic.image.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ImageRepository extends JpaRepository<Image, Long> {

    Page<Image> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Image> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, ImageStatus status, Pageable pageable);

    Page<Image> findByUserIdAndSourceTypeOrderByCreatedAtDesc(UUID userId, SourceType sourceType, Pageable pageable);

    Optional<Image> findByUserIdAndSha256(UUID userId, String sha256);

    long countByUserId(UUID userId);

    boolean existsByUserIdAndSha256(UUID userId, String sha256);
}
