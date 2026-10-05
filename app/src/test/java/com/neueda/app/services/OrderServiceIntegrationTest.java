// package com.neueda.app.services;

// import com.neueda.app.dtos.PlaceOrderRequest;
// import com.neueda.app.dtos.OrderResponse;
// import com.neueda.app.enums.AccountStatus;
// import com.neueda.app.enums.AssetClass;
// import com.neueda.app.enums.OrderSide;
// import com.neueda.app.enums.OrderStatus;
// import com.neueda.app.enums.OrderType;
// import com.neueda.app.models.Account;
// import com.neueda.app.models.Instrument;
// import com.neueda.app.models.Order;
// import com.neueda.app.repositories.AccountRepository;
// import com.neueda.app.repositories.InstrumentRepository;
// import com.neueda.app.repositories.OrderRepository;
// import com.neueda.app.repositories.PositionRepository;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.context.SpringBootTest;
// import org.springframework.boot.test.mock.mockito.MockBean;
// import org.springframework.kafka.test.context.EmbeddedKafka;
// import org.springframework.test.context.ActiveProfiles;
// import org.springframework.test.context.TestPropertySource;

// import java.math.BigDecimal;
// import java.time.LocalDateTime;
// import java.util.UUID;
// import java.util.concurrent.TimeUnit;

// import static org.junit.jupiter.api.Assertions.*;
// import static org.awaitility.Awaitility.await;
// import static org.mockito.ArgumentMatchers.anyString;
// import static org.mockito.Mockito.when;

// import org.springframework.kafka.test.EmbeddedKafkaBroker;
// import org.springframework.test.context.TestPropertySource;


// /**
//  * End-to-End Integration Test for Order Processing Workflow
//  * 
//  * Tests the complete flow:
//  * 1. OrderService.placeOrder() creates PENDING order, publishes to "trades"
//  * 2. ExecutionEngine consumes from "trades", executes, publishes to "tradeEvents"
//  * 3. OrderService consumes from "tradeEvents", updates order to FILLED
//  * 4. Verifies order status, execution price, and position updates
//  */
// @SpringBootTest
// @EmbeddedKafka(
//     partitions = 1,
//     topics = { "trades", "tradeEvents" }
// )
// @ActiveProfiles("test")
// class OrderServiceIntegrationTest {

//     @Autowired
//     private OrderService orderService;

//     @Autowired
//     private ExecutionEngine executionEngine;

//     @Autowired
//     private OrderRepository orderRepository;

//     @Autowired
//     private AccountRepository accountRepository;

//     @Autowired
//     private InstrumentRepository instrumentRepository;

//     @Autowired
//     private PositionRepository positionRepository;

//     @MockBean
//     private PriceService priceService;

//     private Account testAccount;
//     private Instrument testInstrument;

//     @BeforeEach
//     void setUp() {
//         // Mock PriceService to return 150.00 for any symbol
//         when(priceService.getCurrentPrice(anyString())).thenReturn(new BigDecimal("150.00"));

//         // Create test account
//         testAccount = new Account(
//             "test-account-123",
//             "Test User",
//             new BigDecimal("5000.00"),
//             AccountStatus.ACTIVE,
//             LocalDateTime.now()
//         );
//         testAccount = accountRepository.save(testAccount);

//         // Create test instrument
//         testInstrument = new Instrument(
//             "TEST",
//             "Test Company",
//             AssetClass.EQUITY,
//             "USD",
//             true
//         );
//         testInstrument = instrumentRepository.save(testInstrument);
//     }

//     @Test
//     void testCompleteWorkflowPlaceMarketOrderThroughExecution() throws InterruptedException {
//         // Arrange
//         BigDecimal marketPrice = new BigDecimal("150.00");
        
//         PlaceOrderRequest request = new PlaceOrderRequest(
//             testAccount.getAccountId(),
//             testInstrument.getSymbol(),
//             "BUY",
//             "MARKET",
//             10,
//             null,  // MARKET orders have no price
//             UUID.randomUUID().toString()
//         );

//         // Act: Place order (publishes to "trades")
//         OrderResponse placeResponse = orderService.placeOrder(request);
//         UUID orderId = placeResponse.getOrderId();

//         // Assert: Order should be PENDING immediately after placement
//         assertEquals(OrderStatus.PENDING, placeResponse.getStatus());
//         assertNull(placeResponse.getPrice(), "MARKET orders should have null price until execution");

//         // Wait for ExecutionEngine to consume and process
//         // (In real scenario with Kafka, this would be asynchronous)
//         await()
//             .atMost(60, TimeUnit.SECONDS)
//             .pollDelay(100, TimeUnit.MILLISECONDS)
//             .until(() -> {
//                 Order order = orderRepository.findById(orderId).orElse(null);
//                 return order != null && order.getStatus() == OrderStatus.FILLED;
//             });

//         // Assert: Order should be FILLED after ExecutionEngine processes
//         Order executedOrder = orderRepository.findById(orderId)
//             .orElseThrow(() -> new AssertionError("Order not found: " + orderId));

//         assertEquals(OrderStatus.FILLED, executedOrder.getStatus());
//         assertNotNull(executedOrder.getPrice(), "Executed order should have execution price");
//         assertEquals(new BigDecimal("150.00"), executedOrder.getPrice());

//         // Verify account cash was debited
//         Account updatedAccount = accountRepository.findById(testAccount.getAccountId())
//             .orElseThrow(() -> new AssertionError("Account not found"));
//         // 5000 - (150 * 10) = 5000 - 1500 = 3500
//         assertEquals(new BigDecimal("3500.00"), updatedAccount.getCashBalance());

//         // Verify position was created/updated
//         assertTrue(positionRepository.findByAccountIdAndSymbol(
//             testAccount.getAccountId(),
//             testInstrument.getSymbol()
//         ).isPresent(), "Position should be created for BUY order");

//         var position = positionRepository.findByAccountIdAndSymbol(
//             testAccount.getAccountId(),
//             testInstrument.getSymbol()
//         ).get();
//         assertEquals(10, position.getQuantity());
//         assertEquals(new BigDecimal("150.00"), position.getAverageCost());
//     }

//     @Test
//     void testCompleteWorkflowPlaceLimitOrderNotTriggered() throws InterruptedException {
//         // Arrange: Place LIMIT order with price 100, but market price will be 150
//         BigDecimal limitPrice = new BigDecimal("100.00");
        
//         PlaceOrderRequest request = new PlaceOrderRequest(
//             testAccount.getAccountId(),
//             testInstrument.getSymbol(),
//             "BUY",
//             "LIMIT",
//             5,
//             limitPrice,
//             UUID.randomUUID().toString()
//         );

//         // Act: Place order
//         OrderResponse placeResponse = orderService.placeOrder(request);
//         UUID orderId = placeResponse.getOrderId();

//         // Assert: Order should be PENDING with limit price set
//         assertEquals(OrderStatus.PENDING, placeResponse.getStatus());
//         assertEquals(limitPrice, placeResponse.getPrice());

//         // Wait a bit to see if ExecutionEngine processes it
//         // (With market price 150, limit price 100 should NOT trigger for BUY)
//         Thread.sleep(500);

//         // Order should still be PENDING (not executed)
//         Order orderAfterWait = orderRepository.findById(orderId)
//             .orElseThrow(() -> new AssertionError("Order not found"));
//         assertEquals(OrderStatus.PENDING, orderAfterWait.getStatus());

//         // Account cash should NOT be debited
//         Account account = accountRepository.findById(testAccount.getAccountId()).get();
//         assertEquals(new BigDecimal("5000.00"), account.getCashBalance());
//     }

    // @Test
    // void testCompleteWorkflowPlaceLimitOrderTriggered() throws InterruptedException {
    //     // Arrange: Place LIMIT BUY order with price 200, market price is 150 (should trigger)
    //     BigDecimal limitPrice = new BigDecimal("200.00");
        
    //     PlaceOrderRequest request = new PlaceOrderRequest(
    //         testAccount.getAccountId(),
    //         testInstrument.getSymbol(),
    //         "BUY",
    //         "LIMIT",
    //         5,
    //         limitPrice,
    //         UUID.randomUUID().toString()
    //     );

    //     // Act: Place order
    //     OrderResponse placeResponse = orderService.placeOrder(request);
    //     UUID orderId = placeResponse.getOrderId();

    //     assertEquals(OrderStatus.PENDING, placeResponse.getStatus());
    //     assertEquals(limitPrice, placeResponse.getPrice());

    //     // Wait for ExecutionEngine to process (should execute because market 150 <= limit 200)
    //     await()
    //         .atMost(10, TimeUnit.SECONDS)
    //         .pollDelay(100, TimeUnit.MILLISECONDS)
    //         .until(() -> {
    //             Order order = orderRepository.findById(orderId).orElse(null);
    //             return order != null && order.getStatus() == OrderStatus.FILLED;
    //         });

    //     // Assert: Order should be FILLED at market price (150), not limit price (200)
    //     Order executedOrder = orderRepository.findById(orderId).get();
    //     assertEquals(OrderStatus.FILLED, executedOrder.getStatus());
    //     assertEquals(new BigDecimal("150.00"), executedOrder.getPrice(), 
    //         "Should execute at market price, not limit price");

    //     // Account cash should be debited at execution price (150 * 5 = 750)
    //     Account account = accountRepository.findById(testAccount.getAccountId()).get();
    //     assertEquals(new BigDecimal("4250.00"), account.getCashBalance());
    // }
// }
