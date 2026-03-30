package com.ownpic.image.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ImageValidationException extends ResponseStatusException {
    public ImageValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
