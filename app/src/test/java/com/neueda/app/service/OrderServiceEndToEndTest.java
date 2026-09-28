package com.neueda.app.service;

import com.neueda.app.dtos.OrderResponse;
import com.neueda.app.dtos.PlaceOrderRequest;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.enums.AccountStatus;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.models.Account;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.Order;
import com.neueda.app.models.Position;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.InstrumentRepository;
import com.neueda.app.repositories.OrderRepository;
import com.neueda.app.repositories.PositionRepository;
import com.neueda.app.services.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

//  JPA EntityManager manages the DB conn and hanles
//      - Saving / Retrieving / Updating / Deleting 
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(OrderService.class)
// Uses application-test.properties imports for Integration Testing
@ActiveProfiles("test")
@DisplayName("OrderService End-to-End Integration Tests")
public class OrderServiceEndToEndTest {

    // Injects a connection to the repos
    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private OrderService orderService;

    private Account testAccount;
    private Instrument testInstrument;

    @BeforeEach
    void setUp() {
        // Create test account
        testAccount = new Account(
            UUID.randomUUID().toString(),
            "Test Trader",
            new BigDecimal("50000.00"),
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );
        accountRepository.save(testAccount);

        // Create test instrument
        testInstrument = new Instrument(
            "AAPL",
            "Apple Inc.",
            AssetClass.EQUITY,
            "USD",
            true
        );
        instrumentRepository.save(testInstrument);

        entityManager.flush();
    }

    // ==================== WORKFLOW TESTS ====================

    @Test
    @DisplayName("E2E: Complete BUY workflow - Place -> Execute -> Verify")
    void testCompleteBuyWorkflow() {
        // 1. Place Order
        PlaceOrderRequest placeRequest = new PlaceOrderRequest(
            testAccount.getAccountId(),
            "AAPL",
            "BUY",
            100,
            new BigDecimal("150.00"),
            "buy-order-001"
        );

        OrderResponse response = orderService.placeOrder(placeRequest);
        assertNotNull(response.getAccountId());

        UUID orderID = response.getOrderId();

        // 2. Execute Order 
        OrderResponse executeResponse = orderService.executeOrder(orderID);
        assertNotNull(executeResponse);
        assertEquals(OrderStatus.FILLED, executeResponse.getStatus());

        // 3. Verify Account Balance Changes
        Account updatedAccountBalance = accountRepository.findById(testAccount.getAccountId()).orElseThrow();
        BigDecimal expectedBalance = new BigDecimal("50000.00")
            .subtract(new BigDecimal("150.00").multiply(new BigDecimal("100")));
        assertEquals(expectedBalance, updatedAccountBalance.getCashBalance());

        // 4. Verify Position has been created.
        Position position = positionRepository.findByAccountIdAndSymbol(testAccount.getAccountId(), "AAPL")
                                        .orElseThrow();
        
        assertEquals(100, position.getQuantity());
        assertEquals(new BigDecimal("150.00"), position.getAverageCost());                     

    }

    @Test
    @DisplayName("E2E: Complete SELL workflow - Place -> Execute -> Verify")
    void testCompleteSellWorkflow() {
        // Setup: Create initial position
        Position initialPosition = new Position(
            testAccount,
            testInstrument,
            100,
            new BigDecimal("150.00")
        );
        positionRepository.save(initialPosition);
        entityManager.flush();

        // Step 1: Place Sell Order
        PlaceOrderRequest sellRequest = new PlaceOrderRequest(
            testAccount.getAccountId(),
            "AAPL",
            "SELL",
            50,
            new BigDecimal("160.00"),
            "sell-order-001"
        );

        OrderResponse placeResponse = orderService.placeOrder(sellRequest);
        UUID orderId = placeResponse.getOrderId();

        OrderResponse executeResponse = orderService.executeOrder(orderId);
        assertEquals(OrderStatus.FILLED, executeResponse.getStatus());

        Account updatedAccount = accountRepository.findById(testAccount.getAccountId()).orElseThrow();
        BigDecimal expectedBalance = new BigDecimal("50000.00")
            .add(new BigDecimal("160.00").multiply(new BigDecimal("50")));
        assertEquals(expectedBalance, updatedAccount.getCashBalance());

        Position updatedPosition = positionRepository
            .findByAccountIdAndSymbol(testAccount.getAccountId(), "AAPL")
            .orElseThrow();
        assertEquals(50, updatedPosition.getQuantity());
    }

    @Test
    @DisplayName("E2E: Cancel order workflow - Place -> Cancel -> Verify")
    void testCancelOrderWorkflow() {
        // Step 1: Place Order
        PlaceOrderRequest placeRequest = new PlaceOrderRequest(
            testAccount.getAccountId(),
            "AAPL",
            "BUY",
            50,
            new BigDecimal("150.00"),
            "cancel-order-001"
        );

        OrderResponse response = orderService.placeOrder(placeRequest);
        UUID orderId = response.getOrderId();

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.PENDING, order.getStatus());

        OrderResponse cancelOrder = orderService.cancelOrder(orderId);
        assertEquals(OrderStatus.CANCELLED, cancelOrder.getStatus());

        // DB Verification
        Order cancelledOrderInDB = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, cancelledOrderInDB.getStatus());

        // Step 5: Verify Account Balance remains the same
        Account account = accountRepository.findById(testAccount.getAccountId()).orElseThrow();
        assertEquals(new BigDecimal("50000.00"), account.getCashBalance());
    }

    @Test
    @DisplayName("E2E: Get order details")
    void testGetOrderDetails() {
        // Place order
        PlaceOrderRequest request = new PlaceOrderRequest(
            testAccount.getAccountId(), "AAPL", "BUY", 100,
            new BigDecimal("150.00"), "get-order-001"
        );
        OrderResponse placeResponse = orderService.placeOrder(request);

        // Retrieve order
        OrderResponse getResponse = orderService.getOrder(placeResponse.getOrderId());
        assertNotNull(getResponse);
        assertEquals(placeResponse.getOrderId(), getResponse.getOrderId());
        assertEquals(OrderStatus.PENDING, getResponse.getStatus());
    }

    // ================= TESTING POSITION AVERAGE =================
    @Test
    @DisplayName("E2E: Verify Position Average")
    void testMultipleBuyOrders() {
        // Buy order 1
        PlaceOrderRequest request1 = new PlaceOrderRequest(
            testAccount.getAccountId(), "AAPL", "BUY", 50,
            new BigDecimal("150.00"), "buy-001"
        );
        OrderResponse response1 = orderService.placeOrder(request1);
        orderService.executeOrder(response1.getOrderId());

        // Buy order 2
        PlaceOrderRequest request2 = new PlaceOrderRequest(
            testAccount.getAccountId(), "AAPL", "BUY", 30,
            new BigDecimal("155.00"), "buy-002"
        );
        OrderResponse response2 = orderService.placeOrder(request2);
        orderService.executeOrder(response2.getOrderId());

        // Verify position averaged
        Position position = positionRepository
            .findByAccountIdAndSymbol(testAccount.getAccountId(), "AAPL")
            .orElseThrow();
        assertEquals(80, position.getQuantity());
    }
}