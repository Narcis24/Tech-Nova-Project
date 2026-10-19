package com.neueda.app.services;

import com.neueda.app.dtos.CreateUserRequest;
import com.neueda.app.enums.Role;
import com.neueda.app.exceptions.RoleChangeNotAllowedException;
import com.neueda.app.exceptions.UserAlreadyExistsException;
import com.neueda.app.models.User;
import com.neueda.app.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Role changes against a real (H2) database, where the row lock in lockAdminUsernames does the work.
 * Runs without a test transaction so each change commits on its own.
 */
@DataJpaTest
@Import(AdminUserService.class)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminUserServiceConcurrencyTest {

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        for (String admin : List.of("admin_one", "admin_two")) {
            jdbc.update("INSERT INTO users (username, email) VALUES (?, ?)", admin, admin + "@example.com");
            jdbc.update("INSERT INTO user_roles (username, role) VALUES (?, 'ADMIN')", admin);
        }
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM users");
    }

    private List<String> admins() {
        return jdbc.queryForList("SELECT username FROM user_roles WHERE role = 'ADMIN' ORDER BY username",
            String.class);
    }

    @Test
    void demotionIsSaved() {
        adminUserService.updateRoles("admin_one", "admin_two", Set.of(Role.TRADER));

        assertEquals(List.of("admin_one"), admins());
        assertEquals(List.of("TRADER"),
            jdbc.queryForList("SELECT role FROM user_roles WHERE username = 'admin_two'", String.class));
    }

    @Test
    void theLastAdminCannotBeDemotedAfterwards() {
        adminUserService.updateRoles("admin_one", "admin_two", Set.of(Role.TRADER));

        // admin_two's token still says ADMIN for up to 15 minutes
        assertThrows(RoleChangeNotAllowedException.class,
            () -> adminUserService.updateRoles("admin_two", "admin_one", Set.of(Role.TRADER)));
        assertEquals(List.of("admin_one"), admins());
    }

    @Test
    void twoAdminsDemotingEachOtherAtOnceLeaveOneAdmin() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        try {
            results.add(pool.submit(() -> {
                start.await();
                return adminUserService.updateRoles("admin_one", "admin_two", Set.of(Role.TRADER));
            }));
            results.add(pool.submit(() -> {
                start.await();
                return adminUserService.updateRoles("admin_two", "admin_one", Set.of(Role.TRADER));
            }));
            start.countDown();

            int succeeded = 0;
            for (Future<?> result : results) {
                try {
                    result.get();
                    succeeded++;
                } catch (ExecutionException e) {
                    // The one that waited for the lock sees a single admin left and is refused
                }
            }

            assertEquals(1, succeeded);
            assertEquals(1, admins().size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void createdUserIsSavedWithRolesAndCreator() {
        adminUserService.createUser("admin_one",
            new CreateUserRequest("new_user1", "new.user@example.com", Set.of(Role.TRADER, Role.ADMIN)));

        Map<String, Object> row = jdbc.queryForMap(
            "SELECT email, created_by, created_at FROM users WHERE username = 'new_user1'");
        assertEquals("new.user@example.com", row.get("EMAIL"));
        assertEquals("admin_one", row.get("CREATED_BY"));
        assertNotNull(row.get("CREATED_AT"));
        assertEquals(List.of("ADMIN", "TRADER"), jdbc.queryForList(
            "SELECT role FROM user_roles WHERE username = 'new_user1' ORDER BY role", String.class));
    }

    @Test
    void creatingAnExistingUsernameLeavesTheExistingUserUntouched() {
        assertThrows(UserAlreadyExistsException.class, () -> adminUserService.createUser("admin_one",
            new CreateUserRequest("admin_two", "someone.else@example.com", Set.of(Role.TRADER))));

        assertEquals("admin_two@example.com",
            jdbc.queryForObject("SELECT email FROM users WHERE username = 'admin_two'", String.class));
    }

    @Test
    void savingAnInvitedUserNeverOverwritesAnExistingOne() {
        // What a create that passed existsById just before another admin's create committed would do.
        // Without User.isNew(), save() would MERGE: silently replace admin_two's email and roles.
        assertThrows(DataIntegrityViolationException.class, () -> userRepository.save(
            User.invited("admin_two", "intruder@example.com", Set.of(Role.TRADER), "admin_one")));

        assertEquals("admin_two@example.com",
            jdbc.queryForObject("SELECT email FROM users WHERE username = 'admin_two'", String.class));
        assertEquals(List.of("ADMIN"),
            jdbc.queryForList("SELECT role FROM user_roles WHERE username = 'admin_two'", String.class));
    }

    @Test
    void twoAdminsCreatingTheSameUsernameAtOnceCreateItOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        try {
            for (String email : List.of("first@example.com", "second@example.com")) {
                results.add(pool.submit(() -> {
                    start.await();
                    return adminUserService.createUser("admin_one",
                        new CreateUserRequest("race_user", email, Set.of(Role.TRADER)));
                }));
            }
            start.countDown();

            int succeeded = 0;
            for (Future<?> result : results) {
                try {
                    result.get();
                    succeeded++;
                } catch (ExecutionException e) {
                    // The loser fails on the primary key (or on the existsById check, if it ran second)
                }
            }

            assertEquals(1, succeeded);
            assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = 'race_user'", Integer.class));
        } finally {
            pool.shutdownNow();
        }
    }
}

