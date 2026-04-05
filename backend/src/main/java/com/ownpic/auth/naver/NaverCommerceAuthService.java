package com.ownpic.auth.naver;

import com.ownpic.auth.AuthService;
import com.ownpic.auth.domain.AuthProvider;
import com.ownpic.auth.domain.User;
import com.ownpic.auth.domain.UserRepository;
import com.ownpic.auth.dto.AuthResponse;
import com.ownpic.auth.exception.InvalidTokenException;
import com.ownpic.image.domain.*;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NaverCommerceAuthService {

    private static final Logger log = LoggerFactory.getLogger(NaverCommerceAuthService.class);

    private final NaverCommerceTokenParser tokenParser;
    private final UserRepository userRepository;
    private final PlatformConnectionRepository connectionRepository;
    private final AuthService authService;

    public NaverCommerceAuthService(NaverCommerceTokenParser tokenParser,
                                    UserRepository userRepository,
                                    PlatformConnectionRepository connectionRepository,
                                    AuthService authService) {
        this.tokenParser = tokenParser;
        this.userRepository = userRepository;
        this.connectionRepository = connectionRepository;
        this.authService = authService;
    }

    @Transactional
    public AuthResponse authenticate(String naverToken) {
        NaverSellerInfo sellerInfo;
        try {
            sellerInfo = tokenParser.parse(naverToken);
        } catch (JwtException e) {
            log.warn("Naver Commerce JWT validation failed: {}", e.getMessage());
            throw new InvalidTokenException("유효하지 않은 네이버 커머스 토큰입니다.");
        }

        var user = findOrCreateUser(sellerInfo);
        upsertPlatformConnection(user, sellerInfo);

        return authService.createAuthResponse(user);
    }

    private User findOrCreateUser(NaverSellerInfo sellerInfo) {
        return userRepository.findByNaverAccountUid(sellerInfo.accountUid())
                .orElseGet(() -> {
                    var user = new User();
                    user.setName(sellerInfo.channelName());
                    user.setAuthProvider(AuthProvider.NAVER_COMMERCE);
                    user.setNaverAccountUid(sellerInfo.accountUid());
                    userRepository.save(user);
                    log.info("Created new user from Naver Commerce: channelName={}, accountUid={}",
                            sellerInfo.channelName(), sellerInfo.accountUid());
                    return user;
                });
    }

    private void upsertPlatformConnection(User user, NaverSellerInfo sellerInfo) {
        var connection = connectionRepository.findByUserIdAndPlatform(user.getId(), PlatformType.NAVER)
                .orElseGet(() -> {
                    var c = new PlatformConnection();
                    c.setUser(user);
                    c.setPlatform(PlatformType.NAVER);
                    return c;
                });

        connection.setStoreName(sellerInfo.channelName());
        connection.setStoreUrl(sellerInfo.url());
        connection.setChannelNo(sellerInfo.defaultChannelNo());
        connection.setSolutionId(sellerInfo.solutionId());
        connection.setSubscriptionId(sellerInfo.subscriptionId());
        connection.setPlanId(sellerInfo.planId());
        connection.setSubscriptionStatus(sellerInfo.status());
        connection.setBusinessRegistrationNumber(sellerInfo.businessRegistrationNumber());
        connection.setStatus(ConnectionStatus.CONNECTED);

        connectionRepository.save(connection);
    }
}
