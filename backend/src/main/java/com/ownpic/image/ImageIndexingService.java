package com.ownpic.image;

import com.ownpic.detection.port.DinoEmbeddingPort;
import com.ownpic.detection.port.SscdEmbeddingPort;
import com.ownpic.image.domain.ImageRepository;
import com.ownpic.image.domain.ImageStatus;
import com.ownpic.image.port.ImageStoragePort;
import com.ownpic.shared.ml.PgvectorUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.ByteBuffer;
import java.time.Instant;

/**
 * 비동기 듀얼 임베딩 서비스 (SSCD + DINOv2).
 */
@Service
public class ImageIndexingService {

    private static final Logger log = LoggerFactory.getLogger(ImageIndexingService.class);

    private final ImageRepository imageRepository;
    private final ImageStoragePort storagePort;
    private final SscdEmbeddingPort sscdEmbeddingPort;
    private final DinoEmbeddingPort dinoEmbeddingPort;

    @PersistenceContext
    private EntityManager entityManager;

    public ImageIndexingService(ImageRepository imageRepository,
                                 ImageStoragePort storagePort,
                                 SscdEmbeddingPort sscdEmbeddingPort,
                                 DinoEmbeddingPort dinoEmbeddingPort) {
        this.imageRepository = imageRepository;
        this.storagePort = storagePort;
        this.sscdEmbeddingPort = sscdEmbeddingPort;
        this.dinoEmbeddingPort = dinoEmbeddingPort;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onImageProtected(ImageProtectedEvent event) {
        try {
            var image = imageRepository.findById(event.imageId()).orElse(null);
            if (image == null || image.getGcsPath() == null) return;

            byte[] imageBytes = storagePort.load(image.getGcsPath());

            // SSCD embedding (512-dim)
            float[] sscdEmbedding = sscdEmbeddingPort.generateEmbedding(imageBytes);
            if (sscdEmbedding != null) {
                image.setEmbedding(floatsToBytes(sscdEmbedding));
            }

            // DINOv2 embedding (384-dim)
            float[] dinoEmbedding = dinoEmbeddingPort.generateEmbedding(imageBytes);
            if (dinoEmbedding != null) {
                image.setEmbeddingDino(floatsToBytes(dinoEmbedding));
            }

            if (sscdEmbedding != null || dinoEmbedding != null) {
                image.setStatus(ImageStatus.INDEXED);
                image.setIndexedAt(Instant.now());
                imageRepository.save(image);

                // vector 컬럼에도 저장 (pgvector 검색용)
                saveVectorColumns(event.imageId(), sscdEmbedding, dinoEmbedding);

                log.info("Image {} indexed (SSCD={}, DINOv2={})",
                        event.imageId(),
                        sscdEmbedding != null ? sscdEmbedding.length + "d" : "skip",
                        dinoEmbedding != null ? dinoEmbedding.length + "d" : "skip");
            } else {
                log.info("Image {} skipped embedding (NoOp adapters)", event.imageId());
            }
        } catch (Exception e) {
            log.error("Failed to index image {}", event.imageId(), e);
            imageRepository.findById(event.imageId()).ifPresent(img -> {
                img.setStatus(ImageStatus.FAILED);
                imageRepository.save(img);
            });
        }
    }

    private void saveVectorColumns(Long imageId, float[] sscd, float[] dino) {
        try {
            if (sscd != null) {
                entityManager.createNativeQuery(
                        "UPDATE images SET embedding_sscd = cast(:vec AS vector) WHERE id = :id")
                        .setParameter("vec", PgvectorUtils.toVectorString(sscd))
                        .setParameter("id", imageId)
                        .executeUpdate();
            }
            if (dino != null) {
                entityManager.createNativeQuery(
                        "UPDATE images SET embedding_dino_vec = cast(:vec AS vector) WHERE id = :id")
                        .setParameter("vec", PgvectorUtils.toVectorString(dino))
                        .setParameter("id", imageId)
                        .executeUpdate();
            }
        } catch (Exception e) {
            log.warn("Failed to save vector columns for image {}: {}", imageId, e.getMessage());
        }
    }

    private static byte[] floatsToBytes(float[] floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4);
        for (float f : floats) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }
}
