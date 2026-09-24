package com.neueda.app.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.app.dtos.OrderResponse;
import com.neueda.app.dtos.PlaceOrderRequest;
import com.neueda.app.exceptions.*;
import com.neueda.app.services.OrderService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired 
    private MockMvc mockMvc;  // Simulates HTTP requests

    @Autowired
    private ObjectMapper objectMapper;  // Converts Java objects to JSON

    @MockBean
    private OrderService orderService;  // Fake service (no database needed)

    @Test 
    void testPlaceOrder_Success() throws Exception {
        // STEP 1: Setup - Create input data
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setAccountId("ACC123");
        request.setSymbol("AAPL");
        request.setSide("BUY");
        request.setQuantity(100);
        request.setPrice(new BigDecimal("150.50"));
        request.setIdempotencyKey(UUID.randomUUID().toString());

        // STEP 2: Setup - Tell the mock service what to return
        OrderResponse mockResponse = new OrderResponse();
        
        when(orderService.placeOrder(any(PlaceOrderRequest.class)))
            .thenReturn(mockResponse);

        // STEP 3: Act - Make the HTTP POST request
        mockMvc.perform(post("/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            // STEP 4: Assert - Verify the response
            .andExpect(status().isOk());
    }

    static Stream<Arguments> exceptionToStatus() {
        return Stream.of(
            Arguments.of(new OrderNotFoundException("no order"), 404),
            Arguments.of(new AccountNotFoundException("no account"), 404),
            Arguments.of(new InstrumentNotFoundException("no instrument"), 404),
            Arguments.of(new InvalidOrderStateException("not pending"), 409),
            Arguments.of(new DuplicateOrderException("duplicate key"), 409),
            Arguments.of(new InsufficientFundsException("no funds"), 422),
            Arguments.of(new InsufficientHoldingsException("no holdings"), 422),
            Arguments.of(new AccountNotActiveException("suspended"), 422),
            Arguments.of(new TradingException("not tradable"), 422),
            Arguments.of(new IllegalArgumentException("bad side"), 400)
        );
    }

    @ParameterizedTest
    @MethodSource("exceptionToStatus")
    void testExceptionsMapToHttpStatus(RuntimeException thrown, int expectedStatus) throws Exception {
        when(orderService.placeOrder(any(PlaceOrderRequest.class))).thenThrow(thrown);

        mockMvc.perform(post("/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PlaceOrderRequest())))
            .andExpect(status().is(expectedStatus))
            .andExpect(jsonPath("$.status").value(expectedStatus))
            .andExpect(jsonPath("$.message").value(thrown.getMessage()));
    }

}
