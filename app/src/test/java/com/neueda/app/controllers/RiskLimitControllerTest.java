package com.neueda.app.controllers;

import com.neueda.app.dtos.RiskLimitResponse;
import com.neueda.app.dtos.UpdateRiskLimitRequest;
import com.neueda.app.enums.LimitSource;
import com.neueda.app.enums.RiskRule;
import com.neueda.app.exceptions.AccountAccessDeniedException;
import com.neueda.app.services.AccountAccess;
import com.neueda.app.services.RiskLimitService;
import com.neueda.app.utils.JwtUtil;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RiskLimitController.class)
@AutoConfigureMockMvc(addFilters = false)
class RiskLimitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AccountAccess accountAccess;  // permits everything unless a test says otherwise

    @MockitoBean
    private RiskLimitService riskLimitService;

    @MockitoBean
    private JwtUtil jwtUtil;  // Required by JwtFilter, which is picked up in the web slice

    @Test
    void testGetLimits_Success() throws Exception {
        when(riskLimitService.getLimits("ACC123")).thenReturn(List.of(
            new RiskLimitResponse(RiskRule.SINGLE_INSTRUMENT, new BigDecimal("25"), "ABOVE", true, LimitSource.CUSTOM),
            new RiskLimitResponse(RiskRule.CASH_FLOOR, new BigDecimal("20"), "BELOW", true, LimitSource.DEFAULT)));

        mockMvc.perform(get("/v1/accounts/ACC123/risk-limits"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].rule").value("SINGLE_INSTRUMENT"))
            .andExpect(jsonPath("$[0].source").value("CUSTOM"))
            .andExpect(jsonPath("$[1].direction").value("BELOW"));
    }

    @Test
    void testUpdateLimit_Success() throws Exception {
        when(riskLimitService.updateLimit(eq("ACC123"), eq(RiskRule.SINGLE_INSTRUMENT), any(UpdateRiskLimitRequest.class)))
            .thenReturn(new RiskLimitResponse(RiskRule.SINGLE_INSTRUMENT, new BigDecimal("25"), "ABOVE", true, LimitSource.CUSTOM));

        mockMvc.perform(put("/v1/accounts/ACC123/risk-limits/SINGLE_INSTRUMENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateRiskLimitRequest(new BigDecimal("25"), true))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.source").value("CUSTOM"))
            .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void testUpdateLimit_ThresholdOutOfRange() throws Exception {
        mockMvc.perform(put("/v1/accounts/ACC123/risk-limits/SINGLE_INSTRUMENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateRiskLimitRequest(new BigDecimal("150"), true))))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(riskLimitService);
    }

    @Test
    void testUpdateLimit_UnknownRule() throws Exception {
        mockMvc.perform(put("/v1/accounts/ACC123/risk-limits/NOT_A_RULE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateRiskLimitRequest(new BigDecimal("25"), true))))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(riskLimitService);
    }

    @Test
    void testResetLimit_Success() throws Exception {
        when(riskLimitService.resetLimit("ACC123", RiskRule.CASH_FLOOR))
            .thenReturn(new RiskLimitResponse(RiskRule.CASH_FLOOR, new BigDecimal("20"), "BELOW", true, LimitSource.DEFAULT));

        mockMvc.perform(delete("/v1/accounts/ACC123/risk-limits/CASH_FLOOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.source").value("DEFAULT"));
    }

    @Test
    void testGetLimits_NotYourAccount() throws Exception {
        doThrow(new AccountAccessDeniedException("Account ACC123 does not belong to you"))
            .when(accountAccess).requireOwned("ACC123");

        mockMvc.perform(get("/v1/accounts/ACC123/risk-limits"))
            .andExpect(status().isForbidden());

        verifyNoInteractions(riskLimitService);
    }
}
