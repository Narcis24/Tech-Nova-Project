package com.neueda.app.exceptions;

/**
 * Exception thrown when a role change would lock admins out: an admin removing their own ADMIN role,
 * or the last admin losing it.
 */
public class RoleChangeNotAllowedException extends RuntimeException {

    public RoleChangeNotAllowedException(String message) {
        super(message);
    }
}
