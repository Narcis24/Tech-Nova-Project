package com.neueda.app.services;

import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.PositionRepository;
import com.neueda.app.repositories.PriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortfolioServiceTest {

    private AccountRepository accountRepository;
    private PositionRepository positionRepository;
    private PriceRepository priceRepository;
    private PortfolioService portfolioService;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        positionRepository = mock(PositionRepository.class);
        priceRepository = mock(PriceRepository.class);
        portfolioService = new PortfolioService(accountRepository, positionRepository, priceRepository);
    }

    @Test
    void testPortfolioServiceInitialization() {
        // Arrange & Act
        assertNotNull(portfolioService);

        // Assert
        assertNotNull(accountRepository);
        assertNotNull(positionRepository);
        assertNotNull(priceRepository);
    }
}
