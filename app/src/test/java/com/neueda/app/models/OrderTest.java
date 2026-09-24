package com.neueda.app.models;

import com.neueda.app.enums.AccountStatus;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    private Order order(OrderSide side, OrderType type, String price) {
        Account account = new Account("12345", "Karl", "Devon", new BigDecimal("2000.00"),
            AccountStatus.ACTIVE, LocalDateTime.of(2026, 9, 17, 13, 0));
        Instrument instrument = new Instrument("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true);
        return new Order(UUID.randomUUID(), account, instrument, side, type, 10,
            new BigDecimal(price), "key", LocalDateTime.now());
    }

    @ParameterizedTest
    @CsvSource({
        "BUY,  LIMIT, 100.00,  99.99, true",
        "BUY,  LIMIT, 100.00, 100.00, true",
        "BUY,  LIMIT, 100.00, 100.01, false",
        "SELL, LIMIT, 100.00, 100.01, true",
        "SELL, LIMIT, 100.00, 100.00, true",
        "SELL, LIMIT, 100.00,  99.99, false",
        "BUY,  MARKET, 100.00, 500.00, true",
        "SELL, MARKET, 100.00,   1.00, true"
    })
    void testIsTriggeredBy(OrderSide side, OrderType type, String price, String marketPrice, boolean expected) {
        assertEquals(expected, order(side, type, price).isTriggeredBy(new BigDecimal(marketPrice)));
    }

    @ParameterizedTest
    @CsvSource({"150.005, 150.01", "150.004, 150.00", "150.1, 150.10"})
    void testPriceIsStoredAtTwoDecimalPlaces(String given, String stored) {
        assertEquals(new BigDecimal(stored), order(OrderSide.BUY, OrderType.LIMIT, given).getPrice());
    }

    @Test
    void testOrderTypeCannotBeNull() {
        assertThrows(IllegalArgumentException.class, () -> order(OrderSide.BUY, null, "100.00"));
    }
}
