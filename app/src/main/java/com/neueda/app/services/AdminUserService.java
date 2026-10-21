package com.neueda.app.services;

import com.neueda.app.dtos.AdminUserResponse;
import com.neueda.app.dtos.AdminUserDetailResponse;
import com.neueda.app.dtos.AccountResponse;
import com.neueda.app.dtos.CreateUserRequest;
import com.neueda.app.enums.AccountStatus;
import com.neueda.app.enums.Role;
import com.neueda.app.exceptions.AccountNotFoundException;
import com.neueda.app.exceptions.RoleChangeNotAllowedException;
import com.neueda.app.exceptions.UserAlreadyExistsException;
import com.neueda.app.exceptions.UserNotFoundException;
import com.neueda.app.models.User;
import com.neueda.app.repositories.UserRepository;
import com.neueda.app.repositories.AccountRepository;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User management for admins. SecurityConfig lets only the ADMIN role reach its controller. */
@Service
@Slf4j
public class AdminUserService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    @Autowired
    public AdminUserService(UserRepository userRepository, AccountRepository accountRepository) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
    }

    /** Every user with their roles, by username. */
    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers() {
        return userRepository.findAllByOrderByUsernameAsc().stream()
            .map(AdminUserService::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUser(String username) {
        User user = userRepository.findById(username)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
        List<AccountResponse> accounts = accountRepository.findByOwnerUsernameOrderByAccountId(username).stream()
            .map(AccountResponse::new)
            .toList();
        return new AdminUserDetailResponse(
            user.getUsername(), user.getEmail(), user.getRoles().stream().sorted().toList(),
            user.getCreatedAt(), user.getCreatedBy(), accounts);
    }

    @Transactional
    public AccountResponse updateAccountStatus(String username, String accountId, AccountStatus status) {
        userRepository.findById(username)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
        var account = accountRepository.findByIdForUpdate(accountId)
            .filter(candidate -> candidate.isOwnedBy(username))
            .orElseThrow(() -> new AccountNotFoundException(
                "Account not found for user: " + accountId));
        account.changeStatus(status);
        return new AccountResponse(account);
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

    /**
     * Creates an account with no password and the given roles. The caller sends the set-password
     * email after this returns, so the email never goes out for a user that was not saved.
     */
    @Transactional
    public AdminUserResponse createUser(String actingAdmin, CreateUserRequest request) {
        // Clear messages for the usual case; a concurrent create of the same user still fails on the
        // primary key or the email index (409 CONFLICT), since User.isNew() makes save() insert
        if (userRepository.existsById(request.getUsername())) {
            throw new UserAlreadyExistsException("Username already taken: " + request.getUsername());
        }
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new UserAlreadyExistsException("Email already in use: " + request.getEmail());
        }

        User user = userRepository.save(
            User.invited(request.getUsername(), request.getEmail(), request.getRoles(), actingAdmin));
        // No email address in the log
        log.info("Admin {} created user {} with roles {}", actingAdmin, user.getUsername(), user.getRoles());
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
