package com.ownpic.detection.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InternetDetectionResultRepository extends JpaRepository<InternetDetectionResult, Long> {
    List<InternetDetectionResult> findByScanIdOrderByCreatedAt(Long scanId);
}
