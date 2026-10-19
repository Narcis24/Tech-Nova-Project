package com.neueda.app.controllers;

import com.neueda.app.configs.SecurityConfig;
import com.neueda.app.dtos.AdminUserResponse;
import com.neueda.app.dtos.CreateUserRequest;
import com.neueda.app.enums.Role;
import com.neueda.app.exceptions.RoleChangeNotAllowedException;
import com.neueda.app.exceptions.UserAlreadyExistsException;
import com.neueda.app.exceptions.UserNotFoundException;
import com.neueda.app.services.AdminUserService;
import com.neueda.app.services.AuthClient;
import com.neueda.app.utils.JwtUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs with the real security chain (JwtFilter + SecurityConfig), unlike the other controller tests. */
@WebMvcTest(AdminUserController.class)
@Import(SecurityConfig.class)
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminUserService adminUserService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private AuthClient authClient;

    @BeforeEach
    void tokens() {
        when(jwtUtil.isTokenValid("admin-token")).thenReturn(true);
        when(jwtUtil.extractUsername("admin-token")).thenReturn("test_admin");
        when(jwtUtil.extractRoles("admin-token")).thenReturn(List.of("ADMIN"));

        when(jwtUtil.isTokenValid("trader-token")).thenReturn(true);
        when(jwtUtil.extractUsername("trader-token")).thenReturn("trader_one");
        when(jwtUtil.extractRoles("trader-token")).thenReturn(List.of("TRADER"));
    }

    @Test
    void listUsers_NoToken_Returns401() throws Exception {
        mockMvc.perform(get("/v1/admin/users"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verifyNoInteractions(adminUserService);
    }

    @Test
    void listUsers_Trader_Returns403() throws Exception {
        mockMvc.perform(get("/v1/admin/users").header("Authorization", "Bearer trader-token"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
            .andExpect(jsonPath("$.message").value("Admin role required"))
            .andExpect(jsonPath("$.httpStatus").value(403));

        verifyNoInteractions(adminUserService);
    }

    @Test
    void listUsers_Admin_ReturnsUsersWithoutPasswords() throws Exception {
        when(adminUserService.listUsers()).thenReturn(List.of(
            new AdminUserResponse("test_admin", "test_admin@technova.local", List.of(Role.ADMIN),
                Instant.parse("2026-10-19T09:25:14Z"), null),
            new AdminUserResponse("trader_one", "trader_one@example.com", List.of(Role.TRADER),
                Instant.parse("2026-10-19T09:25:31Z"), null)));

        mockMvc.perform(get("/v1/admin/users").header("Authorization", "Bearer admin-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].username").value("test_admin"))
            .andExpect(jsonPath("$[0].roles[0]").value("ADMIN"))
            .andExpect(jsonPath("$[1].username").value("trader_one"))
            .andExpect(jsonPath("$[1].roles[0]").value("TRADER"))
            .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    private org.springframework.test.web.servlet.ResultActions putRoles(String token, String username, String body)
            throws Exception {
        return mockMvc.perform(put("/v1/admin/users/" + username + "/roles")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    @Test
    void updateRoles_Admin_PassesTheActingAdminFromTheToken() throws Exception {
        when(adminUserService.updateRoles("test_admin", "trader_one", Set.of(Role.ADMIN, Role.TRADER)))
            .thenReturn(new AdminUserResponse("trader_one", "trader_one@example.com",
                List.of(Role.ADMIN, Role.TRADER), Instant.parse("2026-10-19T09:25:31Z"), null));

        putRoles("admin-token", "trader_one", "{\"roles\":[\"TRADER\",\"ADMIN\"]}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("trader_one"))
            .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
            .andExpect(jsonPath("$.roles[1]").value("TRADER"));

        verify(adminUserService).updateRoles("test_admin", "trader_one", Set.of(Role.ADMIN, Role.TRADER));
    }

    @Test
    void updateRoles_Trader_Returns403() throws Exception {
        putRoles("trader-token", "trader_one", "{\"roles\":[\"ADMIN\"]}")
            .andExpect(status().isForbidden());

        verifyNoInteractions(adminUserService);
    }

    @Test
    void updateRoles_EmptyRoles_Returns400() throws Exception {
        putRoles("admin-token", "trader_one", "{\"roles\":[]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("roles: A user needs at least one role"));

        verifyNoInteractions(adminUserService);
    }

    @Test
    void updateRoles_UnknownRole_Returns400() throws Exception {
        putRoles("admin-token", "trader_one", "{\"roles\":[\"SUPERUSER\"]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));

        verifyNoInteractions(adminUserService);
    }

    @Test
    void updateRoles_UnknownUser_Returns404() throws Exception {
        when(adminUserService.updateRoles("test_admin", "nobody_00", Set.of(Role.TRADER)))
            .thenThrow(new UserNotFoundException("User not found: nobody_00"));

        putRoles("admin-token", "nobody_00", "{\"roles\":[\"TRADER\"]}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"));
    }

    @Test
    void updateRoles_RefusedChange_Returns409() throws Exception {
        when(adminUserService.updateRoles("test_admin", "test_admin", Set.of(Role.TRADER)))
            .thenThrow(new RoleChangeNotAllowedException("You cannot remove your own admin role"));

        putRoles("admin-token", "test_admin", "{\"roles\":[\"TRADER\"]}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("ROLE_CHANGE_NOT_ALLOWED"))
            .andExpect(jsonPath("$.message").value("You cannot remove your own admin role"));
    }

    private static final String NEW_USER_JSON =
        "{\"username\":\"new_user1\",\"email\":\"new.user@example.com\",\"roles\":[\"TRADER\"]}";

    private org.springframework.test.web.servlet.ResultActions postUser(String token, String body) throws Exception {
        return mockMvc.perform(post("/v1/admin/users")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private static AdminUserResponse newUser() {
        return new AdminUserResponse("new_user1", "new.user@example.com", List.of(Role.TRADER),
            Instant.parse("2026-10-19T11:00:00Z"), "test_admin");
    }

    @Test
    void createUser_Admin_Returns201AndSendsTheInvite() throws Exception {
        when(adminUserService.createUser(eq("test_admin"), any(CreateUserRequest.class))).thenReturn(newUser());
        when(authClient.sendInvite("new.user@example.com")).thenReturn(true);

        postUser("admin-token", NEW_USER_JSON)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.user.username").value("new_user1"))
            .andExpect(jsonPath("$.user.createdBy").value("test_admin"))
            .andExpect(jsonPath("$.inviteSent").value(true));

        verify(authClient).sendInvite("new.user@example.com");
    }

    @Test
    void createUser_InviteFails_StillReturns201() throws Exception {
        when(adminUserService.createUser(eq("test_admin"), any(CreateUserRequest.class))).thenReturn(newUser());
        when(authClient.sendInvite("new.user@example.com")).thenReturn(false);

        postUser("admin-token", NEW_USER_JSON)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.inviteSent").value(false));
    }

    @Test
    void createUser_Trader_Returns403() throws Exception {
        postUser("trader-token", NEW_USER_JSON).andExpect(status().isForbidden());

        verifyNoInteractions(adminUserService, authClient);
    }

    @Test
    void createUser_InvalidUsername_Returns400() throws Exception {
        postUser("admin-token",
                "{\"username\":\"bob\",\"email\":\"bob@example.com\",\"roles\":[\"TRADER\"]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("username: Username must be 8-16 characters"));

        verifyNoInteractions(adminUserService, authClient);
    }

    @Test
    void createUser_TakenUsername_Returns409AndSendsNoInvite() throws Exception {
        when(adminUserService.createUser(eq("test_admin"), any(CreateUserRequest.class)))
            .thenThrow(new UserAlreadyExistsException("Username already taken: new_user1"));

        postUser("admin-token", NEW_USER_JSON)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("USER_ALREADY_EXISTS"))
            .andExpect(jsonPath("$.message").value("Username already taken: new_user1"));

        verifyNoInteractions(authClient);
    }
}

