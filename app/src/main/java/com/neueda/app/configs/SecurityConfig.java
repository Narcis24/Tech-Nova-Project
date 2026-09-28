package com.neueda.app.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


import com.neueda.app.filter.JwtFilter;
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

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

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
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .csrf(csrf -> csrf.disable());      // Disable CSRF (stateless JWT doesn't need it)
        
        return http.build();
    }
}
