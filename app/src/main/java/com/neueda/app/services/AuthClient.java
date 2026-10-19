package com.neueda.app.services;

import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls auth-service, which owns passwords and sends account emails. The app never sends email itself:
 * an invited user gets the same set-password link as "Forgot password", valid for 30 minutes.
 */
@Component
@Slf4j
public class AuthClient {

    private final RestClient restClient;

    @Autowired
    public AuthClient(@Value("${auth.url}") String authUrl) {
        // Short timeouts: an admin waits on this call, and a missed invite can be resent
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().baseUrl(authUrl).requestFactory(requestFactory).build();
    }

    /** For tests, with a RestClient bound to a mock server. */
    AuthClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Asks auth-service to email a set-password link. Returns false instead of throwing if auth-service
     * cannot be reached or refuses, so a saved user is never reported as a failed create.
     */
    public boolean sendInvite(String email) {
        try {
            restClient.post()
                .uri("/auth/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email))
                .retrieve()
                .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            // No email address in the log
            log.warn("Invite email could not be requested from auth-service: {}", e.getMessage());
            return false;
        }
    }
}
