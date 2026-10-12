package com.neueda.app.repositories;

import com.neueda.app.models.Price;
import com.neueda.app.models.PriceKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PriceRepository extends JpaRepository<Price, PriceKey> {
    Optional<Price> findFirstBySymbolOrderByTradeDateDesc(String symbol);

    /** Latest bar for each of the given symbols, in one query. Symbols with no price data are absent. */
    @Query("""
        select p from Price p
        where p.symbol in :symbols
          and p.tradeDate = (select max(p2.tradeDate) from Price p2 where p2.symbol = p.symbol)
        """)
    List<Price> findLatestBySymbols(@Param("symbols") Collection<String> symbols);
}
