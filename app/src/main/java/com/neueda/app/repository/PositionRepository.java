package com.neueda.app.repository;

import com.neueda.app.model.Position;
import com.neueda.app.model.PositionKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PositionRepository extends JpaRepository<Position, PositionKey> {
    Optional<Position> findByAccountIdAndSymbol(String accountId, String symbol);
    List<Position> findByAccountId(String accountId);
}
