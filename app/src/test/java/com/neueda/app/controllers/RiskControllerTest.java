package com.neueda.app.controllers;

import com.neueda.app.dtos.PlaceOrderRequest;
import com.neueda.app.dtos.RiskWarning;
import com.neueda.app.enums.LimitSource;
import com.neueda.app.enums.RiskRule;
import com.neueda.app.exceptions.AccountAccessDeniedException;
import com.neueda.app.services.AccountAccess;
import com.neueda.app.services.RiskService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RiskController.class)
@AutoConfigureMockMvc(addFilters = false)
class RiskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AccountAccess accountAccess;  // permits everything unless a test says otherwise

    @MockitoBean
    private RiskService riskService;

    @MockitoBean
    private JwtUtil jwtUtil;  // Required by JwtFilter, which is picked up in the web slice

    @Test
    void testCheck_ReturnsWarnings() throws Exception {
        RiskWarning warning = new RiskWarning(RiskRule.SINGLE_INSTRUMENT, "AAPL", new BigDecimal("25.22"),
            new BigDecimal("20"), LimitSource.DEFAULT,
            "After this order, AAPL would be 25.2% of your portfolio. Your limit is 20%.");
        when(riskService.check(any(PlaceOrderRequest.class))).thenReturn(List.of(warning));

        mockMvc.perform(post("/v1/risk/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buyRequest())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].rule").value("SINGLE_INSTRUMENT"))
            .andExpect(jsonPath("$[0].subject").value("AAPL"))
            .andExpect(jsonPath("$[0].limitSource").value("DEFAULT"))
            .andExpect(jsonPath("$[0].message").value("After this order, AAPL would be 25.2% of your portfolio. Your limit is 20%."));
    }

    @Test
    void testCheck_NoWarnings() throws Exception {
        when(riskService.check(any(PlaceOrderRequest.class))).thenReturn(List.of());

        mockMvc.perform(post("/v1/risk/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buyRequest())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testCheck_InvalidRequest() throws Exception {
        PlaceOrderRequest request = buyRequest();
        request.setQuantity(0);

        mockMvc.perform(post("/v1/risk/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(riskService);
    }

    @Test
    void testCheck_NotYourAccount() throws Exception {
        doThrow(new AccountAccessDeniedException("Account ACC123 does not belong to you"))
            .when(accountAccess).requireOwned("ACC123");

        mockMvc.perform(post("/v1/risk/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buyRequest())))
            .andExpect(status().isForbidden());

        verifyNoInteractions(riskService);
    }

    private static PlaceOrderRequest buyRequest() {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setAccountId("ACC123");
        request.setSymbol("AAPL");
        request.setSide("BUY");
        request.setOrderType("MARKET");
        request.setQuantity(60);
        request.setIdempotencyKey("key-1");
        return request;
    }
}
