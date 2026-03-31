package com.ownpic.detection.controller;

import com.ownpic.auth.jwt.JwtProvider;
import com.ownpic.detection.DetectionService;
import com.ownpic.detection.dto.DetectionScanDetailResponse;
import com.ownpic.detection.dto.DetectionScanResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DetectionController.class)
class DetectionControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean DetectionService detectionService;
    @MockitoBean JwtProvider jwtProvider;

    private final UUID userId = UUID.randomUUID();

    private String validToken() {
        String token = "test-jwt-token";
        when(jwtProvider.validateAccessToken(token)).thenReturn(true);
        when(jwtProvider.getUserIdFromToken(token)).thenReturn(userId);
        var claims = io.jsonwebtoken.Jwts.claims().subject(userId.toString()).add("role", "FREE").build();
        when(jwtProvider.parseAccessToken(token)).thenReturn(claims);
        return token;
    }

    @Test
    void postScan_authenticated_returns202() throws Exception {
        String token = validToken();
        var response = new DetectionScanResponse(1L, "SCANNING", 5, 0, 0, 0, Instant.now(), null);
        when(detectionService.startScan(userId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/detections/scan")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SCANNING"))
                .andExpect(jsonPath("$.totalImages").value(5));
    }

    @Test
    void postScan_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/detections/scan"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getScans_authenticated_returns200() throws Exception {
        String token = validToken();
        var scan = new DetectionScanResponse(1L, "COMPLETED", 10, 10, 3, 100, Instant.now(), Instant.now());
        when(detectionService.getScans(eq(userId), any())).thenReturn(
                new PageImpl<>(List.of(scan), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/detections/scans")
                        .header("Authorization", "Bearer " + token)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.content[0].matchesFound").value(3));
    }

    @Test
    void getScanDetail_authenticated_returns200() throws Exception {
        String token = validToken();
        var scanResp = new DetectionScanResponse(1L, "COMPLETED", 5, 5, 1, 100, Instant.now(), Instant.now());
        var detail = new DetectionScanDetailResponse(scanResp, List.of());
        when(detectionService.getScanDetail(userId, 1L)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/detections/scans/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scan.status").value("COMPLETED"))
                .andExpect(jsonPath("$.results").isArray());
    }
}
