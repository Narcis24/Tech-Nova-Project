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
import com.neueda.app.services.OrderService;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            // STEP 4: Assert - Verify the response
            .andExpect(status().isOk());
    }

}