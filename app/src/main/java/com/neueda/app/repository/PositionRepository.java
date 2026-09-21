package com.neueda.app.repository;

import com.neueda.app.model.Position;
import java.util.List;
import java.util.Optional;

public interface PositionRepository {
    Optional<Position> findByAccountIdAndSymbol(String accountId, String symbol);
    List<Position> findByAccountId(String accountId);
    void save(Position position);
    void update(Position position);
    void delete(String accountId, String symbol);
}
