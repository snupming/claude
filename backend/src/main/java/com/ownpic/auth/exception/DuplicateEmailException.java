package com.ownpic.auth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class DuplicateEmailException extends ResponseStatusException {

    public DuplicateEmailException() {
        super(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.");
    }
}
