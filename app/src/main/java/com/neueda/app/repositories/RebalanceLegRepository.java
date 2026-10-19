package com.neueda.app.repositories;

import com.neueda.app.models.RebalanceLeg;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Keyed by order id: an order belongs to at most one leg. */
@Repository
public interface RebalanceLegRepository extends JpaRepository<RebalanceLeg, UUID> {
    /** Sells first (SELL sorts after BUY), then by symbol. */
    List<RebalanceLeg> findByRunIdOrderByPhaseDescSymbolAsc(UUID runId);
}
