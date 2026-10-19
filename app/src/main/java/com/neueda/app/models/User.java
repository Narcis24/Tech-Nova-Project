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
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * A user and their roles, for admin user management. The users table belongs to auth-service, which
 * registers users and checks passwords; password_hash is deliberately not mapped, so this app can
 * never read or overwrite it.
 */
@Entity
@Table(name = "users")
@NoArgsConstructor
@Getter
public class User implements Persistable<String> {
    @Id
    @Column(name = "username")
    private String username;

    @Column(name = "email")
    private String email;

    // Set by invited() for users this app creates; auth-service's inserts get the column's DEFAULT now()
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /** The admin who created the account; null if self-registered. */
    @Column(name = "created_by")
    private String createdBy;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "username"))
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

    /**
     * True only for a User built by invited() and not yet saved. The username is assigned, not generated,
     * so without this Spring Data's save() would treat every User as existing and MERGE it: a concurrent
     * create of the same username would then overwrite that user instead of failing on the primary key.
     */
    @Transient
    private boolean isNew;

    /** A user an admin creates. No password: they set one from the emailed link. */
    public static User invited(String username, String email, Set<Role> roles, String createdBy) {
        User user = new User();
        user.username = username;
        user.email = email;
        user.roles = new HashSet<>(roles);
        user.createdBy = createdBy;
        user.createdAt = Instant.now();
        user.isNew = true;
        return user;
    }

    @Override
    public String getId() {
        return username;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    /** Once loaded from or saved to the database, save() must update rather than insert. */
    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }

    /** Replaces every role; Hibernate writes the difference to user_roles when the transaction commits. */
    public void replaceRoles(Set<Role> newRoles) {
        roles.clear();
        roles.addAll(newRoles);
    }
}
