package com.neueda.app.services;

import com.neueda.app.repositories.PositionRepository;
import com.neueda.app.repositories.PriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PositionServiceTest {

    private PositionRepository positionRepository;
    private PriceRepository priceRepository;
    private PositionService positionService;

    @BeforeEach
    void setUp() {
        positionRepository = mock(PositionRepository.class);
        priceRepository = mock(PriceRepository.class);
        positionService = new PositionService(positionRepository, priceRepository);
    }

    @Test
    void testPositionServiceInitialization() {
        // Arrange & Act
        assertNotNull(positionService);

        // Assert
        assertNotNull(positionRepository);
        assertNotNull(priceRepository);
    }
}
