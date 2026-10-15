package com.neueda.app.controllers;

import com.neueda.app.dtos.MarketAssetResponse;
import com.neueda.app.services.MarketOverviewService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class DashboardController {

    private final MarketOverviewService marketOverviewService;

    public DashboardController(MarketOverviewService marketOverviewService) {
        this.marketOverviewService = marketOverviewService;
    }

    @GetMapping("/market/overview")
    public List<MarketAssetResponse> getMarketOverview() {
        return marketOverviewService.overview();
    }
}
