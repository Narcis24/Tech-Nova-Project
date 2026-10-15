package com.neueda.app.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MarketAssetResponse(String symbol, String name, String assetClass, String currency,
                                  BigDecimal currentPrice, BigDecimal previousClose,
                                  BigDecimal change, BigDecimal changePercent,
                                  LocalDate priceDate, List<BigDecimal> history) {}
