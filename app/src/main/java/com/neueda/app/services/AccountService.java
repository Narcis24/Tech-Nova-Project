package com.neueda.app.services;

import org.springframework.stereotype.Service;
import com.neueda.app.dtos.AccountResponse;
import com.neueda.app.dtos.OrderResponse;
import com.neueda.app.dtos.PositionResponse;
import com.neueda.app.exceptions.*;
import com.neueda.app.models.Account;
import com.neueda.app.models.Order;
import com.neueda.app.models.Position;
import com.neueda.app.models.Price;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.OrderRepository;
import com.neueda.app.repositories.PositionRepository;
import com.neueda.app.repositories.PriceRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final OrderRepository orderRepository;
    private final PositionRepository positionRepository;
    private final PriceRepository priceRepository;

    public AccountService(AccountRepository accountRepository, OrderRepository orderRepository, PositionRepository positionRepository, PriceRepository priceRepository) {
        this.accountRepository = accountRepository;
        this.orderRepository = orderRepository;
        this.positionRepository = positionRepository;
        this.priceRepository = priceRepository;
    }

    public AccountResponse getAccount(String accountId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        return new AccountResponse(account);
    }

    public BigDecimal getAccountCashBalance(String accountId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(
                "Account not found: " + accountId
            ));
        
        return account.getCashBalance();
    }

    public AccountResponse depositCash(String accountId, BigDecimal amount) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(
                "Account not found: " + accountId
            ));
        
        account.validateStatus();  // Throws if not ACTIVE
        account.creditCash(amount);  // Validates amount > 0, updates balance + timestamp
        
        accountRepository.save(account);
        return new AccountResponse(account);
    }

    public AccountResponse withdrawCash(String accountId, BigDecimal amount) {
        Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new AccountNotFoundException(
                "Account not found: " + accountId
            ));
        
        account.validateStatus();  // Throws if not ACTIVE
        account.debitCash(amount);  // Throws if insufficient funds
        
        accountRepository.save(account);
        return new AccountResponse(account);
    }

    public List<OrderResponse> getAccountOrders(String accountId) {
        // Verify account exists
        accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(
                "Account not found: " + accountId
            ));
        
        // Retrieve all orders for the account
        List<Order> orders = orderRepository.findByAccountId(accountId);
        
        // Convert to OrderResponse DTOs
        return orders.stream()
            .map(OrderResponse::new)
            .collect(Collectors.toList());
    }

    public List<PositionResponse> getAccountPositions(String accountId) {
        // Verify account exists
        accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(
                "Account not found: " + accountId
            ));
        
        // Retrieve all positions for the account
        List<Position> positions = positionRepository.findByAccountId(accountId);
        
        // Convert to PositionResponse DTOs
        return positions.stream()
            .map(position -> {
                // Fetch current price for this position
                BigDecimal currentPrice = priceRepository.findBySymbol(position.getSymbol())
                    .map(Price::getPrice)
                    .orElse(BigDecimal.ZERO);
                
                return new PositionResponse(
                    position.getAccountId(),
                    position.getSymbol(),
                    position.getQuantity(),
                    position.getAverageCost(),
                    currentPrice,
                    position.getMarketValue(currentPrice),
                    position.getUnrealizedPnL(currentPrice)
                );
            })
            .collect(Collectors.toList());
    }
}

