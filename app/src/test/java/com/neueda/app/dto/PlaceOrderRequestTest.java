package com.neueda.app.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Set;
import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

public class PlaceOrderRequestTest {

    private Validator validator;

    @BeforeEach
    public void setUp() {
        ValidatorFactory factory = Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    public void validPlaceOrderRequestTest() {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setAccountID(1L);
        request.setSymbol("AAPL");
        request.setSide("BUY");
        request.setQuantity(100);
        request.setPrice(new BigDecimal("150.00"));
        request.setIdempotency_key("key-123");

        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(request);
    
        assertTrue(violations.isEmpty());
    }

    @Test
    public void nullAccountIdTest() {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setAccountID(null);
        request.setSymbol("AAPL");
        request.setSide("BUY");
        request.setQuantity(100);
        request.setPrice(new BigDecimal("150.00"));

        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("Account ID required")));
    }

    @Test 
    public void nullSymbolTest() {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setAccountID(1234L);
        request.setSymbol(null);
        request.setSide("SELL");
        request.setQuantity(200);
        request.setPrice(new BigDecimal("250.00"));

        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Symbol required")));
    }

    @Test 
    public void nullSideTest() {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setAccountID(3456L);
        request.setSymbol("AAPL");
        request.setSide(null);
        request.setQuantity(500);
        request.setPrice(new BigDecimal("550.00"));

        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Must be BUY or SELL")));
    }

    @Test
    public void invalidSidePatternTest() {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setAccountID(123456L);
        request.setSymbol("AAPL");
        request.setSide("HOLD");  
        request.setQuantity(500);
        request.setPrice(new BigDecimal("550.00"));

        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Must be BUY or SELL")));
    }

    @Test 
    public void nullQuantityTest() {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setAccountID(1256L);
        request.setSymbol("AAPL");
        request.setSide("SELL");
        request.setQuantity(null);
        request.setPrice(new BigDecimal("250.00"));

        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Quantity must be positive")));
    }

    @Test 
    public void negativePriceTest() {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setAccountID(123456L);
        request.setSymbol("APPL");
        request.setSide("SELL");
        request.setQuantity(200);
        request.setPrice(new BigDecimal("-250.00"));

        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Minimal price is 0.01")));
    }
}
