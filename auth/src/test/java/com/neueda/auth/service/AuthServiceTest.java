package com.neueda.auth.service;

import com.neueda.auth.dto.LoginRequest;
import com.neueda.auth.model.User;
import com.neueda.auth.repository.UserRepository;
import com.neueda.auth.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    public void testRegisterAndLogin() {
        String username = "testuser";
        String password = "password123";

        // Mock: user doesn't exist initially, then exists after registration
        when(userRepository.findByUsername(username))
            .thenReturn(Optional.empty())  // For register() check
            .thenReturn(Optional.of(new User(username, passwordEncoder.encode(password))));  // For login()
        
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtil.generateToken(username)).thenReturn("mock-jwt-token");

        // Register
        authService.register(username, password);
        verify(userRepository, times(1)).save(any(User.class));

        // Login
        LoginRequest request = new LoginRequest(username, password);
        var response = authService.login(request);

        assertNotNull(response.getToken());
        assertEquals(username, response.getUsername());
        assertEquals("mock-jwt-token", response.getToken());
    }

    @Test
    public void testLoginWithWrongPassword() {
        String username = "user2";
        String correctPassword = "correctpass";
        String wrongPassword = "wrongpass";

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(
            new User(username, passwordEncoder.encode(correctPassword))
        ));

        LoginRequest request = new LoginRequest(username, wrongPassword);
        assertThrows(RuntimeException.class, () -> authService.login(request));
    }

    @Test
    public void testLoginNonexistentUser() {
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest("nonexistent", "password");
        assertThrows(RuntimeException.class, () -> authService.login(request));
    }
}
