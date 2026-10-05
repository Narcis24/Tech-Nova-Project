package com.neueda.app.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class PlaceOrderRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean symbolIsValid(String symbol) {
        PlaceOrderRequest request =
            new PlaceOrderRequest("ACC001", symbol, "BUY", "LIMIT", 1, new BigDecimal("10.00"), "key");
        return validator.validateProperty(request, "symbol").isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"V", "KO", "MA", "AAPL", "BRK-B"})
    void realInstrumentSymbolsAreAccepted(String symbol) {
        assertTrue(symbolIsValid(symbol));
    }

    @Test
    void emptyOrOverlongSymbolsAreRejected() {
        assertEquals(false, symbolIsValid(""));
        assertEquals(false, symbolIsValid("ABCDEFGHIJK"));
    }
}
