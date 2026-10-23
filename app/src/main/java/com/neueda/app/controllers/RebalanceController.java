package com.neueda.app.controllers;

import com.neueda.app.dtos.RebalanceRunResponse;
import com.neueda.app.services.AccountAccess;
import com.neueda.app.services.RebalanceRunService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RebalanceController {

    private final RebalanceRunService rebalanceRunService;
    private final AccountAccess accountAccess;

    public RebalanceController(RebalanceRunService rebalanceRunService, AccountAccess accountAccess) {
        this.rebalanceRunService = rebalanceRunService;
        this.accountAccess = accountAccess;
    }

    @PostMapping("/v1/accounts/{accountId}/rebalance")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RebalanceRunResponse startRebalance(@PathVariable String accountId) {
        accountAccess.requireOwned(accountId);
        return rebalanceRunService.start(accountId);
    }

    @GetMapping("/v1/rebalance-runs/{runId}")
    public RebalanceRunResponse getRebalanceRun(@PathVariable UUID runId) {
        accountAccess.requireOwnedRun(runId);
        return rebalanceRunService.get(runId);
    }

    @PostMapping("/v1/rebalance-runs/{runId}/cancel")
    public RebalanceRunResponse cancelRebalanceRun(@PathVariable UUID runId) {
        accountAccess.requireOwnedRun(runId);
        return rebalanceRunService.cancel(runId);
    }
}
