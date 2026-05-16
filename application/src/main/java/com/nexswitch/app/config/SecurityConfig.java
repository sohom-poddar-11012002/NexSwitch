package com.nexswitch.app.config;

import org.springframework.context.annotation.Configuration;

// LEARN: Spring Security wraps every HTTP request in a chain of servlet filters evaluated in order.
//        The last filter (FilterSecurityInterceptor) enforces access rules — anything before it
//        can short-circuit the chain (e.g. JWT validation, CORS preflight rejection).
/** Shared SecurityConfig — populated as infrastructure is wired in Sprint Week 1. */
@Configuration
public class SecurityConfig {
}
