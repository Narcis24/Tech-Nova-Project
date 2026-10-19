package com.neueda.app.repositories;

import com.neueda.app.models.AccountPie;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountPieRepository extends JpaRepository<AccountPie, String> {
    List<AccountPie> findByPieId(UUID pieId);

    boolean existsByPieIdAndAccountIdIn(UUID pieId, Collection<String> accountIds);
}
