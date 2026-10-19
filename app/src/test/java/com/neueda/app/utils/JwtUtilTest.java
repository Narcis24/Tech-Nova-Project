package com.neueda.app.utils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil withSecret(String secret) {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", secret);
        return jwtUtil;
    }

    @Test
    void rejectsMissingSecret() {
        JwtUtil emptySecret = withSecret("");
        JwtUtil nullSecret = withSecret(null);
        assertThrows(IllegalStateException.class, emptySecret::validateSecret);
        assertThrows(IllegalStateException.class, nullSecret::validateSecret);
    }

    @Test
    void rejectsSecretShorterThan32Bytes() {
        JwtUtil shortSecret = withSecret("too-short-secret");
        assertThrows(IllegalStateException.class, shortSecret::validateSecret);
    }

    @Test
    void acceptsLongEnoughSecret() {
        JwtUtil jwtUtil = withSecret("0123456789abcdef0123456789abcdef");
        assertDoesNotThrow(jwtUtil::validateSecret);
        assertFalse(jwtUtil.isTokenValid("not-a-token"));
    }

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private JwtUtil validJwtUtil() {
        JwtUtil jwtUtil = withSecret(SECRET);
        jwtUtil.validateSecret();
        return jwtUtil;
    }

    private String tokenWith(Map<String, ?> claims, String secret) {
        return Jwts.builder()
                .subject("john_doe1")
                .claims(claims)
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void extractsEveryRole() {
        String token = tokenWith(Map.of("roles", List.of("ADMIN", "TRADER")), SECRET);
        assertEquals(List.of("ADMIN", "TRADER"), validJwtUtil().extractRoles(token));
    }

    @Test
    void extractsNoRolesFromAForgedToken() {
        String forged = tokenWith(Map.of("roles", List.of("ADMIN")), "another-secret-another-secret-123");
        assertEquals(List.of(), validJwtUtil().extractRoles(forged));
    }

    @Test
    void extractsNoRolesWhenTheClaimIsMissingOrNotAList() {
        JwtUtil jwtUtil = validJwtUtil();
        assertEquals(List.of(), jwtUtil.extractRoles(tokenWith(Map.of(), SECRET)));
        assertEquals(List.of(), jwtUtil.extractRoles(tokenWith(Map.of("roles", "ADMIN"), SECRET)));
    }

    @Test
    void dropsRolesThatAreNotStrings() {
        String token = tokenWith(Map.of("roles", List.of(1, "TRADER")), SECRET);
        assertEquals(List.of("TRADER"), validJwtUtil().extractRoles(token));
    }
}
