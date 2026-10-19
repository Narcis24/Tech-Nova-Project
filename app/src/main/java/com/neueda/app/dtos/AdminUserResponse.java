package com.neueda.app.dtos;

import com.neueda.app.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
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
    // Always set, so the OpenAPI spec marks them required and generated clients don't type them as optional
    @Schema(requiredMode = RequiredMode.REQUIRED)
    private String username;
    private String email;            // null for accounts registered before emails were collected
    @Schema(requiredMode = RequiredMode.REQUIRED)
    private List<Role> roles;        // alphabetical, as in the access token
    @Schema(requiredMode = RequiredMode.REQUIRED)
    private Instant createdAt;
    private String createdBy;        // the admin who created the account; null if self-registered
}
