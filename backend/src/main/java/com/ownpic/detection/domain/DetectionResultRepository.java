package com.ownpic.detection.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DetectionResultRepository extends JpaRepository<DetectionResult, Long> {
    List<DetectionResult> findByScanIdOrderByCreatedAt(Long scanId);
    int countByScanId(Long scanId);
}
