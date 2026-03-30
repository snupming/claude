package com.ownpic.auth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class AuthenticationFailedException extends ResponseStatusException {

    public AuthenticationFailedException() {
        super(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
    }
}
