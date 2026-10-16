package com.neueda.app.repositories;

import com.neueda.app.models.AccountRiskLimit;
import com.neueda.app.models.AccountRiskLimitKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AccountRiskLimitRepository extends JpaRepository<AccountRiskLimit, AccountRiskLimitKey> {
    List<AccountRiskLimit> findByAccountId(String accountId);
}
