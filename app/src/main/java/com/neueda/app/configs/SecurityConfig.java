package com.neueda.app.configs;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


import com.neueda.app.dtos.ErrorResponse;
import com.neueda.app.filter.JwtFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;
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

    private final JwtFilter jwtFilter;  // Use JwtFilter, not JwtAuthenticationFilter
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtFilter jwtFilter, ObjectMapper objectMapper) {
        this.jwtFilter = jwtFilter;
        this.objectMapper = objectMapper;
    }

    /**
     * JwtFilter is a @Component, so Spring Boot would also run it as a plain servlet filter outside the
     * security chain. Run that way first, it marks the request as filtered and the chain then resets the
     * security context, so the user looks anonymous. It must run only inside the chain.
     */
    @Bean
    public FilterRegistrationBean<JwtFilter> jwtFilterRegistration(JwtFilter filter) {
        FilterRegistrationBean<JwtFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/swagger-ui/**").permitAll()        // Allow Swagger UI
                .requestMatchers("/v3/api-docs/**").permitAll()       // Allow OpenAPI docs
                .requestMatchers("/swagger-ui.html").permitAll()      // Allow Swagger HTML
                // Paths are inside the /api context path, so this is /api/v1/admin/**
                .requestMatchers("/v1/admin/**").hasRole("ADMIN")     // User management: ADMIN role in the JWT
                .requestMatchers("/api/**").authenticated()           // Protect all trading endpoints
                .anyRequest().authenticated()                         // Everything else needs auth
            )
            // A valid token without the role gets 403 in the same JSON shape as the filter's 401
            .exceptionHandling(ex -> ex.accessDeniedHandler((request, response, denied) -> {
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                objectMapper.writeValue(response.getOutputStream(),
                    new ErrorResponse("FORBIDDEN", "Admin role required", 403));
            }))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .csrf(csrf -> csrf.disable());      // Disable CSRF (stateless JWT doesn't need it)
        
        return http.build();
    }
}
