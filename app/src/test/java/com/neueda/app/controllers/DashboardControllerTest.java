package com.neueda.app.controllers;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.neueda.app.dtos.MarketAssetResponse;
import com.neueda.app.services.MarketOverviewService;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardControllerTest {

    @Test
    void returnsTheMarketOverview() {
        MarketOverviewService service = mock(MarketOverviewService.class);
        List<MarketAssetResponse> overview = List.of();
        when(service.overview()).thenReturn(overview);

        assertSame(overview, new DashboardController(service).getMarketOverview());
    }
}
