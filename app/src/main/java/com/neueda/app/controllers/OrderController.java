
package com.neueda.app.controllers;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import com.neueda.app.dtos.OrderResponse;
import com.neueda.app.dtos.PlaceOrderRequest;
import com.neueda.app.services.OrderService;
import java.util.UUID;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/v1/orders")

public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

   @PostMapping
   public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest orderRequest) {
       OrderResponse orderResponse = orderService.placeOrder(orderRequest);
       return ResponseEntity.ok(orderResponse);
   }


   @DeleteMapping("/{orderId}")
   public ResponseEntity<OrderResponse> cancelOrder(@PathVariable String orderId) {
       OrderResponse orderResponse = orderService.cancelOrder(UUID.fromString(orderId));
       return ResponseEntity.ok(orderResponse);
   }

   @PostMapping("/execute/{orderId}")
   public ResponseEntity<OrderResponse> executeOrder(@PathVariable String orderId) {
       OrderResponse orderResponse = orderService.executeOrder(UUID.fromString(orderId));
       return ResponseEntity.ok(orderResponse);
   }
}