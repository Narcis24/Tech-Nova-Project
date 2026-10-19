package com.neueda.app.models;

import com.neueda.app.enums.Role;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A user and their roles, for admin user management. The users table belongs to auth-service, which
 * registers users and checks passwords; password_hash is deliberately not mapped, so this app can
 * never read or overwrite it.
 */
@Entity
@Table(name = "users")
@NoArgsConstructor
@Getter
public class User {
    @Id
    @Column(name = "username")
    private String username;

    @Column(name = "email")
    private String email;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** The admin who created the account; null if self-registered. */
    @Column(name = "created_by")
    private String createdBy;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "username"))
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

    /** Replaces every role; Hibernate writes the difference to user_roles when the transaction commits. */
    public void replaceRoles(Set<Role> newRoles) {
        roles.clear();
        roles.addAll(newRoles);
    }
}
