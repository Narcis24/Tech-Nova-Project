package com.neueda.app.repository;

import com.neueda.app.model.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface InstrumentRepository extends JpaRepository<Instrument, String> {
    Optional<Instrument> findBySymbol(String symbol);
    List<Instrument> findAllTradable();
}
