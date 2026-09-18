package com.neueda.app.repository;

import com.neueda.app.model.Instrument;
import java.util.List;
import java.util.Optional;

public interface InstrumentRepository {
    Optional<Instrument> findBySymbol(String symbol);
    List<Instrument> findAllTradable();
    void save(Instrument instrument);
    void update(Instrument instrument);
    void delete(String symbol);
}
