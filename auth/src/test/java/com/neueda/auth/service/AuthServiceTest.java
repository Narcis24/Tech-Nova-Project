package com.neueda.auth.service;

import com.neueda.auth.dto.LoginRequest;
import com.neueda.auth.model.User;
import com.neueda.auth.repository.UserRepository;
import com.neueda.auth.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Test
    public void testRegisterAndLogin() {
        // Register
        authService.register("testuser", "password123");

        // Verify user exists
        assertTrue(userRepository.findByUsername("testuser").isPresent());

        // Login
        LoginRequest request = new LoginRequest("testuser", "password123");
        var response = authService.login(request);

        assertNotNull(response.getToken());
        assertEquals("testuser", response.getUsername());
    }

    @Test
    public void testLoginWithWrongPassword() {
        authService.register("user2", "correctpass");

        LoginRequest request = new LoginRequest("user2", "wrongpass");
        assertThrows(RuntimeException.class, () -> authService.login(request));
    }

    @Test
    public void testLoginNonexistentUser() {
        LoginRequest request = new LoginRequest("nonexistent", "password");
        assertThrows(RuntimeException.class, () -> authService.login(request));
    }
}
