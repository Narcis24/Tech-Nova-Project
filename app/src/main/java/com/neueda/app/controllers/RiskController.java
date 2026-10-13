package com.neueda.app.controllers;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import com.neueda.app.dtos.PlaceOrderRequest;
import com.neueda.app.dtos.RiskWarning;
import com.neueda.app.services.AccountAccess;
import com.neueda.app.services.RiskService;
import java.util.List;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/v1/risk")
public class RiskController {

    private final RiskService riskService;
    private final AccountAccess accountAccess;

    public RiskController(RiskService riskService, AccountAccess accountAccess) {
        this.riskService = riskService;
        this.accountAccess = accountAccess;
    }

    /** Warnings for an order before it is placed. Takes the same body as POST /v1/orders and places nothing. */
    @PostMapping("/check")
    public ResponseEntity<List<RiskWarning>> check(@Valid @RequestBody PlaceOrderRequest orderRequest) {
        accountAccess.requireOwned(orderRequest.getAccountId());
        return ResponseEntity.ok(riskService.check(orderRequest));
    }
}
