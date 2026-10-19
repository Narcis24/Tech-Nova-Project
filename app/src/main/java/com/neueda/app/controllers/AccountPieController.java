package com.neueda.app.controllers;

import com.neueda.app.dtos.AccountPieResponse;
import com.neueda.app.dtos.LinkPieRequest;
import com.neueda.app.dtos.RebalancePreviewResponse;
import com.neueda.app.services.AccountAccess;
import com.neueda.app.services.AccountPieService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/accounts/{accountId}/pie")
public class AccountPieController {

    private final AccountPieService accountPieService;
    private final AccountAccess accountAccess;

    public AccountPieController(AccountPieService accountPieService, AccountAccess accountAccess) {
        this.accountPieService = accountPieService;
        this.accountAccess = accountAccess;
    }

    @PutMapping
    public ResponseEntity<AccountPieResponse> link(@PathVariable String accountId,
                                                   @Valid @RequestBody LinkPieRequest request) {
        accountAccess.requireOwned(accountId);
        return ResponseEntity.ok(accountPieService.link(accountId, accountAccess.currentUser(), request.getPieId()));
    }

    @DeleteMapping
    public ResponseEntity<Void> unlink(@PathVariable String accountId) {
        accountAccess.requireOwned(accountId);
        accountPieService.unlink(accountId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<AccountPieResponse> get(@PathVariable String accountId) {
        accountAccess.requireOwned(accountId);
        return ResponseEntity.ok(accountPieService.get(accountId));
    }

    @PostMapping("/preview")
    public ResponseEntity<RebalancePreviewResponse> preview(@PathVariable String accountId) {
        accountAccess.requireOwned(accountId);
        return ResponseEntity.ok(accountPieService.preview(accountId));
    }
}
