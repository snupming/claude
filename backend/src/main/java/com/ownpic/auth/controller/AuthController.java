package com.ownpic.auth.controller;

import com.ownpic.auth.AuthService;
import com.ownpic.auth.dto.AuthResponse;
import com.ownpic.auth.dto.LoginRequest;
import com.ownpic.auth.dto.NaverCommerceAuthRequest;
import com.ownpic.auth.dto.RefreshRequest;
import com.ownpic.auth.dto.SignupRequest;
import com.ownpic.auth.dto.SignupResponse;
import com.ownpic.auth.naver.NaverCommerceAuthService;
import com.ownpic.shared.dto.ApiPaths;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.V1 + "/auth")
public class AuthController {

    private final AuthService authService;
    private final NaverCommerceAuthService naverCommerceAuthService;

    public AuthController(AuthService authService, NaverCommerceAuthService naverCommerceAuthService) {
        this.authService = authService;
        this.naverCommerceAuthService = naverCommerceAuthService;
    }

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        var response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/naver-commerce")
    public ResponseEntity<AuthResponse> naverCommerce(@Valid @RequestBody NaverCommerceAuthRequest request) {
        return ResponseEntity.ok(naverCommerceAuthService.authenticate(request.token()));
    }
}
