package com.neueda.app.repositories;

import com.neueda.app.models.Price;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PriceRepository extends JpaRepository<Price, String> {
    Optional<Price> findBySymbol(String symbol);
}