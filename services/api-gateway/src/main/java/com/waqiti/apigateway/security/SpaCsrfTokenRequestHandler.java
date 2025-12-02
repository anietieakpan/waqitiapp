package com.waqiti.apigateway.security;

import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestHandler;
import org.springframework.security.web.server.csrf.XorServerCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.function.Supplier;

/**
 * SPA CSRF Token Request Handler
 *
 * Provides production-ready CSRF protection for Single Page Applications (SPAs)
 * that use cookie-based CSRF tokens with the Double Submit Cookie pattern.
 *
 * This handler:
 * - Works with React, Angular, Vue.js, and other SPA frameworks
 * - Uses BREACH attack protection via XOR masking
 * - Validates CSRF tokens from custom headers (X-XSRF-TOKEN)
 * - Falls back to parameter-based validation if needed
 * - Thread-safe and reactive (WebFlux compatible)
 *
 * The CSRF token flow:
 * 1. Server generates token and sets it in cookie (XSRF-TOKEN)
 * 2. Frontend reads token from cookie
 * 3. Frontend sends token in X-XSRF-TOKEN header for state-changing requests
 * 4. Server validates token matches cookie value
 *
 * @author Waqiti Platform Team - Security Engineering
 * @version 1.0.0
 * @since 2025-10-26
 */
public class SpaCsrfTokenRequestHandler implements ServerCsrfTokenRequestHandler {

    private final XorServerCsrfTokenRequestAttributeHandler delegate =
        new XorServerCsrfTokenRequestAttributeHandler();

    /**
     * Handle CSRF token resolution for incoming requests
     *
     * This method:
     * - Extracts CSRF token from X-XSRF-TOKEN header (SPA standard)
     * - Falls back to form parameter if header not present
     * - Validates token using XOR algorithm to prevent BREACH attacks
     *
     * @param exchange Current server web exchange
     * @param csrfToken CSRF token from cookie
     */
    @Override
    public void handle(ServerWebExchange exchange, Supplier<Mono<CsrfToken>> csrfToken) {
        /*
         * Always use XorCsrfTokenRequestAttributeHandler to provide BREACH protection
         * XOR masking prevents BREACH attack by ensuring the token value changes
         * on each request while maintaining the ability to validate it.
         */
        delegate.handle(exchange, csrfToken);
    }

    /**
     * Resolve CSRF token from request
     *
     * Priority order:
     * 1. X-XSRF-TOKEN header (SPA standard)
     * 2. X-CSRF-TOKEN header (alternative)
     * 3. _csrf parameter (form fallback)
     *
     * @param exchange Current server web exchange
     * @return Mono containing the CSRF token string, or empty if not found
     */
    @Override
    public Mono<String> resolveCsrfTokenValue(ServerWebExchange exchange, CsrfToken csrfToken) {
        // First, try to get token from X-XSRF-TOKEN header (JavaScript convention)
        String headerToken = exchange.getRequest().getHeaders().getFirst("X-XSRF-TOKEN");

        if (StringUtils.hasText(headerToken)) {
            return Mono.just(headerToken);
        }

        // Fallback to X-CSRF-TOKEN header (alternative naming)
        String altHeaderToken = exchange.getRequest().getHeaders().getFirst("X-CSRF-TOKEN");

        if (StringUtils.hasText(altHeaderToken)) {
            return Mono.just(altHeaderToken);
        }

        // Final fallback: delegate to XOR handler for parameter-based resolution
        // This handles form-based submissions with _csrf parameter
        return delegate.resolveCsrfTokenValue(exchange, csrfToken);
    }
}
