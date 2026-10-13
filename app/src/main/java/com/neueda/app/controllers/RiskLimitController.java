package com.neueda.app.controllers;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import com.neueda.app.dtos.RiskLimitResponse;
import com.neueda.app.dtos.UpdateRiskLimitRequest;
import com.neueda.app.enums.RiskRule;
import com.neueda.app.services.AccountAccess;
import com.neueda.app.services.RiskLimitService;
import java.util.List;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/v1/accounts")
public class RiskLimitController {

    private final RiskLimitService riskLimitService;
    private final AccountAccess accountAccess;

    public RiskLimitController(RiskLimitService riskLimitService, AccountAccess accountAccess) {
        this.riskLimitService = riskLimitService;
        this.accountAccess = accountAccess;
    }

    @GetMapping("/{accountId}/risk-limits")
    public ResponseEntity<List<RiskLimitResponse>> getLimits(@PathVariable String accountId) {
        accountAccess.requireOwned(accountId);
        return ResponseEntity.ok(riskLimitService.getLimits(accountId));
    }

    @PutMapping("/{accountId}/risk-limits/{rule}")
    public ResponseEntity<RiskLimitResponse> updateLimit(@PathVariable String accountId, @PathVariable RiskRule rule,
                                                         @Valid @RequestBody UpdateRiskLimitRequest request) {
        accountAccess.requireOwned(accountId);
        return ResponseEntity.ok(riskLimitService.updateLimit(accountId, rule, request));
    }

    @DeleteMapping("/{accountId}/risk-limits/{rule}")
    public ResponseEntity<RiskLimitResponse> resetLimit(@PathVariable String accountId, @PathVariable RiskRule rule) {
        accountAccess.requireOwned(accountId);
        return ResponseEntity.ok(riskLimitService.resetLimit(accountId, rule));
    }
}
