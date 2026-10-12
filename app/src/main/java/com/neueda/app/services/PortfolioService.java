package com.neueda.app.services;

import com.neueda.app.dtos.PortfolioMetricsResponse;
import com.neueda.app.dtos.PortfolioSnapshotResponse;
import com.neueda.app.dtos.PositionResponse;
import com.neueda.app.exceptions.AccountNotFoundException;
import com.neueda.app.models.Account;

import com.neueda.app.models.PortfolioMetrics;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.PositionRepository;
import java.math.BigDecimal;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PortfolioService {
    
    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final PositionValuator positionValuator;

    public PortfolioService(AccountRepository accountRepository, PositionRepository positionRepository, PositionValuator positionValuator) {
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.positionValuator = positionValuator;
    }

    public PortfolioSnapshotResponse getPortfolioSnapshot(String accountId) {
    
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(
                "Account not found: " + accountId
            ));
        
        List<PositionResponse> positionResponses = positionValuator.valueAll(positionRepository.findByAccountId(accountId));

        BigDecimal totalMarketValue = BigDecimal.ZERO;
        for (PositionResponse position : positionResponses) {
            totalMarketValue = totalMarketValue.add(position.getMarketValue());
        }
        // Return snapshot
        BigDecimal totalPortfolioValue = account.getCashBalance().add(totalMarketValue);
        
        return new PortfolioSnapshotResponse(
            accountId,
            account.getCashBalance(),
            totalMarketValue,
            totalPortfolioValue,
            positionResponses
        );
    }

    public PortfolioMetricsResponse getPortfolioMetrics(String accountId) {
        // Step 1: Fetch account
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(
                "Account not found: " + accountId
            ));
        
        // Step 2: Value all positions (one price query)
        List<PositionResponse> positionResponses = positionValuator.valueAll(positionRepository.findByAccountId(accountId));

        // Step 3: Calculate totals
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        BigDecimal totalUnrealizedPnL = BigDecimal.ZERO;
        for (PositionResponse position : positionResponses) {
            totalMarketValue = totalMarketValue.add(position.getMarketValue());
            totalUnrealizedPnL = totalUnrealizedPnL.add(position.getUnrealizedPnL());
        }

        // Step 4: Create PortfolioMetrics entity
        PortfolioMetrics metrics = new PortfolioMetrics(
            totalMarketValue,
            account.getCashBalance(),
            totalUnrealizedPnL
        );
        
        // Step 5: Get portfolio return %
        BigDecimal portfolioReturn = metrics.getPortfolioReturn();
        
        // Step 6: Return response
        return new PortfolioMetricsResponse(
            accountId,
            account.getCashBalance(),
            totalMarketValue,
            metrics.getTotalPortfolioValue(),
            totalUnrealizedPnL,
            portfolioReturn,
            positionResponses
        );
    }
}

