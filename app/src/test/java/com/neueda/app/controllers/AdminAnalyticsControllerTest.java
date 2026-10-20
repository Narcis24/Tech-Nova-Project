package com.neueda.app.controllers;

import com.neueda.app.configs.SecurityConfig;
import com.neueda.app.dtos.AdminAnalyticsResponse;
import com.neueda.app.dtos.AnalyticsTotals;
import com.neueda.app.enums.AnalyticsBucketSize;
import com.neueda.app.enums.AnalyticsPeriod;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderType;
import com.neueda.app.services.AdminAnalyticsService;
import com.neueda.app.utils.JwtUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs with the real security chain (JwtFilter + SecurityConfig), like AdminUserControllerTest. */
@WebMvcTest(AdminAnalyticsController.class)
@Import(SecurityConfig.class)
class AdminAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAnalyticsService adminAnalyticsService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @BeforeEach
    void tokens() {
        when(jwtUtil.isTokenValid("admin-token")).thenReturn(true);
        when(jwtUtil.extractUsername("admin-token")).thenReturn("test_admin");
        when(jwtUtil.extractRoles("admin-token")).thenReturn(List.of("ADMIN"));

        when(jwtUtil.isTokenValid("trader-token")).thenReturn(true);
        when(jwtUtil.extractUsername("trader-token")).thenReturn("trader_one");
        when(jwtUtil.extractRoles("trader-token")).thenReturn(List.of("TRADER"));
    }

    @Test
    void analytics_NoToken_Returns401() throws Exception {
        mockMvc.perform(get("/v1/admin/analytics"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verifyNoInteractions(adminAnalyticsService);
    }

    @Test
    void analytics_Trader_Returns403() throws Exception {
        mockMvc.perform(get("/v1/admin/analytics").header("Authorization", "Bearer trader-token"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
            .andExpect(jsonPath("$.message").value("Admin role required"));

        verifyNoInteractions(adminAnalyticsService);
    }

    @Test
    void analytics_Admin_ReturnsTheServicesResponse() throws Exception {
        when(adminAnalyticsService.analytics(any(), any(), any(), any(), any())).thenReturn(sample());

        mockMvc.perform(get("/v1/admin/analytics").header("Authorization", "Bearer admin-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.period").value("LAST_7_DAYS"))
            .andExpect(jsonPath("$.bucketSize").value("DAY"))
            .andExpect(jsonPath("$.totals.tradedValue").value(184250.00))
            .andExpect(jsonPath("$.totals.orders").value(212))
            .andExpect(jsonPath("$.totals.fillRate").value(0.8312))
            .andExpect(jsonPath("$.totals.activeTraders").value(14))
            .andExpect(jsonPath("$.buckets").isArray())
            .andExpect(jsonPath("$.topTradedInstruments").isArray())
            .andExpect(jsonPath("$.assetMix").isArray());
    }

    @Test
    void analytics_NoParameters_UsesLast30DaysAndNoFilters() throws Exception {
        when(adminAnalyticsService.analytics(any(), any(), any(), any(), any())).thenReturn(sample());

        mockMvc.perform(get("/v1/admin/analytics").header("Authorization", "Bearer admin-token"))
            .andExpect(status().isOk());

        verify(adminAnalyticsService).analytics(AnalyticsPeriod.LAST_30_DAYS, null, null, null, null);
    }

    @Test
    void analytics_PassesEveryFilterToTheService() throws Exception {
        when(adminAnalyticsService.analytics(any(), any(), any(), any(), any())).thenReturn(sample());

        mockMvc.perform(get("/v1/admin/analytics")
                .param("period", "TODAY")
                .param("assetClass", "ETF")
                .param("side", "SELL")
                .param("orderType", "LIMIT")
                .param("trader", "alice")
                .header("Authorization", "Bearer admin-token"))
            .andExpect(status().isOk());

        verify(adminAnalyticsService).analytics(AnalyticsPeriod.TODAY, AssetClass.ETF, OrderSide.SELL,
            OrderType.LIMIT, "alice");
    }

    @Test
    void analytics_UnknownPeriod_Returns400() throws Exception {
        mockMvc.perform(get("/v1/admin/analytics").param("period", "FOREVER")
                .header("Authorization", "Bearer admin-token"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
            .andExpect(jsonPath("$.message").value("Invalid value for period"));

        verifyNoInteractions(adminAnalyticsService);
    }

    @Test
    void analytics_UnknownSide_Returns400() throws Exception {
        mockMvc.perform(get("/v1/admin/analytics").param("side", "HOLD")
                .header("Authorization", "Bearer admin-token"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Invalid value for side"));

        verifyNoInteractions(adminAnalyticsService);
    }

    // Enum values are matched exactly, so the UI must send the upper-case names
    @Test
    void analytics_LowerCaseAssetClass_Returns400() throws Exception {
        mockMvc.perform(get("/v1/admin/analytics").param("assetClass", "etf")
                .header("Authorization", "Bearer admin-token"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(adminAnalyticsService);
    }

    private static AdminAnalyticsResponse sample() {
        AnalyticsTotals totals = new AnalyticsTotals(new BigDecimal("184250.00"), 212, new BigDecimal("0.8312"), 14);
        return new AdminAnalyticsResponse(AnalyticsPeriod.LAST_7_DAYS, AnalyticsBucketSize.DAY,
            Instant.parse("2026-10-14T04:00:00Z"), Instant.parse("2026-10-21T04:00:00Z"),
            null, null, totals, null, List.of(), List.of(), List.of(), List.of());
    }
}
