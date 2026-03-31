package com.ownpic.image.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ownpic.auth.jwt.JwtAuthenticationFilter;
import com.ownpic.auth.jwt.JwtProperties;
import com.ownpic.auth.jwt.JwtProvider;
import com.ownpic.shared.config.CorsProperties;
import com.ownpic.image.StoreService;
import com.ownpic.image.dto.PlatformConnectionResponse;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StoreController.class)
@Import(JwtAuthenticationFilter.class)
@TestPropertySource(properties = {
        "ownpic.cors.allowed-origins=http://localhost:3000",
        "ownpic.jwt.secret=test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm",
        "ownpic.jwt.access-token-expiration=900000",
        "ownpic.jwt.refresh-token-expiration=604800000"
})
class StoreControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean StoreService storeService;
    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean JwtProperties jwtProperties;
    @MockitoBean CorsProperties corsProperties;

    private final UUID userId = UUID.randomUUID();

    private String validToken() {
        String token = "test-jwt-token";
        when(jwtProvider.validateAccessToken(token)).thenReturn(true);
        when(jwtProvider.getUserIdFromToken(token)).thenReturn(userId);
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("role", String.class)).thenReturn("FREE");
        when(jwtProvider.parseAccessToken(token)).thenReturn(claims);
        return token;
    }

    @Test
    void connect_validPlatform_returns201() throws Exception {
        String token = validToken();
        var response = new PlatformConnectionResponse(1L, "NAVER", null, "CONNECTED", Instant.now(), Instant.now());
        when(storeService.connect(userId, "NAVER")).thenReturn(response);

        mockMvc.perform(post("/api/v1/stores/connect")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"NAVER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.platform").value("NAVER"));
    }

    @Test
    void getConnections_returns200() throws Exception {
        String token = validToken();
        when(storeService.getConnections(userId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/stores")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void disconnect_returns204() throws Exception {
        String token = validToken();

        mockMvc.perform(delete("/api/v1/stores/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        verify(storeService).disconnect(userId, 1L);
    }

    @Test
    void syncProducts_returns200() throws Exception {
        String token = validToken();

        mockMvc.perform(post("/api/v1/stores/1/sync")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        verify(storeService).syncProducts(userId, 1L);
    }

    @Test
    void connect_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/stores/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"NAVER\"}"))
                .andExpect(status().isUnauthorized());
    }
}
