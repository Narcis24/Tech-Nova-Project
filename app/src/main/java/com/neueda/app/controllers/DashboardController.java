package com.neueda.app.controllers;

import com.neueda.app.dtos.BalanceHistoryResponse;
import com.neueda.app.dtos.MarketAssetResponse;
import com.neueda.app.services.AccountAccess;
import com.neueda.app.services.BalanceHistoryService;
import com.neueda.app.services.MarketOverviewService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
public class DashboardController {
    private final AccountAccess access;
    private final BalanceHistoryService balances;
    private final MarketOverviewService market;
    public DashboardController(AccountAccess access, BalanceHistoryService balances, MarketOverviewService market) {
        this.access = access; this.balances = balances; this.market = market;
    }
    @GetMapping("/accounts/{accountId}/balance-history")
    public BalanceHistoryResponse getBalanceHistory(@PathVariable String accountId) {
        access.requireOwned(accountId);
        return balances.previousSession(accountId);
    }
    @GetMapping("/market/overview")
    public List<MarketAssetResponse> getMarketOverview() { return market.overview(); }
}
