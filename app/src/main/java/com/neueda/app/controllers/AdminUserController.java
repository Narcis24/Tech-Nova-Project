package com.neueda.app.controllers;

import com.neueda.app.dtos.AdminUserResponse;
import com.neueda.app.dtos.CreateUserRequest;
import com.neueda.app.dtos.CreateUserResponse;
import com.neueda.app.dtos.UpdateRolesRequest;
import com.neueda.app.services.AdminUserService;
import com.neueda.app.services.AuthClient;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** User management. SecurityConfig restricts every /v1/admin/** route to the ADMIN role. */
@RestController
@RequestMapping("/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final AuthClient authClient;

    public AdminUserController(AdminUserService adminUserService, AuthClient authClient) {
        this.adminUserService = adminUserService;
        this.authClient = authClient;
    }

    @GetMapping
    public ResponseEntity<List<AdminUserResponse>> listUsers() {
        return ResponseEntity.ok(adminUserService.listUsers());
    }

    /**
     * Creates a user with no password, then asks auth-service to email them a set-password link. The
     * email is requested only after createUser has committed, so it never names a user that was not
     * saved, and auth-service (on its own connection) can already see the new email address.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)   // so the OpenAPI spec documents 201, not the default 200
    public ResponseEntity<CreateUserResponse> createUser(@Valid @RequestBody CreateUserRequest request,
                                                         Authentication authentication) {
        AdminUserResponse user = adminUserService.createUser(authentication.getName(), request);
        boolean inviteSent = authClient.sendInvite(user.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateUserResponse(user, inviteSent));
    }

    /** Replaces the user's roles. The acting admin is the token's subject, set by JwtFilter. */
    @PutMapping("/{username}/roles")
    public ResponseEntity<AdminUserResponse> updateRoles(@PathVariable String username,
                                                         @Valid @RequestBody UpdateRolesRequest request,
                                                         Authentication authentication) {
        return ResponseEntity.ok(
            adminUserService.updateRoles(authentication.getName(), username, request.getRoles()));
    }
}
