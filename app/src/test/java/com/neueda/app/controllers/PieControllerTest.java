package com.neueda.app.controllers;

import com.neueda.app.dtos.PieResponse;
import com.neueda.app.dtos.PieSliceRequest;
import com.neueda.app.dtos.PieSliceResponse;
import com.neueda.app.dtos.SavePieRequest;
import com.neueda.app.exceptions.InvalidPieException;
import com.neueda.app.exceptions.PieInUseException;
import com.neueda.app.exceptions.PieNotFoundException;
import com.neueda.app.services.AccountAccess;
import com.neueda.app.services.PieService;
import com.neueda.app.utils.JwtUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PieController.class)
@AutoConfigureMockMvc(addFilters = false)
class PieControllerTest {

    private static final UUID PIE_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PieService pieService;

    @MockitoBean
    private AccountAccess accountAccess;

    @MockitoBean
    private JwtUtil jwtUtil;  // Required by JwtFilter, which is picked up in the web slice

    @BeforeEach
    void setUp() {
        when(accountAccess.currentUser()).thenReturn("jay");
    }

    private String body(String name) throws Exception {
        return objectMapper.writeValueAsString(new SavePieRequest(name, "desc",
            List.of(new PieSliceRequest("AAPL", new BigDecimal("60")))));
    }

    private PieResponse response() {
        return new PieResponse(PIE_ID, "Tech", "desc", "PRIVATE", 1,
            List.of(new PieSliceResponse("AAPL", "Apple Inc.", new BigDecimal("60"))),
            new BigDecimal("40"), List.of(), LocalDateTime.now(), null);
    }

    @Test
    void create_returns201() throws Exception {
        when(pieService.create(eq("jay"), any(SavePieRequest.class))).thenReturn(response());

        mockMvc.perform(post("/v1/pies").contentType(MediaType.APPLICATION_JSON).content(body("Tech")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pieId").value(PIE_ID.toString()))
            .andExpect(jsonPath("$.slices[0].symbol").value("AAPL"))
            .andExpect(jsonPath("$.cashPct").value(40));
    }

    @Test
    void create_blankName_400() throws Exception {
        mockMvc.perform(post("/v1/pies").contentType(MediaType.APPLICATION_JSON).content(body(" ")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void get_unknown_404WithPieNotFound() throws Exception {
        when(pieService.get(PIE_ID, "jay")).thenThrow(new PieNotFoundException("Pie not found: " + PIE_ID));

        mockMvc.perform(get("/v1/pies/" + PIE_ID))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("PIE_NOT_FOUND"));
    }

    @Test
    void update_invalid_422() throws Exception {
        when(pieService.update(eq(PIE_ID), eq("jay"), any(SavePieRequest.class)))
            .thenThrow(new InvalidPieException("Unknown symbol: NOPE"));

        mockMvc.perform(put("/v1/pies/" + PIE_ID).contentType(MediaType.APPLICATION_JSON).content(body("Tech")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("INVALID_PIE"));
    }

    @Test
    void delete_inUse_409() throws Exception {
        doThrow(new PieInUseException("in use")).when(pieService).delete(PIE_ID, "jay");

        mockMvc.perform(delete("/v1/pies/" + PIE_ID))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("PIE_IN_USE"));
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/v1/pies/" + PIE_ID))
            .andExpect(status().isNoContent());

        verify(pieService).delete(PIE_ID, "jay");
    }

    @Test
    void publish_returnsPieWithPublishTime() throws Exception {
        PieResponse published = new PieResponse(PIE_ID, "Tech", "desc", "PUBLIC", 1, List.of(), new BigDecimal("100"),
            List.of(), LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 2, 3, 4));
        when(pieService.publish(PIE_ID, "jay")).thenReturn(published);

        mockMvc.perform(post("/v1/pies/" + PIE_ID + "/publish"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.visibility").value("PUBLIC"))
            .andExpect(jsonPath("$.publishedAt").exists());
    }

    @Test
    void publish_unknown_404() throws Exception {
        when(pieService.publish(PIE_ID, "jay")).thenThrow(new PieNotFoundException("Pie not found: " + PIE_ID));

        mockMvc.perform(post("/v1/pies/" + PIE_ID + "/publish"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("PIE_NOT_FOUND"));
    }

    @Test
    void unpublish_returnsPrivatePie() throws Exception {
        when(pieService.unpublish(PIE_ID, "jay")).thenReturn(response());

        mockMvc.perform(post("/v1/pies/" + PIE_ID + "/unpublish"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.visibility").value("PRIVATE"))
            .andExpect(jsonPath("$.publishedAt").doesNotExist());
    }

}
