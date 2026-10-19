package com.neueda.app.repositories;

import com.neueda.app.enums.PieLinkMode;
import com.neueda.app.models.AccountPie;
import com.neueda.app.enums.PieLinkMode;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountPieRepository extends JpaRepository<AccountPie, String> {
    List<AccountPie> findByPieId(UUID pieId);

    /** Accounts copying the pie that follow its new versions on their own. */
    List<AccountPie> findByPieIdAndModeAndAutoSyncTrue(UUID pieId, PieLinkMode mode);

    List<AccountPie> findBySyncPendingTrue();

    boolean existsByPieIdAndAccountIdIn(UUID pieId, Collection<String> accountIds);
    boolean existsByPieIdAndMode(UUID pieId, PieLinkMode mode);

    /** Number of accounts copying each pie. Pies nobody copies are absent. */
    @Query("select a.pieId as pieId, count(a) as followers from AccountPie a "
        + "where a.pieId in :pieIds and a.mode = com.neueda.app.enums.PieLinkMode.COPY group by a.pieId")
    List<FollowerCount> countFollowers(@Param("pieIds") Collection<UUID> pieIds);

    interface FollowerCount {
        UUID getPieId();

        long getFollowers();
    }
}
