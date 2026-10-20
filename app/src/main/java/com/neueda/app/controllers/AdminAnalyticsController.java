package com.neueda.app.controllers;

import com.neueda.app.dtos.AdminAnalyticsResponse;
import com.neueda.app.enums.AnalyticsPeriod;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderType;
import com.neueda.app.services.AdminAnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Trading activity for the admin home page. SecurityConfig restricts every /v1/admin/** route to the ADMIN role. */
@RestController
@RequestMapping("/v1/admin/analytics")
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;

    public AdminAnalyticsController(AdminAnalyticsService adminAnalyticsService) {
        this.adminAnalyticsService = adminAnalyticsService;
    }

    /**
     * Totals, chart points, top traded instruments and asset mix for a period, after the filters.
     * Every filter is optional; leaving one out includes everything.
    */
    @GetMapping
    public ResponseEntity<AdminAnalyticsResponse> analytics(
        @RequestParam(defaultValue = "LAST_30_DAYS") AnalyticsPeriod period,
        @RequestParam(required = false) AssetClass assetClass,
        @RequestParam(required = false) OrderSide side,
        @RequestParam(required = false) OrderType orderType,
        @RequestParam(required = false) String trader) {
            
            return ResponseEntity.ok(adminAnalyticsService.analytics(period, assetClass, side, orderType, trader));
    }
}