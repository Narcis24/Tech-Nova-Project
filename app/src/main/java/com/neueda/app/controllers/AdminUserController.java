package com.neueda.app.controllers;

import com.neueda.app.dtos.AdminUserResponse;
import com.neueda.app.dtos.UpdateRolesRequest;
import com.neueda.app.services.AdminUserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** User management. SecurityConfig restricts every /v1/admin/** route to the ADMIN role. */
@RestController
@RequestMapping("/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ResponseEntity<List<AdminUserResponse>> listUsers() {
        return ResponseEntity.ok(adminUserService.listUsers());
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
