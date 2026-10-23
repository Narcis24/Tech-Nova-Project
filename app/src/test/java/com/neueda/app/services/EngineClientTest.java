package com.neueda.app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.neueda.app.exceptions.JournalException;

class EngineClientTest {

    private static final String ENGINE = "http://execution-engine:8082";

    private MockRestServiceServer server;
    private EngineClient engineClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(ENGINE).defaultHeader("X-Internal-Token", "secret");
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        engineClient = new EngineClient(client, client);
    }

    @Test
    void sendsTheSharedTokenAndReadsTheDays() {
        server.expect(requestTo(ENGINE + "/internal/journal/days"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-Internal-Token", "secret"))
            .andRespond(withSuccess("[{\"day\":\"2026-10-20\",\"sizeBytes\":4200000}]", MediaType.APPLICATION_JSON));

        assertEquals("2026-10-20", engineClient.days().getFirst().day());
        server.verify();
    }

    @Test
    void readsTheLatestQuote() {
        server.expect(requestTo(ENGINE + "/internal/quotes/AAPL"))
            .andRespond(withSuccess("{\"symbol\":\"AAPL\",\"bid\":229.10,\"ask\":229.14,"
                + "\"asOf\":\"2026-10-22T19:58:00Z\"}", MediaType.APPLICATION_JSON));

        assertEquals(new BigDecimal("229.14"), engineClient.quote("AAPL").orElseThrow().ask());
    }

    @Test
    void aMissingOrUnreachableQuoteIsEmptyNotAnError() {
        server.expect(requestTo(ENGINE + "/internal/quotes/MSFT")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(ENGINE + "/internal/quotes/TSLA")).andRespond(withException(new IOException("down")));

        assertTrue(engineClient.quote("MSFT").isEmpty());
        assertTrue(engineClient.quote("TSLA").isEmpty());
    }

    @Test
    void verifyPostsToTheDay() {
        server.expect(requestTo(ENGINE + "/internal/journal/days/2026-10-20/verify"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"day\":\"2026-10-20\",\"engineRuns\":1,\"orders\":3,\"quotes\":700,"
                + "\"decisions\":4,\"matches\":true,\"differences\":[]}", MediaType.APPLICATION_JSON));

        assertEquals(true, engineClient.verify("2026-10-20").matches());
    }

    @Test
    void anUnknownDayIsNotFound() {
        server.expect(requestTo(ENGINE + "/internal/journal/days/2026-01-01")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        JournalException e = assertThrows(JournalException.class, () -> engineClient.day("2026-01-01"));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        assertEquals("JOURNAL_NOT_FOUND", e.getErrorCode());
    }

    @Test
    void aSecondReplayIsBusy() {
        server.expect(requestTo(ENGINE + "/internal/journal/days/2026-10-20/verify")).andRespond(withStatus(HttpStatus.CONFLICT));

        assertEquals("REPLAY_BUSY", assertThrows(JournalException.class, () -> engineClient.verify("2026-10-20")).getErrorCode());
    }

    @Test
    void aWrongTokenOrADownEngineIsUnavailable() {
        server.expect(requestTo(ENGINE + "/internal/journal/days")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
            assertThrows(JournalException.class, () -> engineClient.days()).getStatus());

        server.reset();
        server.expect(requestTo(ENGINE + "/internal/journal/days")).andRespond(withException(new IOException("refused")));
        assertEquals("JOURNAL_UNAVAILABLE", assertThrows(JournalException.class, () -> engineClient.days()).getErrorCode());
    }
}
