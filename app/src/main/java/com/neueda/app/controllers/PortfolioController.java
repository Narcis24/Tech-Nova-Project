
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
import com.neueda.app.dtos.PortfolioSnapshotResponse;
import com.neueda.app.dtos.PortfolioMetricResponse;
import com.neueda.app.services.PortfolioService;
import jakarta.validation.Valid;


@RestController
@RequestMapping("/v1/portfolio")
public class PortfolioController {
    
    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/{accountId}/snapshot")
    public ResponseEntity<PortfolioSnapshotResponse> getPortfolioSnapshot(@PathVariable String accoundId) {
        
    }


}
