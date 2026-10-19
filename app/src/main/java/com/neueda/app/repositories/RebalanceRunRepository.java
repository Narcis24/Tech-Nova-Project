package com.neueda.app.repositories;

import com.neueda.app.enums.RebalanceStatus;
import com.neueda.app.models.RebalanceRun;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RebalanceRunRepository extends JpaRepository<RebalanceRun, UUID> {
    boolean existsByAccountIdAndStatusIn(String accountId, Collection<RebalanceStatus> statuses);

    List<RebalanceRun> findByStatusIn(Collection<RebalanceStatus> statuses);

    /**
     * Loads the run with a row lock held until the transaction ends, so a cancel and an advance
     * on the same run take turns. Settlement never takes it, so it can't deadlock with a fill.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RebalanceRun r WHERE r.runId = :runId")
    Optional<RebalanceRun> findByIdForUpdate(@Param("runId") UUID runId);
}
