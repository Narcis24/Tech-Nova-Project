package com.neueda.app.repositories;

import com.neueda.app.models.Price;
import com.neueda.app.models.PriceKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PriceRepository extends JpaRepository<Price, PriceKey> {
    Optional<Price> findFirstBySymbolOrderByTradeDateDesc(String symbol);
}
