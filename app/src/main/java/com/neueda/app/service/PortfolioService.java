package com.neueda.app.service;

import com.neueda.app.dto.PortfolioMetricsResponse;
import com.neueda.app.dto.PortfolioSnapshotResponse;
import com.neueda.app.dto.PositionResponse;
import com.neueda.app.exceptions.AccountNotFoundException;
import com.neueda.app.exceptions.TradingException;
import com.neueda.app.model.Account;
import com.neueda.app.model.Position;
import com.neueda.app.model.PortfolioMetrics;
import com.neueda.app.repository.AccountRepository;
import com.neueda.app.repository.PositionRepository;
import com.neueda.app.repository.PriceRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class PortfolioService {
    
    private AccountRepository accountRepository;
    private PositionRepository positionRepository;
    private PriceRepository priceRepository;

    public PortfolioService(AccountRepository accountRepository, PositionRepository positionRepository, PriceRepository priceRepository) {
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.priceRepository = priceRepository;
    }

    public PortfolioSnapshotResponse getPortfolioSnapshot(String accountId) {
    
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(
                "Account not found: " + accountId
            ));
        
        List<Position> positions = positionRepository.findByAccountId(accountId);
        
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        List<PositionResponse> positionResponses = new ArrayList<>();
        
        for (Position position : positions) {
            BigDecimal currentPrice = priceRepository.findLatestPrice(position.getSymbol())
                .orElse(BigDecimal.ZERO);
            
            BigDecimal marketValue = position.getMarketValue(currentPrice);
            BigDecimal unrealizedPnL = position.getUnrealizedPnL(currentPrice);
            
            totalMarketValue = totalMarketValue.add(marketValue);
            
            positionResponses.add(new PositionResponse(
                position.getAccountId(),
                position.getSymbol(),
                position.getQuantity(),
                position.getAverageCost(),
                currentPrice,
                marketValue,
                unrealizedPnL
            ));
        }
        
        // Step 5: Return snapshot
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
        
        // Step 2: Fetch all positions
        List<Position> positions = positionRepository.findByAccountId(accountId);
        
        // Step 3: Calculate totals
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        BigDecimal totalUnrealizedPnL = BigDecimal.ZERO;
        List<PositionResponse> positionResponses = new ArrayList<>();
        
        for (Position position : positions) {
            BigDecimal currentPrice = priceRepository.findLatestPrice(position.getSymbol())
                .orElse(BigDecimal.ZERO);
            
            BigDecimal marketValue = position.getMarketValue(currentPrice);
            BigDecimal unrealizedPnL = position.getUnrealizedPnL(currentPrice);
            
            totalMarketValue = totalMarketValue.add(marketValue);
            totalUnrealizedPnL = totalUnrealizedPnL.add(unrealizedPnL);
            
            positionResponses.add(new PositionResponse(
                position.getAccountId(),
                position.getSymbol(),
                position.getQuantity(),
                position.getAverageCost(),
                currentPrice,
                marketValue,
                unrealizedPnL
            ));
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

