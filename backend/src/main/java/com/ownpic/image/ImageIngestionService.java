package com.ownpic.image;

import com.ownpic.auth.domain.User;
import com.ownpic.auth.domain.UserRepository;
import com.ownpic.image.adapter.TrustMarkPayloadBuilder;
import com.ownpic.image.domain.Image;
import com.ownpic.image.domain.ImageRepository;
import com.ownpic.image.domain.ImageStatus;
import com.ownpic.image.domain.SourceType;
import com.ownpic.image.exception.ImageDuplicateException;
import com.ownpic.image.exception.ImageValidationException;
import com.ownpic.image.port.ImageStoragePort;
import com.ownpic.image.port.WatermarkPort;
import com.ownpic.image.port.WatermarkPort.WatermarkResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * 이미지 보호 파이프라인 오케스트레이터.
 *
 * <p>Split-transaction 패턴으로 DB 커넥션 점유 최소화:
 * <pre>
 * TX 밖: 검증 + SHA256
 * TX#1: 중복 체크 + ID 할당 + 쿼터 체크
 * TX 밖: 워터마킹 (~399ms) + 파일 저장
 * TX#2: DB INSERT + 이벤트 발행
 * </pre>
 */
@Service
class ImageIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ImageIngestionService.class);

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final int MIN_DIMENSION = 100;
    private static final int MAX_DIMENSION = 10_000;
    private static final Set<String> ALLOWED_FORMATS = Set.of("jpg", "jpeg", "png", "avif", "webp");

    private final ImageRepository imageRepository;
    private final UserRepository userRepository;
    private final WatermarkPort watermarkPort;
    private final ImageStoragePort storagePort;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    ImageIngestionService(ImageRepository imageRepository,
                          UserRepository userRepository,
                          WatermarkPort watermarkPort,
                          ImageStoragePort storagePort,
                          ApplicationEventPublisher eventPublisher,
                          TransactionTemplate transactionTemplate) {
        this.imageRepository = imageRepository;
        this.userRepository = userRepository;
        this.watermarkPort = watermarkPort;
        this.storagePort = storagePort;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 보호 파이프라인 실행.
     */
    Image ingest(UUID userId, byte[] imageBytes, String keywords, String originalFilename) {
        long start = System.currentTimeMillis();

        // ── TX 밖: 검증 + SHA256 ──
        validateImage(imageBytes);
        String sha256 = computeSha256(imageBytes);

        // ── TX#1: 쿼터 + 중복 체크 + ID 할당 ──
        record AllocResult(Long imageId, User user) {}
        AllocResult alloc = transactionTemplate.execute(status -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

            if (user.getImagesUsed() >= user.getImageQuota()) {
                throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "이미지 등록 한도를 초과했습니다.");
            }

            imageRepository.findByUserIdAndSha256(userId, sha256)
                    .ifPresent(existing -> { throw new ImageDuplicateException(existing.getId()); });

            Long imageId = imageRepository.allocateId();
            return new AllocResult(imageId, user);
        });

        Long imageId = alloc.imageId();
        User user = alloc.user();

        // ── TX 밖: 워터마킹 + 파일 저장 ──
        String payload = TrustMarkPayloadBuilder.build(
                user.getInternalId() != null ? user.getInternalId() : 0L, imageId);
        WatermarkResult wmResult = watermarkPort.encode(imageBytes, payload);
        String gcsPath = storagePort.save(userId, wmResult.watermarkedImage());

        // ── TX#2: DB INSERT + 이벤트 ──
        Image image;
        try {
            image = transactionTemplate.execute(status -> {
                Image img = new Image();
                img.setUser(user);
                img.setName(originalFilename != null ? originalFilename : "image");
                img.setSha256(sha256);
                img.setFileSize(wmResult.watermarkedImage().length);
                img.setWidth(wmResult.width());
                img.setHeight(wmResult.height());
                img.setStatus(ImageStatus.PROTECTED);
                img.setGcsPath(gcsPath);
                img.setWatermarkPayload(wmResult.payload());
                img.setWatermarkWidth(wmResult.width());
                img.setWatermarkHeight(wmResult.height());
                img.setKeywords(keywords);
                img.setSourceType(SourceType.UPLOAD);
                imageRepository.save(img);

                // 사용량 증가
                user.setImagesUsed(user.getImagesUsed() + 1);
                userRepository.save(user);

                eventPublisher.publishEvent(new ImageProtectedEvent(img.getId(), userId, gcsPath));
                return img;
            });
        } catch (Exception e) {
            storagePort.deleteQuietly(gcsPath);
            throw e;
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("[Ingest] userId={}, imageId={}, sha256={}..., {}ms",
                userId, imageId, sha256.substring(0, 8), elapsed);

        return image;
    }

    // ==================================================================
    // 검증
    // ==================================================================

    private void validateImage(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new ImageValidationException("이미지가 비어 있습니다.");
        }
        if (imageBytes.length > MAX_FILE_SIZE) {
            throw new ImageValidationException(
                    "이미지 크기가 10MB를 초과합니다. (" + imageBytes.length / (1024 * 1024) + "MB)");
        }

        try (var iis = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
            if (iis == null) {
                throw new ImageValidationException("이미지를 읽을 수 없습니다.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new ImageValidationException("지원하지 않는 이미지 포맷입니다. (JPG, PNG, WebP, AVIF 지원)");
            }
            ImageReader reader = readers.next();
            String formatName = reader.getFormatName().toLowerCase();
            if (!ALLOWED_FORMATS.contains(formatName)) {
                throw new ImageValidationException(
                        "지원하지 않는 이미지 포맷입니다: " + formatName + " (JPG, PNG, WebP, AVIF 지원)");
            }

            reader.setInput(iis);
            int w = reader.getWidth(0);
            int h = reader.getHeight(0);
            reader.dispose();

            if (w < MIN_DIMENSION || h < MIN_DIMENSION) {
                throw new ImageValidationException(
                        "이미지 해상도가 너무 낮습니다. (최소 " + MIN_DIMENSION + "x" + MIN_DIMENSION
                                + ", 현재 " + w + "x" + h + ")");
            }
            if (w > MAX_DIMENSION || h > MAX_DIMENSION) {
                throw new ImageValidationException(
                        "이미지 해상도가 너무 높습니다. (최대 " + MAX_DIMENSION + "x" + MAX_DIMENSION
                                + ", 현재 " + w + "x" + h + ")");
            }
        } catch (ImageValidationException e) {
            throw e;
        } catch (IOException e) {
            throw new ImageValidationException("이미지 디코딩에 실패했습니다: " + e.getMessage());
        }
    }

    private static String computeSha256(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
