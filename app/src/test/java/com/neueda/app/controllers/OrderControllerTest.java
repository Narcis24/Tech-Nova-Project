package com.neueda.app.controllers;

import com.neueda.app.services.AccountAccess;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.app.dtos.OrderResponse;
import com.neueda.app.dtos.PlaceOrderRequest;
import com.neueda.app.exceptions.OrderNotFoundException;
import com.neueda.app.services.OrderService;

import com.neueda.app.utils.JwtUtil;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    @Autowired 
    private MockMvc mockMvc;  // Simulates HTTP requests

    @Autowired
    private ObjectMapper objectMapper;  // Converts Java objects to JSON

    @MockBean
    private AccountAccess accountAccess;  // permits everything; ownership is tested in AccountAccessTest

    @MockBean
    private OrderService orderService;  // Fake service (no database needed)

    @MockBean
    private JwtUtil jwtUtil;  // Required by JwtFilter, which is picked up in the web slice

    @Test 
    void testPlaceOrder_Success() throws Exception {
        // STEP 1: Setup - Create input data
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setAccountId("ACC123");
        request.setSymbol("AAPL");
        request.setSide("BUY");
        request.setOrderType("LIMIT");
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

    @Test
    void testGetOrder_Success() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderResponse mockResponse = new OrderResponse(
            orderId, "ACC123", "AAPL", "BUY", null, 100,
            new BigDecimal("150.00"), null, null
        );

        when(orderService.getOrder(orderId)).thenReturn(mockResponse);

        mockMvc.perform(get("/v1/orders/{orderId}", orderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value(orderId.toString()));
    }

    @Test
    void testGetOrder_NotFound() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrder(orderId))
            .thenThrow(new OrderNotFoundException("Order not found: " + orderId));

        mockMvc.perform(get("/v1/orders/{orderId}", orderId))
            .andExpect(status().isNotFound());
    }

}