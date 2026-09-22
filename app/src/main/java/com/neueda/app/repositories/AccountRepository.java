package com.neueda.app.repositories;

import com.neueda.app.models.Account;
import java.util.Optional;

public interface AccountRepository {
    Optional<Account> findById(String id);
    void save(Account account);
    void update(Account account);
    void delete(String id);
}
