package com.neueda.app.configs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.app.controllers.OrderController;
import com.neueda.app.dtos.PlaceOrderRequest;
import com.neueda.app.services.OrderService;
import com.neueda.app.exceptions.AccountNotFoundException;
import com.neueda.app.exceptions.AccountNotActiveException;
import com.neueda.app.exceptions.DuplicateOrderException;
import com.neueda.app.exceptions.InstrumentNotFoundException;
import com.neueda.app.exceptions.InsufficientFundsException;
import com.neueda.app.exceptions.InsufficientHoldingsException;
import com.neueda.app.exceptions.InvalidOrderStateException;
import com.neueda.app.exceptions.OrderNotFoundException;
import org.springframework.dao.OptimisticLockingFailureException;
import com.neueda.app.exceptions.TradingException;
import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


@WebMvcTest(OrderController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @Test
    void testValidationErrors() throws Exception {
        PlaceOrderRequest request = new PlaceOrderRequest(
            "",
            "AAPL",
            "BUY",
            "LIMIT",
            100,
            new BigDecimal("150.00"),
            "idempotency-123"
        );

        mockMvc.perform(post("/v1/orders")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andDo(result -> {
                System.out.println("\ntestValidationErrors");
                System.out.println(result.getResponse().getContentAsString());
            })
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.httpStatus").value(400))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "Empty accountId, 'accountId: Account ID is required', '', AAPL, BUY, LIMIT, 100, 150.00, idempotency-123",
        "Empty symbol, 'symbol: Symbol is required', ACC123, '', BUY, LIMIT, 100, 150.00, idempotency-123",
        "Empty side, 'side: Side must be BUY or SELL', ACC123, AAPL, '', LIMIT, 100, 150.00, idempotency-123",
        "Empty order type, 'orderType: Order type must be MARKET or LIMIT', ACC123, AAPL, BUY, '', 100, 150.00, idempotency-123",
        "Null quantity, 'quantity: Quantity is required', ACC123, AAPL, BUY, LIMIT, , 150.00, idempotency-123",
        "Negative quantity, 'quantity: Quantity must be positive', ACC123, AAPL, BUY, LIMIT, -50, 150.00, idempotency-123",
        "Zero quantity, 'quantity: Quantity must be positive', ACC123, AAPL, BUY, LIMIT, 0, 150.00, idempotency-123",
        "Negative price, 'price: Price must be positive', ACC123, AAPL, BUY, LIMIT, 100, -50.00, idempotency-123",
        "Zero price, 'price: Price must be positive', ACC123, AAPL, BUY, LIMIT, 100, 0.00, idempotency-123",
        "Empty idempotency key, 'idempotencyKey: Idempotency key is required', ACC123, AAPL, BUY, LIMIT, 100, 150.00, ''"
    })
    void validationErrorsForPlaceOrderRequestTEst(String testCase, String expectedMessage, String accountId, String symbol, String side, String orderType,
                                          Integer quantity, String priceStr, String idempotencyKey) throws Exception {
        BigDecimal price = priceStr == null || priceStr.isEmpty() ? null : new BigDecimal(priceStr);
        
        PlaceOrderRequest request = new PlaceOrderRequest(accountId, symbol, side, orderType, quantity, price, idempotencyKey);
        
        mockMvc.perform(post("/v1/orders")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.httpStatus").value(400))
            .andExpect(jsonPath("$.message").value(expectedMessage))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    // ============ Tests for Custom Exceptions ============

    @Test
    void testAccountNotFoundException() throws Exception {
        when(orderService.placeOrder(any())).thenThrow(
            new AccountNotFoundException("Account ACC123 not found")
        );

        PlaceOrderRequest request = new PlaceOrderRequest("ACC123", "AAPL", "BUY", "LIMIT", 100, new BigDecimal("150.00"), "id-123");

        mockMvc.perform(post("/v1/orders")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andDo(result -> {
                System.out.println("\ntestAccountNotFoundException");
                System.out.println(result.getResponse().getContentAsString());
            })
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.httpStatus").value(404))
            .andExpect(jsonPath("$.message").value("Account ACC123 not found"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testAccountNotActiveException() throws Exception {
        when(orderService.placeOrder(any())).thenThrow(
            new AccountNotActiveException("Account is not in ACTIVE status")
        );

        PlaceOrderRequest request = new PlaceOrderRequest("ACC123", "AAPL", "BUY", "LIMIT", 100, new BigDecimal("150.00"), "id-123");

        mockMvc.perform(post("/v1/orders")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andDo(result -> {
                System.out.println("\ntestAccountNotActiveException");
                System.out.println(result.getResponse().getContentAsString());
            })
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.httpStatus").value(400))
            .andExpect(jsonPath("$.message").value("Account is not in ACTIVE status"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testDuplicateOrderException() throws Exception {
        when(orderService.placeOrder(any())).thenThrow(
            new DuplicateOrderException("Order with this idempotency key already exists")
        );

        PlaceOrderRequest request = new PlaceOrderRequest("ACC123", "AAPL", "BUY", "LIMIT", 100, new BigDecimal("150.00"), "id-123");

        mockMvc.perform(post("/v1/orders")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.httpStatus").value(409))
            .andExpect(jsonPath("$.message").value("Order with this idempotency key already exists"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testInstrumentNotFoundException() throws Exception {
        when(orderService.placeOrder(any())).thenThrow(
            new InstrumentNotFoundException("Symbol UNKNOWN not found in market data")
        );

        PlaceOrderRequest request = new PlaceOrderRequest("ACC123", "UNKNOWN", "BUY", "LIMIT", 100, new BigDecimal("150.00"), "id-123");

        mockMvc.perform(post("/v1/orders")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.httpStatus").value(404))
            .andExpect(jsonPath("$.message").value("Symbol UNKNOWN not found in market data"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testInsufficientFundsException() throws Exception {
        when(orderService.placeOrder(any())).thenThrow(
            new InsufficientFundsException("Insufficient funds to complete BUY order")
        );

        PlaceOrderRequest request = new PlaceOrderRequest("ACC123", "AAPL", "BUY", "LIMIT", 1000, new BigDecimal("500.00"), "id-123");

        mockMvc.perform(post("/v1/orders")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.httpStatus").value(400))
            .andExpect(jsonPath("$.message").value("Insufficient funds to complete BUY order"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testInsufficientHoldingsException() throws Exception {
        when(orderService.placeOrder(any())).thenThrow(
            new InsufficientHoldingsException("Insufficient holdings to complete SELL order")
        );

        PlaceOrderRequest request = new PlaceOrderRequest("ACC123", "AAPL", "SELL", "LIMIT", 500, new BigDecimal("150.00"), "id-123");

        mockMvc.perform(post("/v1/orders")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.httpStatus").value(400))
            .andExpect(jsonPath("$.message").value("Insufficient holdings to complete SELL order"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testOrderNotFoundException() throws Exception {
        when(orderService.placeOrder(any())).thenThrow(
            new OrderNotFoundException("Order not found: 123")
        );

        PlaceOrderRequest request = new PlaceOrderRequest("ACC123", "AAPL", "BUY", "LIMIT", 100, new BigDecimal("150.00"), "id-123");

        mockMvc.perform(post("/v1/orders")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"))
            .andExpect(jsonPath("$.httpStatus").value(404))
            .andExpect(jsonPath("$.message").value("Order not found: 123"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testInvalidOrderStateException() throws Exception {
        when(orderService.placeOrder(any())).thenThrow(
            new InvalidOrderStateException("Only PENDING orders can be modified")
        );

        PlaceOrderRequest request = new PlaceOrderRequest("ACC123", "AAPL", "BUY", "LIMIT", 100, new BigDecimal("150.00"), "id-123");

        mockMvc.perform(post("/v1/orders")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("INVALID_ORDER_STATE"))
            .andExpect(jsonPath("$.httpStatus").value(409))
            .andExpect(jsonPath("$.message").value("Only PENDING orders can be modified"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testOptimisticLockingFailure() throws Exception {
        when(orderService.placeOrder(any())).thenThrow(
            new OptimisticLockingFailureException("stale")
        );

        PlaceOrderRequest request = new PlaceOrderRequest("ACC123", "AAPL", "BUY", "LIMIT", 100, new BigDecimal("150.00"), "id-123");

        mockMvc.perform(post("/v1/orders")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("CONCURRENT_UPDATE"))
            .andExpect(jsonPath("$.httpStatus").value(409))
            .andExpect(jsonPath("$.message").value("Concurrent update, please retry"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testTradingException() throws Exception {
        when(orderService.placeOrder(any())).thenThrow(
            new TradingException("Trading operation failed")
        );

        PlaceOrderRequest request = new PlaceOrderRequest("ACC123", "AAPL", "BUY", "LIMIT", 100, new BigDecimal("150.00"), "id-123");

        mockMvc.perform(post("/v1/orders")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.httpStatus").value(400))
            .andExpect(jsonPath("$.message").value("Trading operation failed"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testIllegalArgumentException() throws Exception {
        when(orderService.placeOrder(any())).thenThrow(
            new IllegalArgumentException("Invalid side value provided")
        );

        PlaceOrderRequest request = new PlaceOrderRequest("ACC123", "AAPL", "BUY", "LIMIT", 100, new BigDecimal("150.00"), "id-123");

        mockMvc.perform(post("/v1/orders")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.httpStatus").value(400))
            .andExpect(jsonPath("$.message").value("Invalid side value provided"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testGenericException() throws Exception {
        when(orderService.placeOrder(any())).thenThrow(
            new RuntimeException("Unexpected internal error")
        );

        PlaceOrderRequest request = new PlaceOrderRequest("ACC123", "AAPL", "BUY", "LIMIT", 100, new BigDecimal("150.00"), "id-123");

        mockMvc.perform(post("/v1/orders")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.httpStatus").value(500))
            .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
            .andExpect(jsonPath("$.timestamp").exists());
    }
}