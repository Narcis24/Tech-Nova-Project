package com.neueda.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * User entity representing an authenticated user
 * 
 * Validation Rules:
 * - username: Not empty, 8-16 characters
 * - passwordHash: Not empty (hash of minimum 12 character password)
 */
@Entity
@Table(name = "users")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class User {
    
    @Id
    @Column(name = "username")
    @NotBlank(message = "Username cannot be empty")
    @Size(min = 8, max = 16, message = "Username must be between 8 and 16 characters")
    private String username;
    
    @Column(name = "password_hash", nullable = false)
    @NotBlank(message = "Password hash cannot be empty")
    private String passwordHash;
}
