package com.waqiti.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;
import org.springframework.security.web.server.header.XXssProtectionServerHttpHeadersWriter;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * HTTPS Enforcement Configuration
 * 
 * Ensures all traffic uses HTTPS in production environments
 * Implements security headers and HSTS
 */
@Configuration
@EnableWebFluxSecurity
public class HttpsEnforcementConfig {
    
    @Value("${security.require-ssl:true}")
    private boolean requireSsl;
    
    @Value("${security.hsts.max-age:31536000}")
    private long hstsMaxAge;
    
    @Value("${security.hsts.include-subdomains:true}")
    private boolean hstsIncludeSubdomains;
    
    @Value("${security.hsts.preload:true}")
    private boolean hstsPreload;
    
    /**
     * Security filter chain with HTTPS enforcement
     */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
            .redirectToHttps(redirect -> redirect
                .httpsRedirectWhen(exchange -> requireSsl && !isHttps(exchange))
            )
            .headers(headers -> headers
                .frameOptions(frameOptions -> 
                    frameOptions.mode(XFrameOptionsServerHttpHeadersWriter.Mode.DENY)
                )
                .xssProtection(xss -> 
                    xss.mode(XXssProtectionServerHttpHeadersWriter.Mode.BLOCK)
                )
                .contentSecurityPolicy(csp -> 
                    csp.policyDirectives("default-src 'self'; " +
                        "script-src 'self' 'unsafe-inline' https://apis.google.com; " +
                        "style-src 'self' 'unsafe-inline'; " +
                        "img-src 'self' data: https:; " +
                        "font-src 'self' data:; " +
                        "connect-src 'self' https://api.example.com https://auth.example.com; " +
                        "frame-ancestors 'none'; " +
                        "base-uri 'self'; " +
                        "form-action 'self';")
                )
                .hsts(hsts -> hsts
                    .maxAge(hstsMaxAge)
                    .includeSubdomains(hstsIncludeSubdomains)
                    .preload(hstsPreload)
                )
            )
            .build();
    }
    
    /**
     * Web filter to enforce HTTPS and add security headers
     */
    @Bean
    @Profile("!dev")
    public WebFilter httpsEnforcementFilter() {
        return new WebFilter() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
                // Check if request is using HTTPS
                if (requireSsl && !isHttps(exchange)) {
                    // Redirect to HTTPS
                    String httpsUrl = "https://" + exchange.getRequest().getURI().getHost() + 
                                     exchange.getRequest().getURI().getPath();
                    
                    if (exchange.getRequest().getURI().getQuery() != null) {
                        httpsUrl += "?" + exchange.getRequest().getURI().getQuery();
                    }
                    
                    exchange.getResponse().setStatusCode(HttpStatus.MOVED_PERMANENTLY);
                    exchange.getResponse().getHeaders().add("Location", httpsUrl);
                    return exchange.getResponse().setComplete();
                }
                
                // Add security headers
                addSecurityHeaders(exchange);
                
                return chain.filter(exchange);
            }
        };
    }
    
    /**
     * Check if request is using HTTPS
     */
    private boolean isHttps(ServerWebExchange exchange) {
        // Check X-Forwarded-Proto header (when behind proxy/load balancer)
        String forwardedProto = exchange.getRequest().getHeaders().getFirst("X-Forwarded-Proto");
        if ("https".equalsIgnoreCase(forwardedProto)) {
            return true;
        }
        
        // Check direct connection
        return exchange.getRequest().getURI().getScheme().equals("https");
    }
    
    /**
     * Add security headers to response
     */
    private void addSecurityHeaders(ServerWebExchange exchange) {
        exchange.getResponse().getHeaders().add("X-Content-Type-Options", "nosniff");
        exchange.getResponse().getHeaders().add("X-Frame-Options", "DENY");
        exchange.getResponse().getHeaders().add("X-XSS-Protection", "1; mode=block");
        exchange.getResponse().getHeaders().add("Referrer-Policy", "strict-origin-when-cross-origin");
        exchange.getResponse().getHeaders().add("Permissions-Policy", 
            "accelerometer=(), camera=(), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), payment=*, usb=()");
        
        // Add HSTS header
        if (requireSsl) {
            String hstsValue = "max-age=" + hstsMaxAge;
            if (hstsIncludeSubdomains) {
                hstsValue += "; includeSubDomains";
            }
            if (hstsPreload) {
                hstsValue += "; preload";
            }
            exchange.getResponse().getHeaders().add("Strict-Transport-Security", hstsValue);
        }
    }
    
    /**
     * Health check filter to allow health endpoints without HTTPS redirect
     */
    @Bean
    public WebFilter healthCheckFilter() {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getPath().value();
            
            // Allow health check endpoints without HTTPS
            if (path.startsWith("/actuator/health") || path.equals("/health")) {
                return chain.filter(exchange);
            }
            
            return chain.filter(exchange);
        };
    }
}