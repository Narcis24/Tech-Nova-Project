package com.neueda.auth.util;

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
        assertThrows(IllegalStateException.class, () -> withSecret("").validateSecret());
        assertThrows(IllegalStateException.class, () -> withSecret(null).validateSecret());
    }

    @Test
    void rejectsSecretShorterThan32Bytes() {
        assertThrows(IllegalStateException.class, () -> withSecret("too-short-secret").validateSecret());
    }

    @Test
    void acceptsLongEnoughSecret() {
        JwtUtil jwtUtil = withSecret("0123456789abcdef0123456789abcdef");
        assertDoesNotThrow(jwtUtil::validateSecret);
        assertFalse(jwtUtil.isTokenValid("not-a-token"));
    }
}
