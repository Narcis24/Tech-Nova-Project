package com.neueda.app.repositories;

import com.neueda.app.models.AccountPie;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountPieRepository extends JpaRepository<AccountPie, String> {
    List<AccountPie> findByPieId(UUID pieId);

    /** Apply only the completed version, without changing or replacing the account's pie link. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AccountPie a set a.appliedVersion = :version where a.accountId = :accountId and a.pieId = :pieId")
    int applyVersion(@Param("accountId") String accountId, @Param("pieId") UUID pieId, @Param("version") int version);

    boolean existsByPieIdAndAccountIdIn(UUID pieId, Collection<String> accountIds);

    /** Number of accounts copying each pie. Pies nobody copies are absent. */
    @Query("select a.pieId as pieId, count(a) as followers from AccountPie a "
        + "where a.pieId in :pieIds and a.mode = com.neueda.app.enums.PieLinkMode.COPY group by a.pieId")
    List<FollowerCount> countFollowers(@Param("pieIds") Collection<UUID> pieIds);

    interface FollowerCount {
        UUID getPieId();

        long getFollowers();
    }
}
