package com.neueda.app.repositories;

import com.neueda.app.models.Instrument;
import java.util.List;
import java.util.Optional;

public interface InstrumentRepository {
    Optional<Instrument> findBySymbol(String symbol);
    List<Instrument> findAllTradable();
    void save(Instrument instrument);
    void update(Instrument instrument);
    void delete(String symbol);
}
