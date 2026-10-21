package com.neueda.app.services;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.neueda.app.dtos.JournalDtos.JournalDay;
import com.neueda.app.dtos.JournalDtos.JournalDaySummary;
import com.neueda.app.dtos.JournalDtos.JournalTimelineEntry;
import com.neueda.app.dtos.JournalDtos.JournalVerifyResult;
import com.neueda.app.exceptions.JournalException;

import lombok.extern.slf4j.Slf4j;

/**
 * Calls the execution engine's internal journal API for the admin journal page, with the shared
 * INTERNAL_API_TOKEN. Engine errors become JournalExceptions with a status the admin can act on.
 */
@Component
@Slf4j
public class EngineClient {

    static final String TOKEN_HEADER = "X-Internal-Token";
    private static final String DAY = "/internal/journal/days/{day}";

    private final RestClient restClient;
    // A verify replays a whole day; the engine stops it after 120 s
    private final RestClient verifyClient;

    @Autowired
    public EngineClient(@Value("${engine.url}") String engineUrl, @Value("${internal.api.token:}") String token) {
        this(client(engineUrl, token, Duration.ofSeconds(10)), client(engineUrl, token, Duration.ofSeconds(150)));
    }

    /** For tests, with RestClients bound to a mock server. */
    EngineClient(RestClient restClient, RestClient verifyClient) {
        this.restClient = restClient;
        this.verifyClient = verifyClient;
    }

    private static RestClient client(String baseUrl, String token, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory)
            .defaultHeader(TOKEN_HEADER, token).build();
    }

    public List<JournalDay> days() {
        return call(() -> restClient.get().uri("/internal/journal/days").retrieve()
            .body(new ParameterizedTypeReference<List<JournalDay>>() { }));
    }

    public JournalDaySummary day(String day) {
        return call(() -> restClient.get().uri(DAY, day).retrieve().body(JournalDaySummary.class));
    }

    public List<JournalTimelineEntry> timeline(String day, String orderId) {
        return call(() -> restClient.get().uri(DAY + "/orders/{orderId}", day, orderId).retrieve()
            .body(new ParameterizedTypeReference<List<JournalTimelineEntry>>() { }));
    }

    /** The day's journal, gzipped. */
    public byte[] download(String day) {
        return call(() -> restClient.get().uri(DAY + "/download", day).retrieve().body(byte[].class));
    }

    public JournalVerifyResult verify(String day) {
        return call(() -> verifyClient.post().uri(DAY + "/verify", day).retrieve().body(JournalVerifyResult.class));
    }

    private interface Call<T> {
        T run();
    }

    private static <T> T call(Call<T> call) {
        try {
            return call.run();
        } catch (RestClientResponseException e) {
            throw translate(e.getStatusCode());
        } catch (ResourceAccessException e) {
            log.warn("Execution engine unreachable for the journal page: {}", e.getMessage());
            throw new JournalException(HttpStatus.SERVICE_UNAVAILABLE, "JOURNAL_UNAVAILABLE",
                "The execution engine cannot be reached; try again in a moment");
        }
    }

    private static JournalException translate(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> new JournalException(HttpStatus.BAD_REQUEST, "INVALID_DAY", "A day looks like 2026-10-20");
            case 404 -> new JournalException(HttpStatus.NOT_FOUND, "JOURNAL_NOT_FOUND",
                "No journal for that day or order");
            case 409 -> new JournalException(HttpStatus.CONFLICT, "REPLAY_BUSY",
                "A replay is already running; try again in a moment");
            case 504 -> new JournalException(HttpStatus.GATEWAY_TIMEOUT, "REPLAY_TIMEOUT",
                "The replay took too long and was stopped");
            default -> {
                // 401 / 503 here mean the shared token is missing or different on the two services
                log.error("Execution engine refused the journal request with {}", status.value());
                yield new JournalException(HttpStatus.SERVICE_UNAVAILABLE, "JOURNAL_UNAVAILABLE",
                    "The journal is not available; check INTERNAL_API_TOKEN on the app and the engine");
            }
        };
    }
}
