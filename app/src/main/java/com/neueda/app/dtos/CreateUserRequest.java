package com.neueda.app.dtos;

import com.neueda.app.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A new account an admin creates. Same username and email rules as auth-service registration
 * (auth/src/dto.ts), or the user could never log in.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 8, max = 16, message = "Username must be 8-16 characters")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "Username may contain only letters, digits and underscore")
    private String username;

    // @Email accepts null and "", so @NotBlank is what makes it required
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    @Size(max = 254, message = "Email cannot exceed 254 characters")
    private String email;

    // A Set, so ["TRADER", "TRADER"] is one role; an unknown name fails to parse (400 MALFORMED_REQUEST)
    @NotEmpty(message = "A user needs at least one role")
    private Set<@NotNull Role> roles;
}
