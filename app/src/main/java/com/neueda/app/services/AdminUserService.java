package com.neueda.app.services;

import com.neueda.app.dtos.AdminUserResponse;
import com.neueda.app.enums.Role;
import com.neueda.app.exceptions.RoleChangeNotAllowedException;
import com.neueda.app.exceptions.UserNotFoundException;
import com.neueda.app.models.User;
import com.neueda.app.repositories.UserRepository;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User management for admins. SecurityConfig lets only the ADMIN role reach its controller. */
@Service
@Slf4j
public class AdminUserService {

    private final UserRepository userRepository;

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Every user with their roles, by username. */
    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers() {
        return userRepository.findAllByOrderByUsernameAsc().stream()
            .map(AdminUserService::toResponse)
            .toList();
    }

    /**
     * Gives the user exactly these roles. Refuses to let an admin remove their own ADMIN role, or to
     * leave no admin at all. The change reaches the user's access token at their next refresh, within
     * 15 minutes, since auth-service reads roles afresh each time.
     */
    @Transactional
    public AdminUserResponse updateRoles(String actingAdmin, String username, Set<Role> roles) {
        // Lock first, so a concurrent change waits and the count below is current
        List<String> admins = userRepository.lockAdminUsernames();
        User user = userRepository.findById(username)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + username));

        boolean losesAdmin = user.getRoles().contains(Role.ADMIN) && !roles.contains(Role.ADMIN);
        if (losesAdmin && username.equals(actingAdmin)) {
            throw new RoleChangeNotAllowedException("You cannot remove your own admin role");
        }
        if (losesAdmin && admins.size() <= 1) {
            throw new RoleChangeNotAllowedException("At least one admin must remain");
        }

        Set<Role> before = Set.copyOf(user.getRoles());
        user.replaceRoles(roles);
        log.info("Admin {} changed roles of {} from {} to {}", actingAdmin, username, before, roles);
        return toResponse(user);
    }

    static AdminUserResponse toResponse(User user) {
        return new AdminUserResponse(
            user.getUsername(),
            user.getEmail(),
            user.getRoles().stream().sorted().toList(),
            user.getCreatedAt(),
            user.getCreatedBy());
    }
}
