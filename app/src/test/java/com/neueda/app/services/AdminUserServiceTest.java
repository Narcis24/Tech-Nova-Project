package com.neueda.app.services;

import com.neueda.app.dtos.AdminUserResponse;
import com.neueda.app.dtos.CreateUserRequest;
import com.neueda.app.enums.AccountStatus;
import com.neueda.app.enums.Role;
import com.neueda.app.exceptions.AccountNotFoundException;
import com.neueda.app.exceptions.AccountStatusChangeNotAllowedException;
import com.neueda.app.exceptions.RoleChangeNotAllowedException;
import com.neueda.app.exceptions.UserAlreadyExistsException;
import com.neueda.app.exceptions.UserNotFoundException;
import com.neueda.app.models.User;
import com.neueda.app.models.Account;
import com.neueda.app.repositories.UserRepository;
import com.neueda.app.repositories.AccountRepository;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AccountRiskBandService accountRiskBandService = mock(AccountRiskBandService.class);
    private final AdminUserService adminUserService =
        new AdminUserService(userRepository, accountRepository, accountRiskBandService);

    /** A real User: its fields are set the way Hibernate sets them, since the app never constructs one. */
    private static User user(String username, Role... roles) {
        User user = new User();
        ReflectionTestUtils.setField(user, "username", username);
        ReflectionTestUtils.setField(user, "email", username + "@example.com");
        ReflectionTestUtils.setField(user, "createdAt", Instant.parse("2026-10-19T09:00:00Z"));
        ReflectionTestUtils.setField(user, "roles", new HashSet<>(Set.of(roles)));
        return user;
    }

    private void given(List<String> admins, User user) {
        when(userRepository.lockAdminUsernames()).thenReturn(admins);
        when(userRepository.findById(user.getUsername())).thenReturn(Optional.of(user));
    }

    @Test
    void listUsers_KeepsTheRepositoryOrderAndSortsEachUsersRoles() {
        User alice = user("alice_001", Role.TRADER, Role.ADMIN);
        User bob = user("bob_00001", Role.TRADER);
        when(userRepository.findAllByOrderByUsernameAsc()).thenReturn(List.of(alice, bob));

        List<AdminUserResponse> users = adminUserService.listUsers();

        assertEquals(List.of("alice_001", "bob_00001"),
            users.stream().map(AdminUserResponse::getUsername).toList());
        assertEquals(List.of(Role.ADMIN, Role.TRADER), users.get(0).getRoles());
        assertEquals(List.of(Role.TRADER), users.get(1).getRoles());
        assertEquals("alice_001@example.com", users.get(0).getEmail());
        assertNull(users.get(0).getCreatedBy());
    }

    @Test
    void listUsers_NoUsers_ReturnsEmptyList() {
        when(userRepository.findAllByOrderByUsernameAsc()).thenReturn(List.of());
        assertEquals(List.of(), adminUserService.listUsers());
    }

    @Test
    void getUser_ReturnsOnlyAccountsOwnedByThatUser() {
        User trader = user("trader_one", Role.TRADER);
        Account account = Account.open("ACC-1234", "Trader One", "trader_one");
        when(userRepository.findById("trader_one")).thenReturn(Optional.of(trader));
        when(accountRepository.findByOwnerUsernameOrderByAccountId("trader_one"))
            .thenReturn(List.of(account));
        when(accountRiskBandService.classify(account)).thenReturn(
            new com.neueda.app.dtos.AdminAccountResponse("ACC-1234", "Trader One",
                BigDecimal.ZERO, AccountStatus.ACTIVE, com.neueda.app.enums.RiskBand.LOW));

        var result = adminUserService.getUser("trader_one");

        assertEquals("trader_one", result.getUsername());
        assertEquals(List.of("ACC-1234"), result.getAccounts().stream()
            .map(accountResponse -> accountResponse.getAccountId()).toList());
        verify(accountRepository).findByOwnerUsernameOrderByAccountId("trader_one");
    }

    @Test
    void updateAccountStatus_SuspendsAndUnsuspendsOwnedAccount() {
        User trader = user("trader_one", Role.TRADER);
        Account account = Account.open("ACC-1234", "Trader One", "trader_one");
        when(userRepository.findById("trader_one")).thenReturn(Optional.of(trader));
        when(accountRepository.findByIdForUpdate("ACC-1234")).thenReturn(Optional.of(account));

        adminUserService.updateAccountStatus("trader_one", "ACC-1234", AccountStatus.SUSPENDED);
        assertEquals(AccountStatus.SUSPENDED, account.getAccountStatus());

        adminUserService.updateAccountStatus("trader_one", "ACC-1234", AccountStatus.ACTIVE);
        assertEquals(AccountStatus.ACTIVE, account.getAccountStatus());
    }

    @Test
    void updateAccountStatus_RejectsAccountOwnedByAnotherUser() {
        User other = user("other_user", Role.TRADER);
        Account account = Account.open("ACC-1234", "Trader One", "trader_one");
        when(userRepository.findById("other_user")).thenReturn(Optional.of(other));
        when(accountRepository.findByIdForUpdate("ACC-1234")).thenReturn(Optional.of(account));

        assertThrows(AccountNotFoundException.class,
            () -> adminUserService.updateAccountStatus("other_user", "ACC-1234", AccountStatus.SUSPENDED));
    }

    @Test
    void updateAccountStatus_RejectsUnsupportedStatusTransition() {
        User trader = user("trader_one", Role.TRADER);
        Account account = Account.open("ACC-1234", "Trader One", "trader_one");
        when(userRepository.findById("trader_one")).thenReturn(Optional.of(trader));
        when(accountRepository.findByIdForUpdate("ACC-1234")).thenReturn(Optional.of(account));

        assertThrows(AccountStatusChangeNotAllowedException.class,
            () -> adminUserService.updateAccountStatus("trader_one", "ACC-1234", AccountStatus.CLOSED));
        assertEquals(AccountStatus.ACTIVE, account.getAccountStatus());
    }

    @Test
    void updateRoles_PromotesATrader() {
        User trader = user("trader_one", Role.TRADER);
        given(List.of("test_admin"), trader);

        AdminUserResponse result = adminUserService.updateRoles("test_admin", "trader_one",
            Set.of(Role.ADMIN, Role.TRADER));

        assertEquals(List.of(Role.ADMIN, Role.TRADER), result.getRoles());
        assertEquals(Set.of(Role.ADMIN, Role.TRADER), trader.getRoles());
    }

    @Test
    void updateRoles_DemotesAnotherAdminWhileOneRemains() {
        User other = user("other_admin", Role.ADMIN);
        given(List.of("test_admin", "other_admin"), other);

        adminUserService.updateRoles("test_admin", "other_admin", Set.of(Role.TRADER));

        assertEquals(Set.of(Role.TRADER), other.getRoles());
    }

    @Test
    void updateRoles_AnAdminCanChangeTheirOwnRolesWhileKeepingAdmin() {
        User self = user("test_admin", Role.ADMIN);
        given(List.of("test_admin"), self);

        adminUserService.updateRoles("test_admin", "test_admin", Set.of(Role.ADMIN, Role.TRADER));

        assertEquals(Set.of(Role.ADMIN, Role.TRADER), self.getRoles());
    }

    @Test
    void updateRoles_RefusesToRemoveYourOwnAdminRole() {
        User self = user("test_admin", Role.ADMIN);
        given(List.of("test_admin", "other_admin"), self);

        Set<Role> traderOnly = Set.of(Role.TRADER);
        RoleChangeNotAllowedException ex = assertThrows(RoleChangeNotAllowedException.class,
            () -> adminUserService.updateRoles("test_admin", "test_admin", traderOnly));

        assertEquals("You cannot remove your own admin role", ex.getMessage());
        assertEquals(Set.of(Role.ADMIN), self.getRoles());
    }

    @Test
    void updateRoles_RefusesToRemoveTheLastAdmin() {
        // Only reachable if the acting admin lost ADMIN after their token was issued (up to 15 minutes)
        User last = user("only_admin", Role.ADMIN);
        given(List.of("only_admin"), last);

        Set<Role> traderOnly = Set.of(Role.TRADER);
        RoleChangeNotAllowedException ex = assertThrows(RoleChangeNotAllowedException.class,
            () -> adminUserService.updateRoles("former_admin", "only_admin", traderOnly));

        assertEquals("At least one admin must remain", ex.getMessage());
        assertEquals(Set.of(Role.ADMIN), last.getRoles());
    }

    @Test
    void updateRoles_UnknownUser_Throws() {
        when(userRepository.lockAdminUsernames()).thenReturn(List.of("test_admin"));
        when(userRepository.findById("nobody_00")).thenReturn(Optional.empty());

        Set<Role> traderOnly = Set.of(Role.TRADER);
        assertThrows(UserNotFoundException.class,
            () -> adminUserService.updateRoles("test_admin", "nobody_00", traderOnly));
    }

    private static final CreateUserRequest NEW_USER =
        new CreateUserRequest("new_user1", "New.User@example.com", Set.of(Role.TRADER));

    @Test
    void createUser_SavesAnInvitedUserCreatedByTheActingAdmin() {
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        AdminUserResponse result = adminUserService.createUser("test_admin", NEW_USER);

        assertEquals("new_user1", result.getUsername());
        assertEquals("New.User@example.com", result.getEmail());
        assertEquals(List.of(Role.TRADER), result.getRoles());
        assertEquals("test_admin", result.getCreatedBy());
        assertNotNull(result.getCreatedAt());
    }

    @Test
    void createUser_TakenUsername_Throws() {
        when(userRepository.existsById("new_user1")).thenReturn(true);

        UserAlreadyExistsException ex = assertThrows(UserAlreadyExistsException.class,
            () -> adminUserService.createUser("test_admin", NEW_USER));

        assertEquals("Username already taken: new_user1", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_TakenEmail_Throws() {
        when(userRepository.existsByEmailIgnoreCase("New.User@example.com")).thenReturn(true);

        UserAlreadyExistsException ex = assertThrows(UserAlreadyExistsException.class,
            () -> adminUserService.createUser("test_admin", NEW_USER));

        assertEquals("Email already in use: New.User@example.com", ex.getMessage());
        verify(userRepository, never()).save(any());
    }
}

