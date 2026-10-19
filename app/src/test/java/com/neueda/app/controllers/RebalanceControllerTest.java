package com.neueda.app.controllers;

import com.neueda.app.dtos.RebalanceLegResponse;
import com.neueda.app.dtos.RebalanceRunResponse;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.enums.RebalanceStatus;
import com.neueda.app.exceptions.AccountAccessDeniedException;
import com.neueda.app.exceptions.RebalanceConflictException;
import com.neueda.app.exceptions.RebalanceRunNotFoundException;
import com.neueda.app.services.AccountAccess;
import com.neueda.app.services.RebalanceRunService;
import com.neueda.app.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RebalanceController.class)
@AutoConfigureMockMvc(addFilters = false)
class RebalanceControllerTest {

    private static final UUID RUN_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final String START_URL = "/v1/accounts/ACC-1/rebalance";
    private static final String RUN_URL = "/v1/rebalance-runs/" + RUN_ID;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RebalanceRunService rebalanceRunService;

    @MockitoBean
    private AccountAccess accountAccess;

    @MockitoBean
    private JwtUtil jwtUtil;  // Required by JwtFilter, which is picked up in the web slice

    private RebalanceRunResponse run(RebalanceStatus status) {
        return new RebalanceRunResponse(RUN_ID, "ACC-1", UUID.randomUUID(), 2, status, true, "SELL GOOG: No quote",
            Instant.parse("2026-10-19T10:00:00Z"), Instant.parse("2026-10-19T10:01:00Z"),
            List.of(new RebalanceLegResponse(ORDER_ID, "SELL", "GOOG", "SELL", 50, OrderStatus.REJECTED, "No quote")));
    }

    @Test
    void start_returns202WithTheRun() throws Exception {
        when(rebalanceRunService.start("ACC-1")).thenReturn(run(RebalanceStatus.SELLING));

        mockMvc.perform(post(START_URL))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.runId").value(RUN_ID.toString()))
            .andExpect(jsonPath("$.accountId").value("ACC-1"))
            .andExpect(jsonPath("$.targetVersion").value(2))
            .andExpect(jsonPath("$.status").value("SELLING"))
            .andExpect(jsonPath("$.waitingForMarketOpen").value(true))
            .andExpect(jsonPath("$.failureSummary").value("SELL GOOG: No quote"))
            .andExpect(jsonPath("$.legs[0].orderId").value(ORDER_ID.toString()))
            .andExpect(jsonPath("$.legs[0].phase").value("SELL"))
            .andExpect(jsonPath("$.legs[0].symbol").value("GOOG"))
            .andExpect(jsonPath("$.legs[0].side").value("SELL"))
            .andExpect(jsonPath("$.legs[0].quantity").value(50))
            .andExpect(jsonPath("$.legs[0].status").value("REJECTED"))
            .andExpect(jsonPath("$.legs[0].failureReason").value("No quote"));
        verify(accountAccess).requireOwned("ACC-1");
    }

    @Test
    void start_notOwnedAccount_403() throws Exception {
        doThrow(new AccountAccessDeniedException("ACC-1")).when(accountAccess).requireOwned("ACC-1");

        mockMvc.perform(post(START_URL)).andExpect(status().isForbidden());
        verifyNoInteractions(rebalanceRunService);
    }

    @Test
    void start_runAlreadyActive_409() throws Exception {
        when(rebalanceRunService.start("ACC-1")).thenThrow(new RebalanceConflictException("already running"));

        mockMvc.perform(post(START_URL))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("REBALANCE_CONFLICT"));
    }

    @Test
    void get_returnsTheRun() throws Exception {
        when(rebalanceRunService.get(RUN_ID)).thenReturn(run(RebalanceStatus.PARTIAL));

        mockMvc.perform(get(RUN_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PARTIAL"));
        verify(accountAccess).requireOwnedRun(RUN_ID);
    }

    @Test
    void get_notOwnedRun_403() throws Exception {
        doThrow(new AccountAccessDeniedException("ACC-1")).when(accountAccess).requireOwnedRun(RUN_ID);

        mockMvc.perform(get(RUN_URL)).andExpect(status().isForbidden());
        verifyNoInteractions(rebalanceRunService);
    }

    @Test
    void get_unknownRun_404() throws Exception {
        doThrow(new RebalanceRunNotFoundException("nope")).when(accountAccess).requireOwnedRun(RUN_ID);

        mockMvc.perform(get(RUN_URL))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("REBALANCE_RUN_NOT_FOUND"));
    }

    @Test
    void cancel_returnsTheCancelledRun() throws Exception {
        when(rebalanceRunService.cancel(RUN_ID)).thenReturn(run(RebalanceStatus.CANCELLED));

        mockMvc.perform(post(RUN_URL + "/cancel"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
        verify(accountAccess).requireOwnedRun(RUN_ID);
    }

    @Test
    void cancel_notOwnedRun_403() throws Exception {
        doThrow(new AccountAccessDeniedException("ACC-1")).when(accountAccess).requireOwnedRun(RUN_ID);

        mockMvc.perform(post(RUN_URL + "/cancel")).andExpect(status().isForbidden());
        verifyNoInteractions(rebalanceRunService);
    }

    @Test
    void cancel_alreadyEnded_409() throws Exception {
        when(rebalanceRunService.cancel(RUN_ID)).thenThrow(new RebalanceConflictException("already COMPLETED"));

        mockMvc.perform(post(RUN_URL + "/cancel")).andExpect(status().isConflict());
    }
}
