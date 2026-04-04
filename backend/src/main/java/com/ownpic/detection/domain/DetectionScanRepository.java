package com.ownpic.detection.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DetectionScanRepository extends JpaRepository<DetectionScan, Long> {
    Page<DetectionScan> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    Optional<DetectionScan> findByIdAndUserId(Long id, UUID userId);
}
