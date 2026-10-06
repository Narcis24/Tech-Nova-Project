
package com.neueda.app.controllers;

import com.neueda.app.services.AccountAccess;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import org.springframework.http.ResponseEntity;

import com.neueda.app.dtos.PortfolioMetricsResponse;
import com.neueda.app.dtos.PortfolioSnapshotResponse;

import com.neueda.app.services.PortfolioService;


@RestController
@RequestMapping("/v1/accounts")

public class PortfolioController {


    private final PortfolioService portfolioService;
    private final AccountAccess accountAccess;

    public PortfolioController(PortfolioService portfolioService, AccountAccess accountAccess) {
        this.portfolioService = portfolioService;
        this.accountAccess = accountAccess;
    }

    @GetMapping("/{accountId}/snapshot")
    public ResponseEntity<PortfolioSnapshotResponse> getPortfolioSnapshot(@PathVariable String accountId) {
        accountAccess.requireOwned(accountId);
        PortfolioSnapshotResponse portfolioSnapshotResponse = portfolioService.getPortfolioSnapshot(accountId);
        return ResponseEntity.ok(portfolioSnapshotResponse);
    }

    @GetMapping("/{accountId}/metrics")
    public ResponseEntity<PortfolioMetricsResponse> getPortfolioMetrics(@PathVariable String accountId) {
        accountAccess.requireOwned(accountId);
        PortfolioMetricsResponse portfolioMetricsResponse = portfolioService.getPortfolioMetrics(accountId);
        return ResponseEntity.ok(portfolioMetricsResponse);
    }
}