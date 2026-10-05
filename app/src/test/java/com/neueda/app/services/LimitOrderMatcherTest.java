package com.neueda.app.services;

import com.neueda.app.enums.AccountStatus;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.enums.OrderType;
import com.neueda.app.models.Account;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.Order;
import com.neueda.app.repositories.OrderRepository;
import com.neueda.app.repositories.PriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@Disabled("LimitOrderMatcher is deprecated. Limit order matching is now handled by the execution-engine. " +
          "The matcher is kept as a no-op for backward compatibility.")
class LimitOrderMatcherTest {

    private OrderRepository orderRepository;
    private PriceRepository priceRepository;
    private LimitOrderMatcher matcher;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        priceRepository = mock(PriceRepository.class);
        matcher = new LimitOrderMatcher(orderRepository, priceRepository);
    }

    @Test
    void testMatcherIsNowNoOp() {
        // The matcher is deprecated and does nothing.
        // All limit order matching is now handled by the execution-engine.
        
        matcher.matchPendingLimitOrders();
        
        // Verify no database operations occur
        verifyNoInteractions(orderRepository);
        verifyNoInteractions(priceRepository);
    }
}
