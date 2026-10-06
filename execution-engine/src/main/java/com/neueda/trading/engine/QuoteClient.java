package com.neueda.trading.engine;

import java.util.List;

/** Source of live quotes. One call fetches a batch of symbols. */
public interface QuoteClient {

    List<Quote> latest(List<String> symbols);
}
