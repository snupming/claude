package com.ownpic.image.port;

import java.util.UUID;

public interface ImageStoragePort {
    String store(UUID userId, Long imageId, byte[] imageBytes, String extension);
    byte[] load(String path);
    void delete(String path);
}
