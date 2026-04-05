package com.ownpic.auth.naver;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public class NaverCommerceTokenParser {

    private final RSAPublicKey publicKey;

    public NaverCommerceTokenParser(NaverCommerceProperties properties) {
        this.publicKey = parsePublicKey(properties.publicKey());
    }

    public NaverSellerInfo parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer("merc")
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new NaverSellerInfo(
                claims.get("solutionId", String.class),
                claims.get("accountUid", String.class),
                claims.get("roleGroupType", String.class),
                claims.get("channelName", String.class),
                claims.get("defaultChannelNo", Long.class),
                claims.get("type", String.class),
                claims.get("url", String.class),
                claims.get("categoryId", String.class),
                claims.get("representType", String.class),
                claims.get("businessType", String.class),
                claims.get("businessRegistrationNumber", String.class),
                claims.get("actionGrade", String.class),
                claims.get("planId", String.class),
                claims.get("subscriptionId", String.class),
                claims.get("status", String.class)
        );
    }

    private static RSAPublicKey parsePublicKey(String base64Key) {
        try {
            var keyBytes = Base64.getDecoder().decode(base64Key.replaceAll("\\s+", ""));
            var keySpec = new X509EncodedKeySpec(keyBytes);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to parse Naver Commerce RSA public key", e);
        }
    }
}
