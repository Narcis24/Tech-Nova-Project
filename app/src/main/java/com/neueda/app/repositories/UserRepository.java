package com.neueda.app.repositories;

import com.neueda.app.models.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    List<User> findAllByOrderByUsernameAsc();

    /**
     * The usernames that hold ADMIN, locking those rows until the transaction ends. A concurrent role
     * change waits here and then sees the result, so two admins removing each other cannot both pass
     * the last-admin check. Must run inside a transaction.
     */
    @Query(value = "SELECT username FROM user_roles WHERE role = 'ADMIN' FOR UPDATE", nativeQuery = true)
    List<String> lockAdminUsernames();
}
