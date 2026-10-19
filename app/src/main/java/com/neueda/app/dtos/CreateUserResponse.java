package com.neueda.app.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** The created user, and whether auth-service accepted the set-password email for them. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserResponse {
    private AdminUserResponse user;
    // False if auth-service could not be reached: the user exists and can still use "Forgot password"
    private boolean inviteSent;
}
