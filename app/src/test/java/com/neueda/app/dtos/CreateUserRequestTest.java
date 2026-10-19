package com.neueda.app.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.neueda.app.enums.Role;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/** The username and email rules must match auth-service registration, or created users could not log in. */
class CreateUserRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean isValid(String property, CreateUserRequest request) {
        return validator.validateProperty(request, property).isEmpty();
    }

    private boolean usernameIsValid(String username) {
        return isValid("username", new CreateUserRequest(username, "new.user@example.com", Set.of(Role.TRADER)));
    }

    private boolean emailIsValid(String email) {
        return isValid("email", new CreateUserRequest("new_user1", email, Set.of(Role.TRADER)));
    }

    @Test
    void aCompleteRequestIsValid() {
        CreateUserRequest request = new CreateUserRequest("new_user1", "new.user@example.com", Set.of(Role.TRADER));
        assertTrue(validator.validate(request).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"john_doe", "trader_one", "ABCDEFGH12345678", "a_b_c_d_"})
    void usernamesAuthAcceptsAreValid(String username) {
        assertTrue(usernameIsValid(username));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "short", "seventeen_chars_x", "john.doe1", "john doe1", "john-doe1"})
    void usernamesAuthRejectsAreInvalid(String username) {
        assertFalse(usernameIsValid(username));
    }

    @Test
    void usernameIsRequired() {
        assertFalse(usernameIsValid(null));
    }

    @Test
    void emailMustBePresentAndWellFormed() {
        assertTrue(emailIsValid("new.user@example.com"));
        assertFalse(emailIsValid(null));
        assertFalse(emailIsValid(""));
        assertFalse(emailIsValid("not-an-email"));
        assertFalse(emailIsValid("a".repeat(250) + "@x.io"));
    }

    @Test
    void atLeastOneRoleIsRequired() {
        assertFalse(isValid("roles", new CreateUserRequest("new_user1", "new.user@example.com", Set.of())));
        assertFalse(isValid("roles", new CreateUserRequest("new_user1", "new.user@example.com", null)));
    }

    @Test
    void theMessageSaysWhatIsWrong() {
        CreateUserRequest request = new CreateUserRequest("short", "new.user@example.com", Set.of(Role.TRADER));
        assertEquals("Username must be 8-16 characters",
            validator.validateProperty(request, "username").iterator().next().getMessage());
    }
}
