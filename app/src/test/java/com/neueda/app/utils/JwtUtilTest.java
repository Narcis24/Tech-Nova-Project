package com.neueda.app.utils;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
}
