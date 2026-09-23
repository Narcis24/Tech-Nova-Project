package com.neueda.app.repository;

import com.neueda.app.model.Price;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface PriceRepository extends JpaRepository<Price, String> {
    Optional<Price> findBySymbol(String symbol);
    Optional<BigDecimal> findLatestPrice(String symbol);
}