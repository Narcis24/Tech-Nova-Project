package com.neueda.app.controllers;

import com.neueda.app.dtos.PieResponse;
import com.neueda.app.dtos.SavePieRequest;
import com.neueda.app.services.AccountAccess;
import com.neueda.app.services.PieService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/v1/pies")
public class PieController {

    private final PieService pieService;
    private final AccountAccess accountAccess;

    public PieController(PieService pieService, AccountAccess accountAccess) {
        this.pieService = pieService;
        this.accountAccess = accountAccess;
    }

    @GetMapping
    public ResponseEntity<List<PieResponse>> listMine() {
        return ResponseEntity.ok(pieService.listMine(accountAccess.currentUser()));
    }

    @PostMapping
    public ResponseEntity<PieResponse> create(@Valid @RequestBody SavePieRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pieService.create(accountAccess.currentUser(), request));
    }

    @GetMapping("/{pieId}")
    public ResponseEntity<PieResponse> get(@PathVariable UUID pieId) {
        return ResponseEntity.ok(pieService.get(pieId, accountAccess.currentUser()));
    }

    @PutMapping("/{pieId}")
    public ResponseEntity<PieResponse> update(@PathVariable UUID pieId, @Valid @RequestBody SavePieRequest request) {
        return ResponseEntity.ok(pieService.update(pieId, accountAccess.currentUser(), request));
    }

    @DeleteMapping("/{pieId}")
    public ResponseEntity<Void> deletePie(@PathVariable UUID pieId) {
        pieService.delete(pieId, accountAccess.currentUser());
        return ResponseEntity.noContent().build();
    }
}
