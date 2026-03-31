package com.ownpic.auth.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtProviderTest {

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        // Use the same test secret from application-test.yml (must be >= 256 bits)
        JwtProperties props = new JwtProperties(
                "test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm",
                900000L,  // 15 min
                604800000L // 7 days
        );
        jwtProvider = new JwtProvider(props);
    }

    @Test
    void generateAndParse_roundTrip() {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        String role = "FREE";

        String token = jwtProvider.generateAccessToken(userId, email, role);
        assertThat(token).isNotBlank();

        UUID parsed = jwtProvider.getUserIdFromToken(token);
        assertThat(parsed).isEqualTo(userId);

        var claims = jwtProvider.parseAccessToken(token);
        assertThat(claims.get("email", String.class)).isEqualTo(email);
        assertThat(claims.get("role", String.class)).isEqualTo(role);
    }

    @Test
    void validateToken_valid_returnsTrue() {
        UUID userId = UUID.randomUUID();
        String token = jwtProvider.generateAccessToken(userId, "a@b.com", "FREE");

        assertThat(jwtProvider.validateAccessToken(token)).isTrue();
    }

    @Test
    void validateToken_tampered_returnsFalse() {
        UUID userId = UUID.randomUUID();
        String token = jwtProvider.generateAccessToken(userId, "a@b.com", "FREE");

        // Tamper the payload by flipping a character in the middle of the second segment
        String[] parts = token.split("\\.");
        char[] payloadChars = parts[1].toCharArray();
        int mid = payloadChars.length / 2;
        payloadChars[mid] = (payloadChars[mid] == 'X') ? 'Y' : 'X';
        String tampered = parts[0] + "." + new String(payloadChars) + "." + parts[2];
        assertThat(jwtProvider.validateAccessToken(tampered)).isFalse();
    }

    @Test
    void validateToken_expired_returnsFalse() {
        // Create a provider with 0ms expiration
        JwtProperties expiredProps = new JwtProperties(
                "test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm",
                0L,
                604800000L
        );
        JwtProvider expiredProvider = new JwtProvider(expiredProps);

        String token = expiredProvider.generateAccessToken(UUID.randomUUID(), "a@b.com", "FREE");
        assertThat(expiredProvider.validateAccessToken(token)).isFalse();
    }

    @Test
    void validateToken_nullOrEmpty_returnsFalse() {
        assertThat(jwtProvider.validateAccessToken(null)).isFalse();
        assertThat(jwtProvider.validateAccessToken("")).isFalse();
        assertThat(jwtProvider.validateAccessToken("not.a.jwt")).isFalse();
    }

    @Test
    void generateRefreshTokenValue_isUUID() {
        String value = jwtProvider.generateRefreshTokenValue();
        assertThat(value).isNotBlank();
        assertThatCode(() -> UUID.fromString(value)).doesNotThrowAnyException();
    }
}
