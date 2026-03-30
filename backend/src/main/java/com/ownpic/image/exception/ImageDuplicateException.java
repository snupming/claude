package com.ownpic.image.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ImageDuplicateException extends ResponseStatusException {
    private final Long existingImageId;

    public ImageDuplicateException(Long existingImageId) {
        super(HttpStatus.CONFLICT, "이미 등록된 이미지입니다. (id=" + existingImageId + ")");
        this.existingImageId = existingImageId;
    }

    public Long getExistingImageId() {
        return existingImageId;
    }
}
