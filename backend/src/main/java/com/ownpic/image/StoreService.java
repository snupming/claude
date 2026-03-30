package com.ownpic.image;

import com.ownpic.auth.domain.User;
import com.ownpic.auth.domain.UserRepository;
import com.ownpic.image.domain.*;
import com.ownpic.image.dto.PlatformConnectionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class StoreService {

    private static final Logger log = LoggerFactory.getLogger(StoreService.class);

    private final PlatformConnectionRepository connectionRepository;
    private final ImageRepository imageRepository;
    private final UserRepository userRepository;

    public StoreService(PlatformConnectionRepository connectionRepository,
                        ImageRepository imageRepository,
                        UserRepository userRepository) {
        this.connectionRepository = connectionRepository;
        this.imageRepository = imageRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public PlatformConnectionResponse connect(UUID userId, String platformName) {
        var platformType = parsePlatform(platformName);

        if (connectionRepository.existsByUserIdAndPlatform(userId, platformType)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 연동된 플랫폼입니다.");
        }

        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        var connection = new PlatformConnection();
        connection.setUser(user);
        connection.setPlatform(platformType);
        connection.setStatus(ConnectionStatus.SYNCING);
        connectionRepository.save(connection);

        // TODO: 실제 플랫폼 OAuth 인증 및 상품 목록 가져오기
        // 이 부분은 사용자가 직접 구현 예정
        // fetchAndSaveProducts(user, connection);

        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setLastSyncedAt(Instant.now());
        connectionRepository.save(connection);

        return toResponse(connection);
    }

    @Transactional(readOnly = true)
    public List<PlatformConnectionResponse> getConnections(UUID userId) {
        return connectionRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void disconnect(UUID userId, Long connectionId) {
        var connection = connectionRepository.findById(connectionId)
                .filter(c -> c.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "연동 정보를 찾을 수 없습니다."));

        connection.setStatus(ConnectionStatus.DISCONNECTED);
        connectionRepository.save(connection);
    }

    @Transactional
    public void syncProducts(UUID userId, Long connectionId) {
        var connection = connectionRepository.findById(connectionId)
                .filter(c -> c.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "연동 정보를 찾을 수 없습니다."));

        connection.setStatus(ConnectionStatus.SYNCING);
        connectionRepository.save(connection);

        // TODO: 실제 플랫폼 API로 상품 목록 가져와서 images 테이블에 저장
        // 1. 플랫폼 API에서 상품 목록 조회
        // 2. 각 상품의 이미지 URL 추출
        // 3. 이미지 다운로드 → SHA256 해싱 → 중복 체크
        // 4. Image 엔티티 생성 (sourceType=NAVER/COUPANG, sourceProductId, sourceImageUrl)
        // 5. DB 저장
        // 6. 워터마킹 + SSCD 임베딩 파이프라인 트리거 (ImageProtectedEvent)
        log.info("TODO: 플랫폼 {} 상품 동기화 - 사용자 직접 구현 필요", connection.getPlatform());

        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setLastSyncedAt(Instant.now());
        connectionRepository.save(connection);
    }

    private PlatformType parsePlatform(String name) {
        try {
            return PlatformType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 플랫폼입니다: " + name);
        }
    }

    private PlatformConnectionResponse toResponse(PlatformConnection connection) {
        return new PlatformConnectionResponse(
                connection.getId(),
                connection.getPlatform().name(),
                connection.getStoreName(),
                connection.getStatus().name(),
                connection.getLastSyncedAt(),
                connection.getCreatedAt()
        );
    }
}
