package com.nexswitch.app.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// LEARN: API key auth is simpler than OAuth2 for M2M (machine-to-machine) flows.
//        Payment terminals don't have browsers or user agents; they send a pre-shared key.
//        OAuth2 would require a token exchange round-trip on every cold start — wasteful for POS.
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);
    private static final String API_KEY_HEADER = "X-API-Key";

    private final String expectedApiKey;

    public ApiKeyAuthFilter(
            @Value("${security.api-key:dev-api-key-change-me}") String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Permit actuator health and info without API key (used by K8s probes and nginx)
        if (path.startsWith("/actuator/health") || path.startsWith("/actuator/info")) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedKey = request.getHeader(API_KEY_HEADER);
        if (expectedApiKey.equals(providedKey)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("api_key.rejected path={} remoteAddr={}", path, request.getRemoteAddr());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"unauthorized\"}");
    }
}
