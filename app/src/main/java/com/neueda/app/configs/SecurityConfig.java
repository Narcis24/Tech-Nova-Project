package com.neueda.app.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.neueda.app.configs.JwtAuthenticationFilter;

/**
 * Security Configuration for the Trading Application
 * 
 * This configuration:
 * 1. Registers the JWT authentication filter to validate Bearer tokens
 * 2. Configures which endpoints require authentication
 * 3. Sets up custom authentication entry point for consistent error responses
 * 4. Protects all trading endpoints (/api/*) while allowing public access to docs
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationHandler jwtAuthenticationHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                        JwtAuthenticationHandler jwtAuthenticationHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtAuthenticationHandler = jwtAuthenticationHandler;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configure HTTP security for the trading application
     * 
     * Authorization rules:
     * - /swagger-ui/** → Public (API documentation)
     * - /v3/api-docs/** → Public (OpenAPI spec)
     * - /api/* → Requires valid JWT token
     * 
     * JWT validation happens via JwtAuthenticationFilter
     * If token is missing or invalid, CustomAuthenticationEntryPoint returns ErrorResponse DTO
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/swagger-ui/**").permitAll()        // Allow Swagger UI
                .requestMatchers("/v3/api-docs/**").permitAll()       // Allow OpenAPI docs
                .requestMatchers("/swagger-ui.html").permitAll()      // Allow Swagger HTML
                .requestMatchers("/api/**").authenticated()           // Protect all trading endpoints
                .anyRequest().authenticated()                         // Everything else needs auth
            )
            // Custom error handling for authentication failures (401 Unauthorized)
            .exceptionHandling()
                .authenticationEntryPoint(jwtAuthenticationHandler)
            .and()
            // Add JWT filter BEFORE Spring's default UsernamePasswordAuthenticationFilter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .formLogin().disable()      // Disable form login (we use JWT)
            .httpBasic().disable()      // Disable HTTP Basic auth (we use JWT)
            .csrf().disable();          // Disable CSRF (stateless JWT doesn't need it)
        
        return http.build();
    }
}
