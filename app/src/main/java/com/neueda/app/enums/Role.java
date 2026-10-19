package com.neueda.app.enums;

/**
 * What a user may do. Must match the CHECK on user_roles.role (V013) and the roles claim auth-service signs.
 * Declared alphabetically, so sorted roles come out in the same order auth-service lists them.
 */
public enum Role {
    ADMIN,
    TRADER
}
