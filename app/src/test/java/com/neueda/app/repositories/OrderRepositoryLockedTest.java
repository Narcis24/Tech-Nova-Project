package com.neueda.app.repositories;

import com.neueda.app.enums.AssetClass;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.enums.OrderType;
import com.neueda.app.enums.TimeInForce;
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

/** The cash and shares pending orders lock, and the orders due to expire, against a real (H2) database. */
@DataJpaTest
@ActiveProfiles("test")
class OrderRepositoryLockedTest {

    private static final LocalDateTime PLACED = LocalDateTime.of(2026, 10, 21, 14, 0);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    private Account alice;
    private Account bob;
    private Instrument apple;
    private Instrument gold;

    @BeforeEach
    void setUp() {
        apple = instrumentRepository.save(new Instrument("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true));
        gold = instrumentRepository.save(new Instrument("GLD", "SPDR Gold Shares", AssetClass.COMMODITY, "USD", true));
        alice = accountRepository.save(Account.open("ACC-ALICE", "Alice", "alice"));
        bob = accountRepository.save(Account.open("ACC-BOB", "Bob", "bob"));
    }

    @Test
    void nothingIsLockedWithoutPendingOrders() {
        assertEquals(0, BigDecimal.ZERO.compareTo(orderRepository.lockedCash(alice.getAccountId())));
        assertEquals(0, orderRepository.lockedShares(alice.getAccountId(), "AAPL"));
        assertEquals(List.of(), orderRepository.lockedSharesBySymbol(alice.getAccountId()));
    }

    @Test
    void lockedCashSumsOnlyTheAccountsPendingBuys() {
        save(order(alice, apple, OrderSide.BUY, OrderType.LIMIT, 10, "150.00"));   // 1,500.00
        save(order(alice, gold, OrderSide.BUY, OrderType.MARKET, 2, "200.25"));    //   400.50
        save(order(alice, apple, OrderSide.SELL, OrderType.LIMIT, 5, "160.00"));   // a sell
        save(order(bob, apple, OrderSide.BUY, OrderType.LIMIT, 10, "150.00"));     // another account
        Order filled = order(alice, apple, OrderSide.BUY, OrderType.MARKET, 3, "150.00");
        filled.execute();
        save(filled);
        Order cancelled = order(alice, apple, OrderSide.BUY, OrderType.LIMIT, 4, "140.00");
        cancelled.cancel();
        save(cancelled);

        assertEquals(0, new BigDecimal("1900.50").compareTo(orderRepository.lockedCash(alice.getAccountId())));
    }

    @Test
    void lockedSharesSumOnlyTheAccountsPendingSellsPerSymbol() {
        save(order(alice, apple, OrderSide.SELL, OrderType.LIMIT, 5, "160.00"));
        save(order(alice, apple, OrderSide.SELL, OrderType.MARKET, 2, "150.00"));
        save(order(alice, gold, OrderSide.SELL, OrderType.LIMIT, 1, "210.00"));
        save(order(alice, apple, OrderSide.BUY, OrderType.LIMIT, 9, "140.00"));    // a buy
        save(order(bob, apple, OrderSide.SELL, OrderType.LIMIT, 4, "160.00"));     // another account
        Order filled = order(alice, apple, OrderSide.SELL, OrderType.MARKET, 3, "150.00");
        filled.execute();
        save(filled);

        assertEquals(7, orderRepository.lockedShares(alice.getAccountId(), "AAPL"));
        assertEquals(
            List.of(new LockedShares("AAPL", 7L), new LockedShares("GLD", 1L)),
            orderRepository.lockedSharesBySymbol(alice.getAccountId()).stream()
                .sorted((a, b) -> a.symbol().compareTo(b.symbol()))
                .toList());
    }

    @Test
    void findsPendingOrdersExpiringByAGivenTime() {
        Order due = order(alice, apple, OrderSide.BUY, OrderType.LIMIT, 1, "150.00");
        due.setExpiry(TimeInForce.DAY, LocalDateTime.of(2026, 10, 21, 20, 0));
        save(due);
        Order later = order(alice, apple, OrderSide.BUY, OrderType.LIMIT, 1, "150.00");
        later.setExpiry(TimeInForce.GTC, LocalDateTime.of(2027, 1, 19, 21, 0));
        save(later);
        save(order(alice, apple, OrderSide.BUY, OrderType.MARKET, 1, "150.00"));   // never expires

        List<Order> expiring = orderRepository.findByStatusAndExpiresAtLessThanEqual(
            OrderStatus.PENDING, LocalDateTime.of(2026, 10, 21, 20, 0));

        assertEquals(List.of(due.getId()), expiring.stream().map(Order::getId).toList());
    }

    private void save(Order order) {
        orderRepository.save(order);
    }

    private static Order order(Account account, Instrument instrument, OrderSide side, OrderType type,
                               int quantity, String price) {
        return new Order(UUID.randomUUID(), account, instrument, side, type, quantity, new BigDecimal(price),
            UUID.randomUUID().toString(), PLACED);
    }
}
