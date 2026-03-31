package com.ownpic.image.controller;

import com.ownpic.auth.jwt.JwtAuthenticationFilter;
import com.ownpic.auth.jwt.JwtProvider;
import com.ownpic.shared.config.CorsProperties;
import com.ownpic.image.ImageService;
import com.ownpic.image.dto.ImageListResponse;
import com.ownpic.image.dto.ImageResponse;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ImageController.class)
@Import(JwtAuthenticationFilter.class)
@TestPropertySource(properties = {
        "ownpic.cors.allowed-origins=http://localhost:3000",
        "ownpic.jwt.secret=test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm",
        "ownpic.jwt.access-token-expiration=900000",
        "ownpic.jwt.refresh-token-expiration=604800000"
})
class ImageControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ImageService imageService;
    @MockitoBean JwtProvider jwtProvider;
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
    void getImages_authenticated_returns200() throws Exception {
        String token = validToken();
        var listResp = new ImageListResponse(List.of(), 0, 20, 0, 0);
        when(imageService.getImages(eq(userId), eq(0), eq(20), isNull(), isNull())).thenReturn(listResp);

        mockMvc.perform(get("/api/v1/images")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void getImages_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/images"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void upload_validFile_returns201() throws Exception {
        String token = validToken();
        var file = new MockMultipartFile("file", "test.png", "image/png", new byte[]{1, 2, 3});
        var response = new ImageResponse(1L, "test.png", "path", "PROTECTED", 3, 300, 300,
                "UPLOAD", null, null, null, Instant.now(), null);
        when(imageService.upload(eq(userId), any(), isNull())).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/images/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("test.png"));
    }

    @Test
    void getImage_authenticated_returns200() throws Exception {
        String token = validToken();
        var response = new ImageResponse(1L, "test.png", "path", "INDEXED", 1024, 300, 300,
                "UPLOAD", null, null, null, Instant.now(), Instant.now());
        when(imageService.getImage(userId, 1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/images/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void deleteImage_authenticated_returns204() throws Exception {
        String token = validToken();

        mockMvc.perform(delete("/api/v1/images/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        verify(imageService).deleteImage(userId, 1L);
    }

    @Test
    void serveFile_ownFile_returns200() throws Exception {
        String token = validToken();
        when(imageService.loadImageBytes(userId + "/test.png")).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/api/v1/images/file/" + userId + "/test.png")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
    }

    @Test
    void serveFile_otherUserFile_returns403() throws Exception {
        String token = validToken();
        UUID otherUserId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/images/file/" + otherUserId + "/test.png")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
