package com.neueda.app.controllers;

import com.neueda.app.dtos.AccountPieResponse;
import com.neueda.app.dtos.CopyPieRequest;
import com.neueda.app.dtos.LinkPieRequest;
import com.neueda.app.dtos.PreviewLeg;
import com.neueda.app.dtos.RebalancePreviewResponse;
import com.neueda.app.dtos.RebalanceRunResponse;
import com.neueda.app.dtos.SliceDrift;
import com.neueda.app.enums.RebalanceStatus;
import com.neueda.app.exceptions.AccountAccessDeniedException;
import com.neueda.app.exceptions.PieNotFoundException;
import com.neueda.app.exceptions.PieNotLinkedException;
import com.neueda.app.exceptions.RebalanceConflictException;
import com.neueda.app.services.AccountAccess;
import com.neueda.app.services.AccountPieService;
import com.neueda.app.utils.JwtUtil;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountPieController.class)
@AutoConfigureMockMvc(addFilters = false)
class AccountPieControllerTest {

    private static final UUID PIE_ID = UUID.randomUUID();
    private static final String URL = "/v1/accounts/ACC-1/pie";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AccountPieService accountPieService;

    @MockitoBean
    private AccountAccess accountAccess;

    @MockitoBean
    private JwtUtil jwtUtil;  // Required by JwtFilter, which is picked up in the web slice

    private AccountPieResponse response() {
        return new AccountPieResponse("ACC-1", PIE_ID, "Tech", "OWN", null, 1,
            List.of(new SliceDrift("AAPL", new BigDecimal("60"), new BigDecimal("50"), new BigDecimal("10"))),
            new BigDecimal("10"), true, false, null);
    }

    @Test
    void link_returns200() throws Exception {
        when(accountAccess.currentUser()).thenReturn("jay");
        when(accountPieService.link("ACC-1", "jay", PIE_ID)).thenReturn(response());

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LinkPieRequest(PIE_ID))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pieName").value("Tech"))
            .andExpect(jsonPath("$.drift[0].symbol").value("AAPL"))
            .andExpect(jsonPath("$.maxDriftPct").value(10));
        verify(accountAccess).requireOwned("ACC-1");
    }

    @Test
    void link_notOwnedAccount_403() throws Exception {
        doThrow(new AccountAccessDeniedException("ACC-1")).when(accountAccess).requireOwned("ACC-1");

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LinkPieRequest(PIE_ID))))
            .andExpect(status().isForbidden());
        verifyNoInteractions(accountPieService);
    }

    @Test
    void get_unlinked_404PieNotLinked() throws Exception {
        when(accountPieService.get("ACC-1")).thenThrow(new PieNotLinkedException("not linked"));

        mockMvc.perform(get(URL))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("PIE_NOT_LINKED"));
    }

    @Test
    void unlink_204() throws Exception {
        mockMvc.perform(delete(URL))
            .andExpect(status().isNoContent());

        verify(accountPieService).unlink("ACC-1");
    }

    @Test
    void preview_returnsLegs() throws Exception {
        PreviewLeg leg = new PreviewLeg("AAPL", true, 0, new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("60"), new BigDecimal("600"), new BigDecimal("600"), "BUY", 6, new BigDecimal("600"));
        when(accountPieService.preview("ACC-1")).thenReturn(new RebalancePreviewResponse("ACC-1", PIE_ID, 1,
            new BigDecimal("1000"), new BigDecimal("1000"), new BigDecimal("400"), new BigDecimal("40"),
            List.of(leg), List.of()));

        mockMvc.perform(post(URL + "/preview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.legs[0].action").value("BUY"))
            .andExpect(jsonPath("$.legs[0].difference").value(600))
            .andExpect(jsonPath("$.legs[0].estimatedValue").value(600))
            .andExpect(jsonPath("$.cashTargetPct").value(40));
        verify(accountAccess).requireOwned("ACC-1");
    }

    // ---------- copying ----------

    private static final UUID RUN_ID = UUID.randomUUID();

    private RebalanceRunResponse run() {
        return new RebalanceRunResponse(RUN_ID, "ACC-1", PIE_ID, 3, RebalanceStatus.SELLING, false, null,
            Instant.now(), Instant.now(), List.of());
    }

    @Test
    void copyPreview_returnsThePreview_andIsForTheCaller() throws Exception {
        when(accountAccess.currentUser()).thenReturn("jay");
        when(accountPieService.previewCopy("ACC-1", "jay", PIE_ID)).thenReturn(new RebalancePreviewResponse(
            "ACC-1", PIE_ID, 3, new BigDecimal("1000"), new BigDecimal("1000"), new BigDecimal("400"),
            new BigDecimal("40"), List.of(), List.of()));

        mockMvc.perform(post(URL + "/copy-preview").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CopyPieRequest(PIE_ID))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(3));
        verify(accountAccess).requireOwned("ACC-1");
    }

    @Test
    void copy_202WithTheRun_afterConfirmation() throws Exception {
        when(accountAccess.currentUser()).thenReturn("jay");
        when(accountPieService.copy("ACC-1", "jay", PIE_ID)).thenReturn(run());

        mockMvc.perform(post(URL + "/copy").contentType(MediaType.APPLICATION_JSON)
                .content("{\"pieId\":\"" + PIE_ID + "\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.runId").value(RUN_ID.toString()))
            .andExpect(jsonPath("$.targetVersion").value(3));
    }

    @Test
    void copy_noPieId_400() throws Exception {
        mockMvc.perform(post(URL + "/copy").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        verify(accountPieService, never()).copy(any(), any(), any());
    }

    @Test
    void copy_ownPie409_privatePie404() throws Exception {
        when(accountAccess.currentUser()).thenReturn("jay");
        when(accountPieService.copy("ACC-1", "jay", PIE_ID))
            .thenThrow(new RebalanceConflictException("own")).thenThrow(new PieNotFoundException("private"));
        String body = objectMapper.writeValueAsString(new CopyPieRequest(PIE_ID));

        mockMvc.perform(post(URL + "/copy").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict());
        mockMvc.perform(post(URL + "/copy").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("PIE_NOT_FOUND"));
    }

    @Test
    void copy_notOwnedAccount_403() throws Exception {
        doThrow(new AccountAccessDeniedException("ACC-1")).when(accountAccess).requireOwned("ACC-1");

        mockMvc.perform(post(URL + "/copy").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CopyPieRequest(PIE_ID))))
            .andExpect(status().isForbidden());
        mockMvc.perform(post(URL + "/copy-preview").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CopyPieRequest(PIE_ID))))
            .andExpect(status().isForbidden());
        mockMvc.perform(post(URL + "/make-mine")).andExpect(status().isForbidden());
        verifyNoInteractions(accountPieService);
    }

    @Test
    void makeMine_returnsTheRelinkedAccountPie() throws Exception {
        when(accountAccess.currentUser()).thenReturn("jay");
        when(accountPieService.makeMine("ACC-1", "jay")).thenReturn(response());

        mockMvc.perform(post(URL + "/make-mine"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("OWN"));
    }
}
