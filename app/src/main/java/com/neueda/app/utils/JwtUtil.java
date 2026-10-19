package com.neueda.app.utils;

import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private SecretKey signingKey;

    /** Fails startup on a missing or weak secret instead of on the first request. HS256 needs 32+ bytes. */
    @PostConstruct
    void validateSecret() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("jwt.secret is not set; set JWT_SECRET");
        }
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("jwt.secret must be at least 32 bytes");
        }
        signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    private SecretKey getSigningKey() {
        return signingKey;
    }

    public String extractUsername(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The roles claim of a verified token, e.g. ["ADMIN", "TRADER"]. Empty if the token is invalid or the
     * claim is missing or not a list; entries that are not strings are dropped.
     */
    public List<String> extractRoles(String token) {
        try {
            Object roles = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .get("roles");
            if (!(roles instanceof List<?> list)) {
                return List.of();
            }
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    public boolean isTokenValid(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
