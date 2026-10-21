package com.neueda.trading.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards /internal/**: only a caller with the shared INTERNAL_API_TOKEN (the app) gets in. Without a
 * configured token every internal call is refused, so a missing setting never leaves the journals open.
 */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Internal-Token";

    private final byte[] token;

    public InternalTokenFilter(@Value("${internal.api.token:}") String token) {
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (token.length == 0) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "INTERNAL_API_TOKEN is not set");
            return;
        }
        String given = request.getHeader(HEADER);
        // Constant-time comparison, so response timing does not reveal how much of a guess was right
        if (given == null || !MessageDigest.isEqual(token, given.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }
}
