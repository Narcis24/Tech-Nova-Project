package com.neueda.app.controllers;

import com.neueda.app.services.AccountAccess;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import jakarta.validation.constraints.Size;
import java.util.List;
import com.neueda.app.dtos.PositionResponse;
import com.neueda.app.dtos.PositionMetricsResponse;
import com.neueda.app.services.PositionService;

@RestController
@RequestMapping("/v1/positions")
public class PositionController {
    private final PositionService positionService;
    private final AccountAccess accountAccess;

    public PositionController(PositionService positionService, AccountAccess accountAccess) {
        this.positionService = positionService;
        this.accountAccess = accountAccess;
    }

    @GetMapping("/{accountId}/{symbol}")
    public ResponseEntity<PositionResponse> getPosition(@PathVariable String accountId ,@PathVariable @Size(min = 1, max = 10, message = "Symbol must be between 1 and 10 characters") String symbol){
        accountAccess.requireOwned(accountId);
        PositionResponse positionResponse = positionService.getPosition(accountId,symbol);
        return ResponseEntity.ok(positionResponse);
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<List<PositionResponse>> getAccountPositions(@PathVariable String accountId){
        accountAccess.requireOwned(accountId);
       List<PositionResponse> positionResponses = positionService.getAccountPositions(accountId);
        return ResponseEntity.ok(positionResponses);
    } 

    @GetMapping("/{accountId}/{symbol}/metrics")
    public ResponseEntity<PositionMetricsResponse> getPositionMetrics(@PathVariable String accountId,@PathVariable @Size(min = 1, max = 10, message = "Symbol must be between 1 and 10 characters") String symbol ){
        accountAccess.requireOwned(accountId);
        PositionMetricsResponse positionMetricsResponse = positionService.getPositionMetrics(accountId,symbol);
        return ResponseEntity.ok(positionMetricsResponse);
    }

}