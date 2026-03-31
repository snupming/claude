package com.ownpic.auth;

import com.ownpic.auth.domain.RefreshToken;
import com.ownpic.auth.domain.RefreshTokenRepository;
import com.ownpic.auth.domain.User;
import com.ownpic.auth.domain.UserRepository;
import com.ownpic.auth.dto.LoginRequest;
import com.ownpic.auth.dto.SignupRequest;
import com.ownpic.auth.exception.AuthenticationFailedException;
import com.ownpic.auth.exception.DuplicateEmailException;
import com.ownpic.auth.exception.InvalidTokenException;
import com.ownpic.auth.jwt.JwtProperties;
import com.ownpic.auth.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtProvider jwtProvider;
    @Mock JwtProperties jwtProperties;

    @InjectMocks AuthService authService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    // --- signup ---

    @Test
    void signup_success_createsUser() {
        var request = new SignupRequest("테스트", "test@example.com", "password123");
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded_password");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            setField(user, "id", userId);
            return user;
        });

        var response = authService.signup(request);

        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.name()).isEqualTo("테스트");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void signup_duplicateEmail_throwsException() {
        var request = new SignupRequest("테스트", "dup@example.com", "password123");
        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).save(any());
    }

    // --- login ---

    @Test
    void login_success_returnsTokens() {
        var request = new LoginRequest("test@example.com", "password123");
        User user = createUser(userId, "test@example.com", "encoded");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded")).thenReturn(true);
        when(jwtProvider.generateAccessToken(any(), any(), any())).thenReturn("access-token");
        when(jwtProvider.generateRefreshTokenValue()).thenReturn("refresh-value");
        when(jwtProperties.refreshTokenExpiration()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-value");
        assertThat(response.user().email()).isEqualTo("test@example.com");
    }

    @Test
    void login_wrongPassword_throwsException() {
        var request = new LoginRequest("test@example.com", "wrong");
        User user = createUser(userId, "test@example.com", "encoded");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void login_emailNotFound_throwsException() {
        var request = new LoginRequest("nonexist@example.com", "password");
        when(userRepository.findByEmail("nonexist@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    // --- refresh ---

    @Test
    void refresh_validToken_rotatesAndReturns() {
        User user = createUser(userId, "test@example.com", "encoded");
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("hashed-token");
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.findByTokenAndRevokedFalse(anyString())).thenReturn(Optional.of(refreshToken));
        when(jwtProvider.generateAccessToken(any(), any(), any())).thenReturn("new-access");
        when(jwtProvider.generateRefreshTokenValue()).thenReturn("new-refresh");
        when(jwtProperties.refreshTokenExpiration()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = authService.refresh("some-refresh-token");

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(refreshToken.isRevoked()).isTrue();
    }

    @Test
    void refresh_invalidToken_throwsException() {
        when(refreshTokenRepository.findByTokenAndRevokedFalse(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("invalid-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    // --- logout ---

    @Test
    void logout_revokesRefreshToken() {
        RefreshToken token = new RefreshToken();
        token.setToken("hashed");
        when(refreshTokenRepository.findByTokenAndRevokedFalse(anyString())).thenReturn(Optional.of(token));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.logout("some-token");

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    // --- Helpers ---

    private User createUser(UUID id, String email, String password) {
        User user = new User();
        setField(user, "id", id);
        user.setName("테스트");
        user.setEmail(email);
        user.setPassword(password);
        return user;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var clazz = target.getClass();
            while (clazz != null) {
                try {
                    var field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    field.set(target, value);
                    return;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            throw new NoSuchFieldException(fieldName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}
