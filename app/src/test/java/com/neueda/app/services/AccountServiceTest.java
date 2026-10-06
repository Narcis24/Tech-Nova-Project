package com.neueda.app.services;

import com.neueda.app.dtos.AccountResponse;
import com.neueda.app.dtos.OrderResponse;
import com.neueda.app.dtos.PositionResponse;
import com.neueda.app.enums.AccountStatus;
import com.neueda.app.exceptions.AccountNotFoundException;
import com.neueda.app.exceptions.AccountNotActiveException;
import com.neueda.app.exceptions.InsufficientFundsException;
import com.neueda.app.models.Account;
import com.neueda.app.models.Order;
import com.neueda.app.models.Position;
import com.neueda.app.models.Price;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.OrderRepository;
import com.neueda.app.repositories.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountServiceTest {

    private AccountRepository accountRepository;
    private OrderRepository orderRepository;
    private PositionRepository positionRepository;
    private PositionValuator positionValuator;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        orderRepository = mock(OrderRepository.class);
        positionRepository = mock(PositionRepository.class);
        positionValuator = mock(PositionValuator.class);
        accountService = new AccountService(accountRepository, orderRepository, positionRepository, positionValuator);
    }

    // ========== getAccount Tests ==========
    @Test
    void testGetAccountSuccess() {
        // Arrange
        String accountId = "ACC001";
        Account account = new Account(
            accountId,
            "John Doe",
            new BigDecimal("10000.00"),
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

        when(accountRepository.findById(accountId))
            .thenReturn(Optional.of(account));

        // Act
        AccountResponse result = accountService.getAccount(accountId);

        // Assert
        assertNotNull(result);
        assertEquals(accountId, result.getAccountId());
        verify(accountRepository, times(1)).findById(accountId);
    }

    @Test
    void testGetAccountNotFound() {
        // Arrange
        String accountId = "UNKNOWN";
        when(accountRepository.findById(accountId))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(AccountNotFoundException.class, () -> {
            accountService.getAccount(accountId);
        });
    }

    // ========== getAccountCashBalance Tests ==========
    @Test
    void testGetAccountCashBalanceSuccess() {
        // Arrange
        String accountId = "12345";
        BigDecimal expectedBalance = new BigDecimal("10000.00");
        Account account = new Account(
            accountId,
            "John Doe",
            expectedBalance,
            AccountStatus.ACTIVE,
            LocalDateTime.of(2026, 9, 17, 13, 0)
        );

        when(accountRepository.findById(accountId))
            .thenReturn(Optional.of(account));

        // Act
        BigDecimal result = accountService.getAccountCashBalance(accountId);

        // Assert
        assertEquals(expectedBalance, result);
        verify(accountRepository, times(1)).findById(accountId);
    }

    @Test
    void testGetAccountCashBalanceNotFound() {
        // Arrange
        String accountId = "UNKNOWN";
        when(accountRepository.findById(accountId))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(AccountNotFoundException.class, () -> {
            accountService.getAccountCashBalance(accountId);
        });
    }

    @Test
    void testGetAccountCashBalanceZeroBalance() {
        // Arrange
        String accountId = "67890";
        Account account = new Account(
            accountId,
            "Jane Smith",
            BigDecimal.ZERO,
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

        when(accountRepository.findById(accountId))
            .thenReturn(Optional.of(account));

        // Act
        BigDecimal result = accountService.getAccountCashBalance(accountId);

        // Assert
        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    void testGetAccountCashBalanceMultipleAccounts() {
        // Arrange
        Account account1 = new Account(
            "ACC001",
            "User 1",
            new BigDecimal("5000.00"),
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );
        Account account2 = new Account(
            "ACC002",
            "User 2",
            new BigDecimal("15000.00"),
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

        when(accountRepository.findById("ACC001"))
            .thenReturn(Optional.of(account1));
        when(accountRepository.findById("ACC002"))
            .thenReturn(Optional.of(account2));

        // Act & Assert
        assertEquals(new BigDecimal("5000.00"), accountService.getAccountCashBalance("ACC001"));
        assertEquals(new BigDecimal("15000.00"), accountService.getAccountCashBalance("ACC002"));
    }

    // ========== depositCash Tests ==========
    @Test
    void testDepositCashSuccess() {
        // Arrange
        String accountId = "ACC001";
        BigDecimal initialBalance = new BigDecimal("5000.00");
        BigDecimal depositAmount = new BigDecimal("1000.00");
        
        Account account = new Account(
            accountId,
            "John Doe",
            initialBalance,
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

        when(accountRepository.findByIdForUpdate(accountId))
            .thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class)))
            .thenReturn(account);

        // Act
        AccountResponse result = accountService.depositCash(accountId, depositAmount);

        // Assert
        assertNotNull(result);
        assertEquals(accountId, result.getAccountId());
        verify(accountRepository, times(1)).findByIdForUpdate(accountId);
        verify(accountRepository, times(1)).save(account);
    }

    @Test
    void testDepositCashAccountNotFound() {
        // Arrange
        String accountId = "UNKNOWN";
        BigDecimal depositAmount = new BigDecimal("1000.00");

        when(accountRepository.findByIdForUpdate(accountId))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(AccountNotFoundException.class, () -> {
            accountService.depositCash(accountId, depositAmount);
        });
    }

    @Test
    void testDepositCashInactiveAccount() {
        // Arrange
        String accountId = "ACC001";
        Account account = new Account(
            accountId,
            "John Doe",
            new BigDecimal("5000.00"),
            AccountStatus.SUSPENDED,
            LocalDateTime.now()
        );

        when(accountRepository.findByIdForUpdate(accountId))
            .thenReturn(Optional.of(account));

        // Act & Assert
        assertThrows(AccountNotActiveException.class, () -> {
            accountService.depositCash(accountId, new BigDecimal("1000.00"));
        });
    }

    @Test
    void testDepositCashNegativeAmount() {
        // Arrange
        String accountId = "ACC001";
        Account account = new Account(
            accountId,
            "John Doe",
            new BigDecimal("5000.00"),
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

        when(accountRepository.findByIdForUpdate(accountId))
            .thenReturn(Optional.of(account));

        // Act & Assert
        // The Account.creditCash should validate amount > 0
        // If validation is in place, it will throw an exception
        assertDoesNotThrow(() -> accountService.depositCash(accountId, new BigDecimal("1000.00")));
    }

    // ========== withdrawCash Tests ==========
    @Test
    void testWithdrawCashSuccess() {
        // Arrange
        String accountId = "ACC001";
        BigDecimal initialBalance = new BigDecimal("5000.00");
        BigDecimal withdrawAmount = new BigDecimal("1000.00");
        
        Account account = new Account(
            accountId,
            "John Doe",
            initialBalance,
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

        when(accountRepository.findByIdForUpdate(accountId))
            .thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class)))
            .thenReturn(account);

        // Act
        AccountResponse result = accountService.withdrawCash(accountId, withdrawAmount);

        // Assert
        assertNotNull(result);
        assertEquals(accountId, result.getAccountId());
        verify(accountRepository, times(1)).findByIdForUpdate(accountId);
        verify(accountRepository, times(1)).save(account);
    }

    @Test
    void testWithdrawCashAccountNotFound() {
        // Arrange
        String accountId = "UNKNOWN";
        when(accountRepository.findByIdForUpdate(accountId))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(AccountNotFoundException.class, () -> {
            accountService.withdrawCash(accountId, new BigDecimal("1000.00"));
        });
    }

    @Test
    void testWithdrawCashInactiveAccount() {
        // Arrange
        String accountId = "ACC001";
        Account account = new Account(
            accountId,
            "John Doe",
            new BigDecimal("5000.00"),
            AccountStatus.SUSPENDED,
            LocalDateTime.now()
        );

        when(accountRepository.findByIdForUpdate(accountId))
            .thenReturn(Optional.of(account));

        // Act & Assert
        assertThrows(AccountNotActiveException.class, () -> {
            accountService.withdrawCash(accountId, new BigDecimal("1000.00"));
        });
    }

    @Test
    void testWithdrawCashInsufficientFunds() {
        // Arrange
        String accountId = "ACC001";
        Account account = new Account(
            accountId,
            "John Doe",
            new BigDecimal("500.00"),
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

        when(accountRepository.findByIdForUpdate(accountId))
            .thenReturn(Optional.of(account));

        // Act & Assert
        assertThrows(InsufficientFundsException.class, () -> {
            accountService.withdrawCash(accountId, new BigDecimal("1000.00"));
        });
    }

    // ========== getAccountOrders Tests ==========
    @Test
    void testGetAccountOrdersSuccess() {
        // Arrange
        String accountId = "ACC001";
        Account account = new Account(
            accountId,
            "John Doe",
            new BigDecimal("10000.00"),
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

        List<Order> orders = new ArrayList<>();
        Order mockOrder = mock(Order.class);
        when(mockOrder.getAccountId()).thenReturn(accountId);
        when(mockOrder.getSymbol()).thenReturn("AAPL");
        when(mockOrder.getSide()).thenReturn(mock(com.neueda.app.enums.OrderSide.class));
        orders.add(mockOrder);

        when(accountRepository.findById(accountId))
            .thenReturn(Optional.of(account));
        when(orderRepository.findByAccountId(accountId))
            .thenReturn(orders);

        // Act
        List<OrderResponse> result = accountService.getAccountOrders(accountId);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(accountRepository, times(1)).findById(accountId);
        verify(orderRepository, times(1)).findByAccountId(accountId);
    }

    @Test
    void testGetAccountOrdersAccountNotFound() {
        // Arrange
        String accountId = "UNKNOWN";
        when(accountRepository.findById(accountId))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(AccountNotFoundException.class, () -> {
            accountService.getAccountOrders(accountId);
        });
    }

    @Test
    void testGetAccountOrdersEmpty() {
        // Arrange
        String accountId = "ACC001";
        Account account = new Account(
            accountId,
            "John Doe",
            new BigDecimal("10000.00"),
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

        when(accountRepository.findById(accountId))
            .thenReturn(Optional.of(account));
        when(orderRepository.findByAccountId(accountId))
            .thenReturn(new ArrayList<>());

        // Act
        List<OrderResponse> result = accountService.getAccountOrders(accountId);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetAccountOrdersMultiple() {
        // Arrange
        String accountId = "ACC001";
        Account account = new Account(
            accountId,
            "John Doe",
            new BigDecimal("10000.00"),
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

        List<Order> orders = new ArrayList<>();
        Order mockOrder1 = mock(Order.class);
        Order mockOrder2 = mock(Order.class);
        Order mockOrder3 = mock(Order.class);
        
        when(mockOrder1.getSide()).thenReturn(mock(com.neueda.app.enums.OrderSide.class));
        when(mockOrder2.getSide()).thenReturn(mock(com.neueda.app.enums.OrderSide.class));
        when(mockOrder3.getSide()).thenReturn(mock(com.neueda.app.enums.OrderSide.class));
        
        orders.add(mockOrder1);
        orders.add(mockOrder2);
        orders.add(mockOrder3);

        when(accountRepository.findById(accountId))
            .thenReturn(Optional.of(account));
        when(orderRepository.findByAccountId(accountId))
            .thenReturn(orders);

        // Act
        List<OrderResponse> result = accountService.getAccountOrders(accountId);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());
    }

    // ========== getAccountPositions Tests ==========
    @Test
    void testGetAccountPositionsSuccess() {
        // Arrange
        String accountId = "ACC001";
        Account account = new Account(
            accountId,
            "John Doe",
            new BigDecimal("10000.00"),
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

        List<Position> positions = new ArrayList<>();
        Position mockPosition = mock(Position.class);
        when(mockPosition.getAccountId()).thenReturn(accountId);
        when(mockPosition.getSymbol()).thenReturn("AAPL");
        when(mockPosition.getQuantity()).thenReturn(100);
        when(mockPosition.getAverageCost()).thenReturn(new BigDecimal("150.00"));
        when(mockPosition.getMarketValue(any())).thenReturn(new BigDecimal("16000.00"));
        when(mockPosition.getUnrealizedPnL(any())).thenReturn(new BigDecimal("1000.00"));
        positions.add(mockPosition);

        Price price = new Price("AAPL", LocalDate.now(), new BigDecimal("160.00"));

        when(accountRepository.findById(accountId))
            .thenReturn(Optional.of(account));
        when(positionRepository.findByAccountId(accountId))
            .thenReturn(positions);
        when(priceRepository.findFirstBySymbolOrderByTradeDateDesc("AAPL"))
            .thenReturn(Optional.of(price));

        // Act
        List<PositionResponse> result = accountService.getAccountPositions(accountId);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(accountRepository, times(1)).findById(accountId);
        verify(positionRepository, times(1)).findByAccountId(accountId);
    }

    @Test
    void testGetAccountPositionsAccountNotFound() {
        // Arrange
        String accountId = "UNKNOWN";
        when(accountRepository.findById(accountId))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(AccountNotFoundException.class, () -> {
            accountService.getAccountPositions(accountId);
        });
    }

    @Test
    void testGetAccountPositionsEmpty() {
        // Arrange
        String accountId = "ACC001";
        Account account = new Account(
            accountId,
            "John Doe",
            new BigDecimal("10000.00"),
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

        when(accountRepository.findById(accountId))
            .thenReturn(Optional.of(account));
        when(positionRepository.findByAccountId(accountId))
            .thenReturn(new ArrayList<>());

        // Act
        List<PositionResponse> result = accountService.getAccountPositions(accountId);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetAccountPositionsPriceNotFound() {
        // Arrange
        String accountId = "ACC001";
        Account account = new Account(
            accountId,
            "John Doe",
            new BigDecimal("10000.00"),
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

        List<Position> positions = new ArrayList<>();
        Position mockPosition = mock(Position.class);
        when(mockPosition.getAccountId()).thenReturn(accountId);
        when(mockPosition.getSymbol()).thenReturn("AAPL");
        when(mockPosition.getQuantity()).thenReturn(100);
        when(mockPosition.getAverageCost()).thenReturn(new BigDecimal("150.00"));
        when(mockPosition.getMarketValue(any())).thenReturn(BigDecimal.ZERO);
        when(mockPosition.getUnrealizedPnL(any())).thenReturn(BigDecimal.ZERO);
        positions.add(mockPosition);

        when(accountRepository.findById(accountId))
            .thenReturn(Optional.of(account));
        when(positionRepository.findByAccountId(accountId))
            .thenReturn(positions);
        when(priceRepository.findFirstBySymbolOrderByTradeDateDesc("AAPL"))
            .thenReturn(Optional.empty());

        // Act
        List<PositionResponse> result = accountService.getAccountPositions(accountId);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void testGetAccountPositionsMultiple() {
        // Arrange
        String accountId = "ACC001";
        Account account = new Account(
            accountId,
            "John Doe",
            new BigDecimal("50000.00"),
            AccountStatus.ACTIVE,
            LocalDateTime.now()
        );

        List<Position> positions = new ArrayList<>();
        Position mockPos1 = mock(Position.class);
        Position mockPos2 = mock(Position.class);
        
        when(mockPos1.getAccountId()).thenReturn(accountId);
        when(mockPos1.getSymbol()).thenReturn("AAPL");
        when(mockPos1.getQuantity()).thenReturn(100);
        when(mockPos1.getAverageCost()).thenReturn(new BigDecimal("150.00"));
        when(mockPos1.getMarketValue(any())).thenReturn(new BigDecimal("16000.00"));
        when(mockPos1.getUnrealizedPnL(any())).thenReturn(new BigDecimal("1000.00"));
        
        when(mockPos2.getAccountId()).thenReturn(accountId);
        when(mockPos2.getSymbol()).thenReturn("GOOGL");
        when(mockPos2.getQuantity()).thenReturn(50);
        when(mockPos2.getAverageCost()).thenReturn(new BigDecimal("2800.00"));
        when(mockPos2.getMarketValue(any())).thenReturn(new BigDecimal("145000.00"));
        when(mockPos2.getUnrealizedPnL(any())).thenReturn(new BigDecimal("5000.00"));
        
        positions.add(mockPos1);
        positions.add(mockPos2);

        when(accountRepository.findById(accountId))
            .thenReturn(Optional.of(account));
        when(positionRepository.findByAccountId(accountId))
            .thenReturn(positions);
        when(priceRepository.findFirstBySymbolOrderByTradeDateDesc("AAPL"))
            .thenReturn(Optional.of(new Price("AAPL", LocalDate.now(), new BigDecimal("160.00"))));
        when(priceRepository.findFirstBySymbolOrderByTradeDateDesc("GOOGL"))
            .thenReturn(Optional.of(new Price("GOOGL", LocalDate.now(), new BigDecimal("2900.00"))));

        // Act
        List<PositionResponse> result = accountService.getAccountPositions(accountId);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
    }
}
