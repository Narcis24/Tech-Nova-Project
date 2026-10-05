package com.neueda.app.controllers;

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

    public PositionController(PositionService positionService) {
        this.positionService = positionService;
    }

    @GetMapping("/{accountId}/{symbol}")
    public ResponseEntity<PositionResponse> getPosition(@PathVariable String accountId ,@PathVariable @Size(min = 1, max = 10, message = "Symbol must be between 1 and 10 characters") String symbol){
        PositionResponse positionResponse = positionService.getPosition(accountId,symbol);
        return ResponseEntity.ok(positionResponse);
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<List<PositionResponse>> getAccountPositions(@PathVariable String accountId){
       List<PositionResponse> positionResponses = positionService.getAccountPositions(accountId);
        return ResponseEntity.ok(positionResponses);
    } 

    @GetMapping("/{accountId}/{symbol}/metrics")
    public ResponseEntity<PositionMetricsResponse> getPositionMetrics(@PathVariable String accountId,@PathVariable @Size(min = 1, max = 10, message = "Symbol must be between 1 and 10 characters") String symbol ){
        PositionMetricsResponse positionMetricsResponse = positionService.getPositionMetrics(accountId,symbol);
        return ResponseEntity.ok(positionMetricsResponse);
    }

}