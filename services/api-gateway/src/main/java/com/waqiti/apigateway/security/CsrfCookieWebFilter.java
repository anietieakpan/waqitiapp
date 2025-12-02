package com.waqiti.apigateway.security;

import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * CSRF Cookie Web Filter
 *
 * Forces generation and exposure of CSRF tokens for Single Page Applications.
 *
 * Spring Security's CsrfToken is lazy-loaded by default, meaning it won't be
 * generated until accessed. This filter ensures the CSRF token is:
 * 1. Generated on every request
 * 2. Set in a cookie (XSRF-TOKEN) readable by JavaScript
 * 3. Available for SPA frameworks to read and send back in headers
 *
 * This enables the Double Submit Cookie pattern:
 * - Token stored in cookie (readable by JS, NOT httpOnly)
 * - Token sent back in custom header (X-XSRF-TOKEN)
 * - Server validates header value matches cookie value
 *
 * Security Properties:
 * - Cookie is NOT httpOnly (JavaScript needs to read it)
 * - Cookie has SameSite=Strict to prevent CSRF attacks
 * - Cookie is Secure in production (HTTPS only)
 * - Token uses XOR masking to prevent BREACH attacks
 *
 * @author Waqiti Platform Team - Security Engineering
 * @version 1.0.0
 * @since 2025-10-26
 */
public class CsrfCookieWebFilter implements WebFilter {

    /**
     * Process web requests to ensure CSRF token generation
     *
     * This filter subscribes to the CSRF token Mono, which triggers
     * token generation and cookie setting even if the token isn't
     * explicitly accessed by downstream handlers.
     *
     * @param exchange Current server web exchange
     * @param chain Filter chain for request processing
     * @return Mono that completes when request processing is done
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        /*
         * Retrieve the CSRF token from the exchange attributes.
         * The token is stored by CsrfWebFilter earlier in the filter chain.
         */
        Mono<CsrfToken> csrfToken = exchange.getAttribute(CsrfToken.class.getName());

        if (csrfToken != null) {
            /*
             * Subscribe to the token Mono to force generation.
             * This ensures:
             * 1. Token is generated
             * 2. Cookie is set in response
             * 3. Token is available for SPA to read
             *
             * The .then() operator ensures we wait for token generation
             * before proceeding with the filter chain.
             */
            return csrfToken
                    .doOnSuccess(token -> {
                        // Token has been generated and cookie set
                        // Log for debugging in development
                        if (token != null) {
                            exchange.getResponse().getHeaders()
                                .add("X-CSRF-TOKEN-GENERATED", "true");
                        }
                    })
                    .then(chain.filter(exchange));
        }

        // If no CSRF token present (disabled or excluded path), continue normally
        return chain.filter(exchange);
    }
}
