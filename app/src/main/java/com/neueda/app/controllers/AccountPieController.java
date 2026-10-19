package com.neueda.app.controllers;

import com.neueda.app.dtos.AccountPieResponse;
import com.neueda.app.dtos.CopyPieRequest;
import com.neueda.app.dtos.LinkPieRequest;
import com.neueda.app.dtos.RebalancePreviewResponse;
import com.neueda.app.dtos.RebalanceRunResponse;
import com.neueda.app.dtos.UpdateCopySettingsRequest;
import com.neueda.app.services.AccountAccess;
import com.neueda.app.services.AccountPieService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    public ResponseEntity<AccountPieResponse> linkPie(@PathVariable String accountId,
                                                   @Valid @RequestBody LinkPieRequest request) {
        accountAccess.requireOwned(accountId);
        return ResponseEntity.ok(accountPieService.link(accountId, accountAccess.currentUser(), request.getPieId()));
    }

    @DeleteMapping
    public ResponseEntity<Void> unlinkPie(@PathVariable String accountId) {
        accountAccess.requireOwned(accountId);
        accountPieService.unlink(accountId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<AccountPieResponse> getAccountPie(@PathVariable String accountId) {
        accountAccess.requireOwned(accountId);
        return ResponseEntity.ok(accountPieService.get(accountId));
    }

    @PostMapping("/preview")
    public ResponseEntity<RebalancePreviewResponse> previewRebalance(@PathVariable String accountId) {
        accountAccess.requireOwned(accountId);
        return ResponseEntity.ok(accountPieService.preview(accountId));
    }

    /** The preview of copying a public pie into the account. Nothing is linked or placed. */
    @PostMapping("/copy-preview")
    public ResponseEntity<RebalancePreviewResponse> previewCopy(@PathVariable String accountId,
                                                                @Valid @RequestBody CopyPieRequest request) {
        accountAccess.requireOwned(accountId);
        return ResponseEntity.ok(accountPieService.previewCopy(accountId, accountAccess.currentUser(), request.getPieId()));
    }

    /** Confirms the copy: links the account as a COPY and starts the run to the pie's latest version. */
    @PostMapping("/copy")
    public ResponseEntity<RebalanceRunResponse> copyPie(@PathVariable String accountId,
                                                        @Valid @RequestBody CopyPieRequest request) {
        accountAccess.requireOwned(accountId);
        boolean autoSync = !Boolean.FALSE.equals(request.getAutoSync());
        return ResponseEntity.accepted()
            .body(accountPieService.copy(accountId, accountAccess.currentUser(), request.getPieId(), autoSync));
    }

    @PatchMapping
    public ResponseEntity<AccountPieResponse> updateCopySettings(@PathVariable String accountId,
                                                                 @Valid @RequestBody UpdateCopySettingsRequest request) {
        accountAccess.requireOwned(accountId);
        return ResponseEntity.ok(accountPieService.updateCopySettings(accountId, request.getAutoSync()));
    }

    @PostMapping("/make-mine")
    public ResponseEntity<AccountPieResponse> makePieMine(@PathVariable String accountId) {
        accountAccess.requireOwned(accountId);
        return ResponseEntity.ok(accountPieService.makeMine(accountId, accountAccess.currentUser()));
    }
}
