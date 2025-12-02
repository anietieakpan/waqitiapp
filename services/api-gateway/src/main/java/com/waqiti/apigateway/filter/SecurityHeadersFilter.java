package com.waqiti.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global filter to add security headers to all responses
 * Implements OWASP security best practices
 */
@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityHeadersFilter.class);
    
    // Security header constants
    private static final String X_FRAME_OPTIONS = "X-Frame-Options";
    private static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String X_XSS_PROTECTION = "X-XSS-Protection";
    private static final String STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";
    private static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    private static final String X_PERMITTED_CROSS_DOMAIN_POLICIES = "X-Permitted-Cross-Domain-Policies";
    private static final String REFERRER_POLICY = "Referrer-Policy";
    private static final String PERMISSIONS_POLICY = "Permissions-Policy";
    private static final String X_REQUEST_ID = "X-Request-ID";
    private static final String X_CORRELATION_ID = "X-Correlation-ID";
    private static final String CACHE_CONTROL = "Cache-Control";
    private static final String PRAGMA = "Pragma";
    private static final String EXPIRES = "Expires";
    
    // CSP directives for production
    private static final String CSP_POLICY = String.join("; ",
        "default-src 'self'",
        "script-src 'self' 'unsafe-inline' https://cdn.example.com https://www.google-analytics.com https://www.googletagmanager.com",
        "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
        "font-src 'self' https://fonts.gstatic.com",
        "img-src 'self' data: https: blob:",
        "connect-src 'self' wss://waqiti.com https://api.example.com https://analytics.example.com",
        "frame-src 'self' https://www.youtube.com https://player.vimeo.com",
        "frame-ancestors 'none'",
        "form-action 'self'",
        "base-uri 'self'",
        "object-src 'none'",
        "media-src 'self'",
        "manifest-src 'self'",
        "worker-src 'self' blob:",
        "upgrade-insecure-requests",
        "block-all-mixed-content"
    );
    
    // Permissions Policy for modern browsers
    private static final String PERMISSIONS_POLICY_VALUE = String.join(", ",
        "accelerometer=()",
        "camera=(self)",
        "geolocation=(self)",
        "gyroscope=()",
        "magnetometer=()",
        "microphone=()",
        "payment=(self)",
        "usb=()",
        "interest-cohort=()",
        "battery=()",
        "display-capture=()",
        "document-domain=()",
        "encrypted-media=(self)",
        "execution-while-not-rendered=()",
        "execution-while-out-of-viewport=()",
        "fullscreen=(self)",
        "navigation-override=()",
        "picture-in-picture=()",
        "publickey-credentials-get=(self)",
        "screen-wake-lock=()",
        "sync-xhr=()",
        "web-share=(self)",
        "xr-spatial-tracking=()"
    );
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Generate unique request ID if not present
        String requestId = exchange.getRequest().getHeaders().getFirst(X_REQUEST_ID);
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        
        // Get or generate correlation ID
        String correlationId = exchange.getRequest().getHeaders().getFirst(X_CORRELATION_ID);
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        
        final String finalRequestId = requestId;
        final String finalCorrelationId = correlationId;
        
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            
            // Add security headers
            addSecurityHeaders(headers, exchange);
            
            // Add tracking headers
            headers.add(X_REQUEST_ID, finalRequestId);
            headers.add(X_CORRELATION_ID, finalCorrelationId);
            
            // Add cache control headers for sensitive endpoints
            if (isSensitiveEndpoint(exchange.getRequest().getPath().toString())) {
                addNoCacheHeaders(headers);
            }
            
            logger.debug("Security headers added for request: {} with correlation ID: {}", 
                        finalRequestId, finalCorrelationId);
        }));
    }
    
    private void addSecurityHeaders(HttpHeaders headers, ServerWebExchange exchange) {
        // Prevent clickjacking attacks
        headers.add(X_FRAME_OPTIONS, "DENY");
        
        // Prevent MIME type sniffing
        headers.add(X_CONTENT_TYPE_OPTIONS, "nosniff");
        
        // Enable XSS protection (legacy browsers)
        headers.add(X_XSS_PROTECTION, "1; mode=block");
        
        // Force HTTPS for production
        if (isProductionEnvironment()) {
            headers.add(STRICT_TRANSPORT_SECURITY, "max-age=31536000; includeSubDomains; preload");
        }
        
        // Content Security Policy
        headers.add(CONTENT_SECURITY_POLICY, CSP_POLICY);
        
        // Prevent Flash/PDF cross-domain attacks
        headers.add(X_PERMITTED_CROSS_DOMAIN_POLICIES, "none");
        
        // Control referrer information
        headers.add(REFERRER_POLICY, "strict-origin-when-cross-origin");
        
        // Permissions Policy (replaces Feature-Policy)
        headers.add(PERMISSIONS_POLICY, PERMISSIONS_POLICY_VALUE);
        
        // Remove server header if present
        headers.remove("Server");
        headers.remove("X-Powered-By");
        
        // Add custom security header for API version
        headers.add("X-API-Version", "v1");
        
        // Add rate limiting headers if applicable
        addRateLimitHeaders(headers, exchange);
    }
    
    private void addNoCacheHeaders(HttpHeaders headers) {
        headers.add(CACHE_CONTROL, "no-store, no-cache, must-revalidate, proxy-revalidate, max-age=0");
        headers.add(PRAGMA, "no-cache");
        headers.add(EXPIRES, "0");
    }
    
    private void addRateLimitHeaders(HttpHeaders headers, ServerWebExchange exchange) {
        // These would typically come from a rate limiting service
        String remainingRequests = exchange.getAttribute("rate-limit-remaining");
        String resetTime = exchange.getAttribute("rate-limit-reset");
        
        if (remainingRequests != null) {
            headers.add("X-RateLimit-Remaining", remainingRequests);
        }
        if (resetTime != null) {
            headers.add("X-RateLimit-Reset", resetTime);
        }
        
        // Add default rate limit for transparency
        headers.add("X-RateLimit-Limit", "1000");
    }
    
    private boolean isSensitiveEndpoint(String path) {
        return path.contains("/api/v1/users") ||
               path.contains("/api/v1/wallets") ||
               path.contains("/api/v1/payments") ||
               path.contains("/api/v1/kyc") ||
               path.contains("/api/v1/security") ||
               path.contains("/api/v1/admin");
    }
    
    private boolean isProductionEnvironment() {
        String activeProfile = System.getProperty("spring.profiles.active", "");
        String envProfile = System.getenv("SPRING_PROFILES_ACTIVE");
        return "prod".equals(activeProfile) || "production".equals(activeProfile) ||
               "prod".equals(envProfile) || "production".equals(envProfile);
    }
    
    @Override
    public int getOrder() {
        // Execute early in the filter chain
        return -100;
    }
}