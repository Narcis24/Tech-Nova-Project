package com.neueda.app.services;

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
import com.neueda.app.models.Price;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.InstrumentRepository;
import com.neueda.app.repositories.OrderRepository;
import com.neueda.app.repositories.PositionRepository;
import com.neueda.app.repositories.PriceRepository;
import com.neueda.app.services.OrderService;
import com.neueda.app.services.PriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

//  JPA EntityManager manages the DB conn and hanles
//      - Saving / Retrieving / Updating / Deleting 
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({OrderService.class, PriceService.class})
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
    private PriceRepository priceRepository;

    @Autowired
    private OrderService orderService;

    // Kafka publishing is out of scope here
    @MockBean
    private EventProducerService eventProducerService;

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
    @DisplayName("E2E: Cancel order workflow - Place -> Cancel -> Verify")
    void testCancelOrderWorkflow() {
        // Step 1: Place Order
        PlaceOrderRequest placeRequest = new PlaceOrderRequest(
            testAccount.getAccountId(),
            "AAPL",
            "BUY",
            "LIMIT",
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
            testAccount.getAccountId(), "AAPL", "BUY", "LIMIT", 100,
            new BigDecimal("150.00"), "get-order-001"
        );
        OrderResponse placeResponse = orderService.placeOrder(request);

        // Retrieve order
        OrderResponse getResponse = orderService.getOrder(placeResponse.getOrderId());
        assertNotNull(getResponse);
        assertEquals(placeResponse.getOrderId(), getResponse.getOrderId());
        assertEquals(OrderStatus.PENDING, getResponse.getStatus());
    }

}