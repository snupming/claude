package com.ownpic.auth;

import com.ownpic.auth.domain.RefreshToken;
import com.ownpic.auth.domain.RefreshTokenRepository;
import com.ownpic.auth.domain.User;
import com.ownpic.auth.domain.UserRepository;
import com.ownpic.auth.dto.AuthResponse;
import com.ownpic.auth.dto.LoginRequest;
import com.ownpic.auth.dto.SignupRequest;
import com.ownpic.auth.dto.SignupResponse;
import com.ownpic.auth.exception.AuthenticationFailedException;
import com.ownpic.auth.exception.DuplicateEmailException;
import com.ownpic.auth.exception.InvalidTokenException;
import com.ownpic.auth.jwt.JwtProperties;
import com.ownpic.auth.jwt.JwtProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider,
                       JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException();
        }

        var user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        userRepository.save(user);

        return new SignupResponse(
                user.getId().toString(),
                user.getName(),
                user.getEmail(),
                user.getRole().name()
        );
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        var user = userRepository.findByEmail(request.email())
                .orElseThrow(AuthenticationFailedException::new);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new AuthenticationFailedException();
        }

        return createAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        var tokenHash = hashToken(refreshTokenValue);
        var refreshToken = refreshTokenRepository.findByTokenAndRevokedFalse(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("유효하지 않은 리프레시 토큰입니다."));

        if (refreshToken.isExpired()) {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
            throw new InvalidTokenException("만료된 리프레시 토큰입니다.");
        }

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        var user = refreshToken.getUser();
        return createAuthResponse(user);
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        var tokenHash = hashToken(refreshTokenValue);
        refreshTokenRepository.findByTokenAndRevokedFalse(tokenHash)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    private AuthResponse createAuthResponse(User user) {
        var accessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        var refreshTokenValue = jwtProvider.generateRefreshTokenValue();

        var refreshToken = new RefreshToken();
        refreshToken.setToken(hashToken(refreshTokenValue));
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(Instant.now().plusMillis(jwtProperties.refreshTokenExpiration()));
        refreshTokenRepository.save(refreshToken);

        var userInfo = new AuthResponse.UserInfo(
                user.getId().toString(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.getImageQuota(),
                user.getImagesUsed()
        );

        return new AuthResponse(accessToken, refreshTokenValue, userInfo);
    }

    private String hashToken(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
