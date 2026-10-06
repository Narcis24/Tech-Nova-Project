package com.neueda.app.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;
import com.neueda.app.dtos.AccountResponse;
import com.neueda.app.dtos.CashAmountRequest;
import com.neueda.app.enums.AccountStatus;
import com.neueda.app.exceptions.AccountNotFoundException;
import com.neueda.app.exceptions.InsufficientFundsException;
import com.neueda.app.services.AccountService;
import com.neueda.app.utils.JwtUtil;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@AutoConfigureMockMvc(addFilters = false)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @Test
    void testDeposit_Success() throws Exception {
        AccountResponse response = new AccountResponse("ACC123", "Test Trader", new BigDecimal("150.00"), AccountStatus.ACTIVE);
        when(accountService.depositCash(eq("ACC123"), any(BigDecimal.class))).thenReturn(response);

        mockMvc.perform(post("/v1/accounts/{accountId}/deposit", "ACC123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CashAmountRequest(new BigDecimal("50.00")))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cashBalance").value(150.00));
    }

    @Test
    void testDeposit_AccountNotFound() throws Exception {
        when(accountService.depositCash(eq("FAKE"), any(BigDecimal.class)))
            .thenThrow(new AccountNotFoundException("Account not found: FAKE"));

        mockMvc.perform(post("/v1/accounts/{accountId}/deposit", "FAKE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CashAmountRequest(new BigDecimal("50.00")))))
            .andExpect(status().isNotFound());
    }

    @Test
    void testDeposit_RejectsNonPositiveAmount() throws Exception {
        mockMvc.perform(post("/v1/accounts/{accountId}/deposit", "ACC123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CashAmountRequest(new BigDecimal("-10.00")))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testWithdraw_Success() throws Exception {
        AccountResponse response = new AccountResponse("ACC123", "Test Trader", new BigDecimal("50.00"), AccountStatus.ACTIVE);
        when(accountService.withdrawCash(eq("ACC123"), any(BigDecimal.class))).thenReturn(response);

        mockMvc.perform(post("/v1/accounts/{accountId}/withdraw", "ACC123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CashAmountRequest(new BigDecimal("100.00")))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cashBalance").value(50.00));
    }

    @Test
    void testWithdraw_InsufficientFunds() throws Exception {
        when(accountService.withdrawCash(eq("ACC123"), any(BigDecimal.class)))
            .thenThrow(new InsufficientFundsException("Account ACC123 has $50.00 but order requires $100.00"));

        mockMvc.perform(post("/v1/accounts/{accountId}/withdraw", "ACC123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CashAmountRequest(new BigDecimal("100.00")))))
            .andExpect(status().isBadRequest());
    }
}
