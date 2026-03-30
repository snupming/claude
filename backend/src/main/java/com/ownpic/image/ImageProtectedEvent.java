package com.ownpic.image;

import java.util.UUID;

public record ImageProtectedEvent(Long imageId, UUID userId, String gcsPath) {}
