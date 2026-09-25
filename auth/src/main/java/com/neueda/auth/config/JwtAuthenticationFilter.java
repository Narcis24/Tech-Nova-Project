package com.neueda.auth.filter;

import com.neueda.auth.util.JwtUtil;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

/**
 * JWT Authentication Filter
 * 
 * This filter intercepts every HTTP request and validates the JWT token in the Authorization header.
 * If the token is valid, it sets the user as authenticated in Spring Security's SecurityContext.
 * If the token is missing or invalid, it does nothing - Spring Security will reject the request.
 * 
 * Flow:
 * 1. Request arrives at the application
 * 2. This filter runs BEFORE any controller
 * 3. Extracts JWT token from "Authorization: Bearer <token>" header
 * 4. Validates the token using JwtUtil
 * 5. If valid: extracts username and sets authentication in SecurityContext
 * 6. Request continues to controller (now recognized as authenticated)
 * 7. If invalid/missing: request continues but SecurityContext has no authentication
 *    → Spring Security's @Secured/permitAll rules reject it with 401 Unauthorized
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    /**
     * This method is called for EVERY HTTP request (except static resources)
     * OncePerRequestFilter ensures it runs exactly once per request
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        try {
            String token = extractToken(request);
            
            if (token != null && jwtUtil.isTokenValid(token)) {
                
                // STEP 3: Extract the username from inside the token
                // The token contains encrypted data (claims) including the username
                String username = jwtUtil.extractUsername(token);
                
                // STEP 4: Create an Authentication object
                // This tells Spring Security: "This request is from a valid user"
                UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(
                        username,              // The authenticated user's identity
                        null,                  // No password needed - token proves identity
                        new ArrayList<>()      // Empty list of authorities/roles
                    );
                
                // STEP 5: Store authentication in SecurityContext
                // SecurityContext = Spring Security's place to store "who is currently authenticated"
                // Now other parts of the app know: this request is from username "john_doe"
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            // If token is missing or invalid, we do nothing
            // → SecurityContext stays empty → request will be rejected by authorization rules
            
        } catch (Exception e) {
            // If anything goes wrong parsing the token, just log it and continue
            // Don't crash the filter - let Spring Security handle rejection
            logger.error("Failed to process JWT token", e);
        }
        
        // STEP 6: Continue the request to the next filter/controller
        filterChain.doFilter(request, response);
    }

    /**
     * Extract the Bearer token from the Authorization header
     * 
     * @param request the HTTP request
     * @return the JWT token, or null if not found or wrong format
     */
    private String extractToken(HttpServletRequest request) {

        String authHeader = request.getHeader("Authorization");
        
        // Check if header exists and starts with "Bearer " (7 characters including space)
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // Return just the token part (everything after "Bearer ")
            return authHeader.substring(7);
        }
        
        // Return null if header missing or doesn't start with "Bearer"
        return null;
    }
}