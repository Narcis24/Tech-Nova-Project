package com.neueda.app.repositories;

import com.neueda.app.models.Price;
import java.math.BigDecimal;
import java.util.Optional;

public interface PriceRepository {
    Optional<Price> findBySymbol(String symbol);
    Optional<BigDecimal> findLatestPrice(String symbol);
    void save(Price price);
    void update(Price price);
    void delete(String symbol);
}
