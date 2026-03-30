package com.ownpic.image;

import com.ownpic.image.domain.ImageRepository;
import com.ownpic.image.domain.ImageStatus;
import com.ownpic.image.port.EmbeddingPort;
import com.ownpic.image.port.ImageStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

@Service
public class ImageIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ImageIngestionService.class);

    private final ImageRepository imageRepository;
    private final ImageStoragePort storagePort;
    private final EmbeddingPort embeddingPort;

    public ImageIngestionService(ImageRepository imageRepository,
                                  ImageStoragePort storagePort,
                                  EmbeddingPort embeddingPort) {
        this.imageRepository = imageRepository;
        this.storagePort = storagePort;
        this.embeddingPort = embeddingPort;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional
    public void onImageProtected(ImageProtectedEvent event) {
        try {
            var image = imageRepository.findById(event.imageId()).orElse(null);
            if (image == null || image.getGcsPath() == null) return;

            byte[] imageBytes = storagePort.load(image.getGcsPath());
            byte[] embedding = embeddingPort.embed(imageBytes);

            if (embedding != null) {
                image.setEmbedding(embedding);
                image.setStatus(ImageStatus.INDEXED);
                image.setIndexedAt(Instant.now());
                imageRepository.save(image);
                log.info("Image {} indexed successfully", event.imageId());
            } else {
                log.info("Image {} skipped embedding (NoOp adapter)", event.imageId());
            }
        } catch (Exception e) {
            log.error("Failed to index image {}", event.imageId(), e);
            imageRepository.findById(event.imageId()).ifPresent(img -> {
                img.setStatus(ImageStatus.FAILED);
                imageRepository.save(img);
            });
        }
    }
}
