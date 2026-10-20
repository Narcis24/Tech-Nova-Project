package com.neueda.app.repositories;

import com.neueda.app.enums.AccountStatus;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.enums.OrderType;
import com.neueda.app.models.Account;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** OrderRepository.findActivity against a real (H2) database. */
@DataJpaTest
@ActiveProfiles("test")
class OrderRepositoryActivityTest {

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 10, 19, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 10, 20, 0, 0);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    private Account alice;
    private Account unclaimed;
    private Instrument apple;
    private Instrument gold;

    @BeforeEach
    void setUp() {
        apple = instrumentRepository.save(new Instrument("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true));
        gold = instrumentRepository.save(new Instrument("GLD", "SPDR Gold Shares", AssetClass.COMMODITY, "USD", true));
        alice = accountRepository.save(Account.open("ACC-ALICE", "Alice", "alice"));
        unclaimed = accountRepository.save(new Account("ACC-SEED", "Seed", BigDecimal.ZERO, AccountStatus.ACTIVE,
            LocalDateTime.now()));
    }

    @Test
    void readsOrdersInTheRangeWithTheirInstrumentAndOwner() {
        Order filled = order(alice, apple, OrderSide.BUY, OrderType.MARKET, 10, "150.00", FROM.plusHours(10));
        filled.execute();
        orderRepository.save(filled);

        List<OrderActivity> activity = findAll();

        assertEquals(1, activity.size());
        OrderActivity row = activity.get(0);
        assertEquals("AAPL", row.symbol());
        assertEquals("Apple Inc.", row.instrumentName());
        assertEquals(AssetClass.EQUITY, row.assetClass());
        assertEquals(OrderSide.BUY, row.side());
        assertEquals(OrderType.MARKET, row.orderType());
        assertEquals(OrderStatus.FILLED, row.status());
        assertEquals("alice", row.trader());
        assertEquals(0, new BigDecimal("1500.00").compareTo(row.value()));
    }

    @Test
    void includesFromButNotTo() {
        save(order(alice, apple, OrderSide.BUY, OrderType.MARKET, 1, "1", FROM.minusSeconds(1)));
        save(order(alice, apple, OrderSide.BUY, OrderType.MARKET, 2, "1", FROM));
        save(order(alice, apple, OrderSide.BUY, OrderType.MARKET, 3, "1", TO.minusSeconds(1)));
        save(order(alice, apple, OrderSide.BUY, OrderType.MARKET, 4, "1", TO));

        assertEquals(List.of(2, 3), findAll().stream().map(OrderActivity::quantity).toList());
    }

    @Test
    void returnsOldestFirst() {
        save(order(alice, apple, OrderSide.BUY, OrderType.MARKET, 3, "1", FROM.plusHours(15)));
        save(order(alice, apple, OrderSide.BUY, OrderType.MARKET, 1, "1", FROM.plusHours(9)));
        save(order(alice, apple, OrderSide.BUY, OrderType.MARKET, 2, "1", FROM.plusHours(12)));

        assertEquals(List.of(1, 2, 3), findAll().stream().map(OrderActivity::quantity).toList());
    }

    @Test
    void eachFilterNarrowsTheResult() {
        save(order(alice, apple, OrderSide.BUY, OrderType.MARKET, 1, "1", FROM.plusHours(10)));
        save(order(alice, gold, OrderSide.SELL, OrderType.LIMIT, 2, "1", FROM.plusHours(11)));
        save(order(unclaimed, apple, OrderSide.SELL, OrderType.MARKET, 3, "1", FROM.plusHours(12)));

        assertEquals(List.of(2), quantities(AssetClass.COMMODITY, null, null, null));
        assertEquals(List.of(2, 3), quantities(null, OrderSide.SELL, null, null));
        assertEquals(List.of(2), quantities(null, null, OrderType.LIMIT, null));
        assertEquals(List.of(1, 2), quantities(null, null, null, "alice"));
        assertEquals(List.of(3), quantities(AssetClass.EQUITY, OrderSide.SELL, OrderType.MARKET, null));
    }

    @Test
    void unclaimedAccountsHaveNoTrader() {
        save(order(unclaimed, apple, OrderSide.BUY, OrderType.MARKET, 1, "1", FROM.plusHours(10)));

        assertNull(findAll().get(0).trader());
    }

    private List<OrderActivity> findAll() {
        return orderRepository.findActivity(FROM, TO, null, null, null, null);
    }

    private List<Integer> quantities(AssetClass assetClass, OrderSide side, OrderType orderType, String trader) {
        return orderRepository.findActivity(FROM, TO, assetClass, side, orderType, trader).stream()
            .map(OrderActivity::quantity).toList();
    }

    private void save(Order order) {
        orderRepository.save(order);
    }

    private static Order order(Account account, Instrument instrument, OrderSide side, OrderType type,
                               int quantity, String price, LocalDateTime createdOn) {
        return new Order(UUID.randomUUID(), account, instrument, side, type, quantity, new BigDecimal(price),
            UUID.randomUUID().toString(), createdOn);
    }
}
