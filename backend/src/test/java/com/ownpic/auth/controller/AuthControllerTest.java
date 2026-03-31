package com.ownpic.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ownpic.auth.AuthService;
import com.ownpic.auth.dto.AuthResponse;
import com.ownpic.auth.dto.LoginRequest;
import com.ownpic.auth.dto.RefreshRequest;
import com.ownpic.auth.dto.SignupRequest;
import com.ownpic.auth.dto.SignupResponse;
import com.ownpic.auth.exception.AuthenticationFailedException;
import com.ownpic.auth.exception.DuplicateEmailException;
import com.ownpic.auth.jwt.JwtAuthenticationFilter;
import com.ownpic.auth.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(JwtAuthenticationFilter.class)
@TestPropertySource(properties = {
        "ownpic.cors.allowed-origins=http://localhost:3000",
        "ownpic.jwt.secret=test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm",
        "ownpic.jwt.access-token-expiration=900000",
        "ownpic.jwt.refresh-token-expiration=604800000"
})
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean AuthService authService;
    @MockitoBean JwtProvider jwtProvider;

    @Test
    void signup_validRequest_returns201() throws Exception {
        var request = new SignupRequest("테스트", "test@test.com", "password123");
        var response = new SignupResponse("uuid", "테스트", "test@test.com", "FREE");
        when(authService.signup(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("test@test.com"));
    }

    @Test
    void signup_duplicateEmail_returns409() throws Exception {
        var request = new SignupRequest("테스트", "dup@test.com", "password123");
        when(authService.signup(any())).thenThrow(new DuplicateEmailException());

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void signup_blankEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test\",\"email\":\"\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signup_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test\",\"email\":\"a@b.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_validCredentials_returns200() throws Exception {
        var request = new LoginRequest("test@test.com", "password123");
        var userInfo = new AuthResponse.UserInfo("uuid", "테스트", "test@test.com", "FREE", 50, 0);
        var response = new AuthResponse("access-token", "refresh-token", userInfo);
        when(authService.login(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.user.email").value("test@test.com"));
    }

    @Test
    void login_invalidCredentials_returns401() throws Exception {
        var request = new LoginRequest("test@test.com", "wrong");
        when(authService.login(any())).thenThrow(new AuthenticationFailedException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_validToken_returns200() throws Exception {
        var request = new RefreshRequest("valid-refresh-token");
        var userInfo = new AuthResponse.UserInfo("uuid", "테스트", "test@test.com", "FREE", 50, 0);
        var response = new AuthResponse("new-access", "new-refresh", userInfo);
        when(authService.refresh("valid-refresh-token")).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"));
    }

    @Test
    void logout_returns204() throws Exception {
        var request = new RefreshRequest("some-token");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(authService).logout("some-token");
    }
}
