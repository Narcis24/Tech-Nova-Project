package com.neueda.app.dtos;

import com.neueda.app.enums.Role;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A user as admins see them. Never includes the password hash. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {
    private String username;
    private String email;            // null for accounts registered before emails were collected
    private List<Role> roles;        // alphabetical, as in the access token
    private Instant createdAt;
    private String createdBy;        // the admin who created the account; null if self-registered
}
