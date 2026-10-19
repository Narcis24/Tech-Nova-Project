package com.neueda.app.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** The created user, and whether auth-service accepted the set-password email for them. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserResponse {
    @Schema(requiredMode = RequiredMode.REQUIRED)
    private AdminUserResponse user;
    // False if auth-service could not be reached: the user exists and can still use "Forgot password"
    @Schema(requiredMode = RequiredMode.REQUIRED)
    private boolean inviteSent;
}
