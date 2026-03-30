package com.ownpic.image;

import com.ownpic.image.adapter.TrustMarkPayloadBuilder;
import com.ownpic.image.domain.Image;
import com.ownpic.image.domain.ImageRepository;
import com.ownpic.image.exception.ImageDuplicateException;
import com.ownpic.image.exception.ImageValidationException;
import com.ownpic.image.port.ImageStoragePort;
import com.ownpic.image.port.WatermarkPort;
import com.ownpic.image.port.WatermarkResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Set;

/**
 * 이미지 보호 파이프라인 오케스트레이터.
 *
 * <p>입력 경로(대시보드 업로드 / Phase C 플랫폼 API)에 관계없이
 * {@code byte[] + 메타데이터}로 합류하는 공통 파이프라인입니다.
 *
 * <h3>파이프라인 구조 (DB 커넥션 점유 최소화)</h3>
 * <pre>
 * ── 트랜잭션 밖 ──
 * (1) 이미지 검증 (포맷, 크기, 해상도)
 * (2) SHA256 계산
 * ── 짧은 트랜잭션 #1 (중복 체크 + ID 할당) ──
 * (3) SHA256 중복 체크 → 409 Conflict
 * (4) allocateId → TrustMark 페이로드 구성
 * ── 트랜잭션 밖 (중량 I/O) ──
 * (5) TrustMark 워터마킹 (~399ms)
 * (6) GCS/로컬 저장 (~200ms)
 * ── 트랜잭션 #2 (DB INSERT + 이벤트) ──
 * (7) DB INSERT (status=PROTECTED)
 * (8) ImageProtectedEvent 발행
 * </pre>
 *
 * <p>워터마킹/GCS 업로드가 트랜잭션 밖에서 실행되므로,
 * DB 커넥션 점유 시간이 ~600ms → ~10ms로 단축됩니다.
 * HikariCP pool-size 10에서 동시 업로드 10건이 커넥션 풀을 고갈시키지 않습니다.
 *
 * @see ImageProtectedEvent
 */
@Service
class ImageIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ImageIngestionService.class);

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int MIN_DIMENSION = 100;
    private static final int MAX_DIMENSION = 10_000;
    private static final Set<String> ALLOWED_FORMATS = Set.of("jpg", "jpeg", "png", "avif", "webp");

    private final ImageRepository imageRepository;
    private final WatermarkPort watermarkPort;
    private final ImageStoragePort storagePort;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final com.ownpic.billing.BillingQuotaGuard billingQuotaGuard;

    ImageIngestionService(ImageRepository imageRepository,
                          WatermarkPort watermarkPort,
                          ImageStoragePort storagePort,
                          ApplicationEventPublisher eventPublisher,
                          TransactionTemplate transactionTemplate,
                          com.ownpic.billing.BillingQuotaGuard billingQuotaGuard) {
        this.imageRepository = imageRepository;
        this.watermarkPort = watermarkPort;
        this.storagePort = storagePort;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
        this.billingQuotaGuard = billingQuotaGuard;
    }

    /**
     * 보호 파이프라인 실행.
     *
     * <p>{@code @Transactional} 미사용 — {@link TransactionTemplate}으로 DB 작업만 감쌈.
     *
     * @param userId     셀러 내부 PK (auth 모듈에서 변환 완료)
     * @param imageBytes 원본 이미지 바이트
     * @param keywords   검색용 키워드 (상품명)
     * @return 보호 완료된 Image 엔티티 (status=PROTECTED)
     * @throws ImageValidationException 이미지 검증 실패 (400)
     * @throws ImageDuplicateException  동일 이미지 중복 등록 (409)
     */
    Image ingest(Long userId, byte[] imageBytes, String keywords) {
        long start = System.currentTimeMillis();

        // ── 쿼터 확인 ──
        billingQuotaGuard.checkImageUploadQuota(userId);

        // ── 트랜잭션 밖: 검증 + SHA256 ──
        // (1) 이미지 검증 (포맷, 크기, 해상도)
        validateImage(imageBytes);

        // (2) SHA256 계산
        String sha256 = computeSha256(imageBytes);

        // ── 짧은 트랜잭션 #1: 중복 체크 + ID 할당 ──
        Long imageId = transactionTemplate.execute(status -> {
            checkDuplicate(userId, sha256);
            return imageRepository.allocateId();
        });

        // ── 트랜잭션 밖: 중량 I/O (~600ms) ──
        // (3) TrustMark 페이로드 구성 + 워터마킹 (~399ms)
        String payload = TrustMarkPayloadBuilder.build(userId, imageId);
        WatermarkResult wmResult = watermarkPort.encode(imageBytes, payload);

        // (4) 저장소 업로드 (~200ms)
        String gcsPath = storagePort.save(userId, wmResult.watermarkedImage());

        // ── 트랜잭션 #2: DB INSERT + 이벤트 발행 ──
        // 실패 시 GCS 고아 파일 best-effort 삭제
        Image image;
        try {
            image = transactionTemplate.execute(status -> {
                Image img = Image.createProtected(
                        imageId, userId, sha256, gcsPath,
                        wmResult.watermarkedImage().length,
                        wmResult.width(), wmResult.height(),
                        payload, keywords);
                imageRepository.save(img);

                // MVP: 이벤트 유실 시 수동 재인덱싱으로 대응
                eventPublisher.publishEvent(new ImageProtectedEvent(
                        img.getId(), userId, gcsPath));
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

    /**
     * 이미지 검증: 포맷 (매직 바이트 기반), 크기, 해상도.
     *
     * <p>ImageIO.getImageReaders()로 실제 포맷명을 확인하여
     * GIF, WBMP 등 허용하지 않는 포맷을 차단합니다.
     */
    private void validateImage(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new ImageValidationException("이미지가 비어 있습니다.");
        }
        if (imageBytes.length > MAX_FILE_SIZE) {
            throw new ImageValidationException(
                    "이미지 크기가 10MB를 초과합니다. (" + imageBytes.length / (1024 * 1024) + "MB)");
        }

        // 포맷 검증: ImageIO의 ImageReader로 실제 포맷명 확인
        try (var iis = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
            if (iis == null) {
                log.warn("[Validate] ImageInputStream is null, bytes.length={}", imageBytes.length);
                throw new ImageValidationException("이미지를 읽을 수 없습니다.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                // 디버그: 파일 매직 바이트 출력
                String hex = imageBytes.length >= 8
                        ? String.format("%02X %02X %02X %02X %02X %02X %02X %02X",
                            imageBytes[0] & 0xFF, imageBytes[1] & 0xFF, imageBytes[2] & 0xFF, imageBytes[3] & 0xFF,
                            imageBytes[4] & 0xFF, imageBytes[5] & 0xFF, imageBytes[6] & 0xFF, imageBytes[7] & 0xFF)
                        : "too short";
                log.warn("[Validate] No ImageReader found. bytes.length={}, magic={}", imageBytes.length, hex);
                throw new ImageValidationException(
                        "지원하지 않는 이미지 포맷입니다. (JPG, PNG, WebP, AVIF 지원)");
            }
            ImageReader reader = readers.next();
            String formatName = reader.getFormatName().toLowerCase();
            if (!ALLOWED_FORMATS.contains(formatName)) {
                throw new ImageValidationException(
                        "지원하지 않는 이미지 포맷입니다: " + formatName + " (JPG, PNG, WebP, AVIF 지원)");
            }

            // 해상도 검증: reader에서 직접 읽기 (전체 디코딩 불필요)
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

    private void checkDuplicate(Long userId, String sha256) {
        imageRepository.findByUserIdAndSha256(userId, sha256)
                .ifPresent(existing -> {
                    throw new ImageDuplicateException(existing.getId());
                });
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
