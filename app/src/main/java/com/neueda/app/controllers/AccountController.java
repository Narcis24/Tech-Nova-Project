
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
import com.neueda.app.dtos.OpenAccountRequest;
import com.neueda.app.services.AccountAccess;
import com.neueda.app.services.AccountService;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;


@RestController
@RequestMapping("/v1/accounts")

public class AccountController {


    private final AccountService accountService;
    private final AccountAccess accountAccess;

    public AccountController(AccountService accountService, AccountAccess accountAccess) {
        this.accountService = accountService;
        this.accountAccess = accountAccess;
    }

    /** Opens an empty account owned by the caller. */
    @PostMapping
    public ResponseEntity<AccountResponse> openAccount(@Valid @RequestBody OpenAccountRequest request) {
        AccountResponse accountResponse = accountService.openAccount(accountAccess.currentUser(), request.getHolderName());
        return ResponseEntity.status(HttpStatus.CREATED).body(accountResponse);
    }

    /** The caller's own accounts. */
    @GetMapping
    public ResponseEntity<List<AccountResponse>> getMyAccounts() {
        return ResponseEntity.ok(accountService.getAccountsOwnedBy(accountAccess.currentUser()));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable String accountId) {
        accountAccess.requireOwned(accountId);
        AccountResponse accountResponse = accountService.getAccount(accountId);
        return ResponseEntity.ok(accountResponse);
    }

    @GetMapping("/{accountId}/balance") 
    public ResponseEntity<BalanceResponse> getAccountBalance(@PathVariable String accountId) {
        accountAccess.requireOwned(accountId);
        BigDecimal balance = accountService.getAccountCashBalance(accountId);
        return ResponseEntity.ok(new BalanceResponse(balance));
    }

    @GetMapping("/{accountId}/positions")
    public ResponseEntity<List<PositionResponse>> getAccountPositions(@PathVariable String accountId) {
        accountAccess.requireOwned(accountId);
        List<PositionResponse> positionResponses = accountService.getAccountPositions(accountId);
        return ResponseEntity.ok(positionResponses);
    }

    @GetMapping("/{accountId}/orders")
    public ResponseEntity<List<OrderResponse>> getAccountOrders(@PathVariable String accountId) {
        accountAccess.requireOwned(accountId);
        List<OrderResponse> orderResponses = accountService.getAccountOrders(accountId);
        return ResponseEntity.ok(orderResponses);
    }

    @PostMapping("/{accountId}/deposit")
    public ResponseEntity<AccountResponse> deposit(@PathVariable String accountId, @Valid @RequestBody CashAmountRequest request) {
        accountAccess.requireOwned(accountId);
        return ResponseEntity.ok(accountService.depositCash(accountId, request.getAmount()));
    }

    @PostMapping("/{accountId}/withdraw")
    public ResponseEntity<AccountResponse> withdraw(@PathVariable String accountId, @Valid @RequestBody CashAmountRequest request) {
        accountAccess.requireOwned(accountId);
        return ResponseEntity.ok(accountService.withdrawCash(accountId, request.getAmount()));
    }
}
