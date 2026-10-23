package com.neueda.app.repositories;

import com.neueda.app.models.InstrumentFinancial;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InstrumentFinancialRepository extends JpaRepository<InstrumentFinancial, InstrumentFinancial.Key> {
    /** Every stored period for the symbol, oldest first within each period type. */
    List<InstrumentFinancial> findBySymbolOrderByPeriodTypeAscPeriodEndAsc(String symbol);
}
