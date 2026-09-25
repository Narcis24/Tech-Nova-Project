package com.neueda.auth.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Login request DTO
 * 
 * Validation Rules:
 * - username: 8-16 characters
 * - password: 12-20 characters, must contain:
 *   • At least one digit (0-9)
 *   • At least one uppercase letter (A-Z)
 *   • At least one lowercase letter (a-z)
 *   • At least one special character (-, ., ?, _, !, @)
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class LoginRequest {
    @NotBlank(message = "Username is required")
    @Size(min = 8, max = 16, message = "Username must be between 8 and 16 characters")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 12, max = 20, message = "Password must be between 12 and 20 characters")
    @Pattern(
        regexp = "^(?=.*[0-9])(?=.*[A-Z])(?=.*[a-z])(?=.*[-._?_!@]).+$",
        message = "Password must contain at least one digit, one uppercase letter, one lowercase letter, and one special character (-._?_!@)"
    )
    private String password;
}
