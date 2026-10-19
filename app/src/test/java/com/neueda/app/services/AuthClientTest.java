package com.neueda.app.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class AuthClientTest {

    private static final String FORGOT_PASSWORD = "http://auth-service:8082/auth/v1/auth/forgot-password";

    private MockRestServiceServer server;
    private AuthClient authClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://auth-service:8082");
        server = MockRestServiceServer.bindTo(builder).build();
        authClient = new AuthClient(builder.build());
    }

    @Test
    void sendInvite_PostsTheEmailToForgotPassword() {
        server.expect(requestTo(FORGOT_PASSWORD))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json("{\"email\":\"new.user@example.com\"}", true))
            .andRespond(withStatus(HttpStatus.ACCEPTED));

        assertTrue(authClient.sendInvite("new.user@example.com"));
        server.verify();
    }

    @Test
    void sendInvite_AuthServiceError_ReturnsFalseInsteadOfThrowing() {
        server.expect(requestTo(FORGOT_PASSWORD)).andRespond(withServerError());

        assertFalse(authClient.sendInvite("new.user@example.com"));
    }

    @Test
    void sendInvite_AuthServiceUnreachable_ReturnsFalseInsteadOfThrowing() {
        // Nothing listens on port 1; the real constructor, with its timeouts
        assertFalse(new AuthClient("http://localhost:1").sendInvite("new.user@example.com"));
    }
}
