
package com.neueda.app.controllers;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import java.math.BigDecimal;
import java.util.List;
import com.neueda.app.dtos.AccountResponse;
import com.neueda.app.dtos.BalanceResponse;
import com.neueda.app.dtos.CashAmountRequest;
import com.neueda.app.dtos.PositionResponse;
import com.neueda.app.dtos.OrderResponse;
import com.neueda.app.services.AccountService;
import jakarta.validation.Valid;


@RestController
@RequestMapping("/v1/accounts")

public class AccountController {


    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable String accountId) {
        AccountResponse accountResponse = accountService.getAccount(accountId);
        return ResponseEntity.ok(accountResponse);
    }

    @GetMapping("/{accountId}/balance") 
    public ResponseEntity<BalanceResponse> getAccountBalance(@PathVariable String accountId) {
        BigDecimal balance = accountService.getAccountCashBalance(accountId);
        return ResponseEntity.ok(new BalanceResponse(balance));
    }

    @GetMapping("/{accountId}/positions")
    public ResponseEntity<List<PositionResponse>> getAccountPositions(@PathVariable String accountId) {
        List<PositionResponse> positionResponses = accountService.getAccountPositions(accountId);
        return ResponseEntity.ok(positionResponses);
    }

    @GetMapping("/{accountId}/orders")
    public ResponseEntity<List<OrderResponse>> getAccountOrders(@PathVariable String accountId) {
        List<OrderResponse> orderResponses = accountService.getAccountOrders(accountId);
        return ResponseEntity.ok(orderResponses);
    }

    @PostMapping("/{accountId}/deposit")
    public ResponseEntity<AccountResponse> deposit(@PathVariable String accountId, @Valid @RequestBody CashAmountRequest request) {
        return ResponseEntity.ok(accountService.depositCash(accountId, request.getAmount()));
    }

    @PostMapping("/{accountId}/withdraw")
    public ResponseEntity<AccountResponse> withdraw(@PathVariable String accountId, @Valid @RequestBody CashAmountRequest request) {
        return ResponseEntity.ok(accountService.withdrawCash(accountId, request.getAmount()));
    }
}
