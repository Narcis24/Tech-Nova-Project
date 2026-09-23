package com.neueda.app.repositories;

import com.neueda.app.models.Position;
import com.neueda.app.models.PositionKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PositionRepository extends JpaRepository<Position, PositionKey> {
    Optional<Position> findByAccountIdAndSymbol(String accountId, String symbol);
    List<Position> findByAccountId(String accountId);
}
