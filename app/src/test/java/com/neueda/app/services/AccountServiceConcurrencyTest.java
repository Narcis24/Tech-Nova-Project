package com.neueda.app.services;

import com.neueda.app.enums.AccountStatus;
import com.neueda.app.models.Account;
import com.neueda.app.repositories.AccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Concurrent balance changes on one account against a real (H2) database.
 * Runs without a test transaction so each deposit/withdrawal commits on its own.
 */
@DataJpaTest
@Import({AccountService.class, PositionValuator.class})
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AccountServiceConcurrencyTest {

    private static final String ACCOUNT_ID = "CONC001";
    private static final int THREADS = 20;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        accountRepository.save(new Account(ACCOUNT_ID, "Concurrent User", new BigDecimal("100.00"),
            AccountStatus.ACTIVE, LocalDateTime.now()));
    }

    @AfterEach
    void tearDown() {
        accountRepository.deleteAll();
    }

    @Test
    void concurrentDepositsAreAllApplied() throws Exception {
        runConcurrently(() -> accountService.depositCash(ACCOUNT_ID, BigDecimal.ONE));

        assertEquals(0, new BigDecimal("120.00").compareTo(balance()));
    }

    @Test
    void concurrentWithdrawalsAreAllApplied() throws Exception {
        runConcurrently(() -> accountService.withdrawCash(ACCOUNT_ID, BigDecimal.ONE));

        assertEquals(0, new BigDecimal("80.00").compareTo(balance()));
    }

    private void runConcurrently(Runnable action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    action.run();
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get();  // rethrows any failure, e.g. an optimistic lock conflict
            }
        } finally {
            pool.shutdown();
        }
    }

    private BigDecimal balance() {
        return accountRepository.findById(ACCOUNT_ID).orElseThrow().getCashBalance();
    }
}
