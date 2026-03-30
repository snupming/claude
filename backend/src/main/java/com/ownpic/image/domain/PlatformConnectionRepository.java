package com.ownpic.image.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformConnectionRepository extends JpaRepository<PlatformConnection, Long> {

    List<PlatformConnection> findByUserId(UUID userId);

    Optional<PlatformConnection> findByUserIdAndPlatform(UUID userId, PlatformType platform);

    boolean existsByUserIdAndPlatform(UUID userId, PlatformType platform);
}
