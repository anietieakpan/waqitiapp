package com.waqiti.apigateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Security headers configuration for API Gateway
 * Implements comprehensive security headers for all downstream services
 */
@Configuration
@Slf4j
public class GatewaySecurityHeadersConfig {
    
    /**
     * Global filter to add security headers to all responses
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public GlobalFilter securityHeadersGlobalFilter() {
        return (exchange, chain) -> {
            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                ServerHttpResponse response = exchange.getResponse();
                HttpHeaders headers = response.getHeaders();
                
                // Add comprehensive security headers
                addSecurityHeaders(headers);
                
                // Add request tracking headers
                addRequestTrackingHeaders(exchange, headers);
                
                // Add API Gateway specific headers
                addGatewayHeaders(headers);
            }));
        };
    }
    
    /**
     * Add comprehensive security headers
     */
    private void addSecurityHeaders(HttpHeaders headers) {
        // Strict-Transport-Security
        headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
        
        // X-Frame-Options
        headers.set("X-Frame-Options", "DENY");
        
        // X-Content-Type-Options
        headers.set("X-Content-Type-Options", "nosniff");
        
        // X-XSS-Protection
        headers.set("X-XSS-Protection", "1; mode=block");
        
        // Referrer-Policy
        headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
        
        // Content-Security-Policy
        headers.set("Content-Security-Policy", 
            "default-src 'self'; " +
            "script-src 'self' 'strict-dynamic' https://apis.example.com; " +
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
            "img-src 'self' data: https: blob:; " +
            "font-src 'self' https://fonts.gstatic.com; " +
            "connect-src 'self' https://api.example.com wss://ws.waqiti.com; " +
            "media-src 'self'; " +
            "object-src 'none'; " +
            "frame-src 'self'; " +
            "base-uri 'self'; " +
            "form-action 'self'; " +
            "frame-ancestors 'none'; " +
            "block-all-mixed-content; " +
            "upgrade-insecure-requests; " +
            "report-uri https://csp.example.com/report");
        
        // Permissions-Policy
        headers.set("Permissions-Policy", 
            "accelerometer=(), " +
            "ambient-light-sensor=(), " +
            "autoplay=(), " +
            "battery=(), " +
            "camera=(), " +
            "display-capture=(), " +
            "document-domain=(), " +
            "encrypted-media=(), " +
            "fullscreen=(self), " +
            "geolocation=(self), " +
            "gyroscope=(), " +
            "magnetometer=(), " +
            "microphone=(), " +
            "midi=(), " +
            "payment=(self), " +
            "usb=()");
        
        // Additional security headers
        headers.set("X-Permitted-Cross-Domain-Policies", "none");
        headers.set("X-Download-Options", "noopen");
        headers.set("X-DNS-Prefetch-Control", "off");
        
        // Cross-Origin headers
        headers.set("Cross-Origin-Embedder-Policy", "require-corp");
        headers.set("Cross-Origin-Opener-Policy", "same-origin");
        headers.set("Cross-Origin-Resource-Policy", "same-origin");
        
        // Expect-CT
        headers.set("Expect-CT", "max-age=86400, enforce, report-uri=\"https://ct.example.com/report\"");
        
        // Clear sensitive headers
        headers.remove("Server");
        headers.remove("X-Powered-By");
        headers.remove("X-AspNet-Version");
        headers.remove("X-AspNetMvc-Version");
    }
    
    /**
     * Add request tracking headers
     */
    private void addRequestTrackingHeaders(org.springframework.web.server.ServerWebExchange exchange, 
                                         HttpHeaders headers) {
        // Generate or propagate request ID
        String requestId = headers.getFirst("X-Request-ID");
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
            headers.set("X-Request-ID", requestId);
        }
        
        // Generate or propagate correlation ID
        String correlationId = headers.getFirst("X-Correlation-ID");
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
            headers.set("X-Correlation-ID", correlationId);
        }
    }
    
    /**
     * Add API Gateway specific headers
     */
    private void addGatewayHeaders(HttpHeaders headers) {
        headers.set("X-Gateway-Version", "1.0");
        headers.set("X-Gateway-Instance", System.getenv("HOSTNAME"));
        headers.set("X-Response-Time", String.valueOf(System.currentTimeMillis()));
    }
    
    /**
     * Security headers properties for API Gateway
     */
    @Bean
    public GatewaySecurityProperties gatewaySecurityProperties() {
        return new GatewaySecurityProperties();
    }
    
    /**
     * Gateway-specific security properties
     */
    public static class GatewaySecurityProperties {
        private boolean enableSecurityHeaders = true;
        private boolean enableRateLimiting = true;
        private boolean enableWaf = true;
        private boolean enableDdosProtection = true;
        private boolean enableGeoBlocking = true;
        
        // Getters and setters
        public boolean isEnableSecurityHeaders() { return enableSecurityHeaders; }
        public void setEnableSecurityHeaders(boolean enableSecurityHeaders) { 
            this.enableSecurityHeaders = enableSecurityHeaders; 
        }
        
        public boolean isEnableRateLimiting() { return enableRateLimiting; }
        public void setEnableRateLimiting(boolean enableRateLimiting) { 
            this.enableRateLimiting = enableRateLimiting; 
        }
        
        public boolean isEnableWaf() { return enableWaf; }
        public void setEnableWaf(boolean enableWaf) { 
            this.enableWaf = enableWaf; 
        }
        
        public boolean isEnableDdosProtection() { return enableDdosProtection; }
        public void setEnableDdosProtection(boolean enableDdosProtection) { 
            this.enableDdosProtection = enableDdosProtection; 
        }
        
        public boolean isEnableGeoBlocking() { return enableGeoBlocking; }
        public void setEnableGeoBlocking(boolean enableGeoBlocking) { 
            this.enableGeoBlocking = enableGeoBlocking; 
        }
    }
}