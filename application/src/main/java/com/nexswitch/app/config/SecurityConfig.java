package com.nexswitch.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

// LEARN: SecurityFilterChain replaces WebSecurityConfigurerAdapter (removed in Spring Security 6).
//        Each service gets a filter chain; the DSL is the single source of truth for HTTP security rules.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final List<String> allowedOrigins;
    private final ApiKeyAuthFilter apiKeyAuthFilter;

    // LEARN: CORS (Cross-Origin Resource Sharing) — browsers block XHR/fetch to a different origin
    //        unless the server replies with Access-Control-Allow-Origin. Without this, the 3 Next.js
    //        frontends on localhost:3000/3001/3002 cannot call the REST API from a browser tab.
    public SecurityConfig(
            @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:3001,http://localhost:3002,http://localhost:3003}")
            List<String> allowedOrigins,
            ApiKeyAuthFilter apiKeyAuthFilter) {
        this.allowedOrigins   = allowedOrigins;
        this.apiKeyAuthFilter = apiKeyAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // LEARN: API key filter runs before Spring Security's auth chain — unauthenticated
            //        requests are rejected with 401 before reaching any business endpoint.
            .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
            // Stateless REST API — no HTTP session, no CSRF token needed
            // LEARN: CSRF protection guards browser-based session cookies. Stateless JWTs/API-keys
            //        are not vulnerable to CSRF because the browser never auto-sends them.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // Disable Spring Security's built-in Basic Auth popup — we will add JWT/API-key auth
            // in a dedicated ticket once the auth service is wired.
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .authorizeHttpRequests(auth -> auth
                // Actuator health/info — permit for liveness/readiness probes
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                // All other actuator endpoints — restrict to service-internal calls only
                // (management port separation tracked in N9; revisit when ports are split)
                .requestMatchers("/actuator/**").denyAll()
                // All business API endpoints — permitted until JWT/API-key auth ticket lands
                .anyRequest().permitAll()
            );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(allowedOrigins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("X-Trace-Id", "X-Request-Id"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
