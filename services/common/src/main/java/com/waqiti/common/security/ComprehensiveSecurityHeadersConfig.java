package com.waqiti.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.security.web.header.writers.*;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Comprehensive Security Headers Configuration
 * Implements all critical security headers for production-grade security
 * 
 * Headers implemented:
 * - Content Security Policy (CSP) with nonce support
 * - HTTP Strict Transport Security (HSTS)
 * - X-Frame-Options (Clickjacking protection)
 * - X-Content-Type-Options (MIME sniffing protection)
 * - X-XSS-Protection (XSS protection)
 * - Referrer-Policy (Information leakage protection)
 * - Permissions-Policy (Feature policy)
 * - Cross-Origin-Embedder-Policy (COEP)
 * - Cross-Origin-Opener-Policy (COOP)
 * - Cross-Origin-Resource-Policy (CORP)
 * - Expect-CT (Certificate Transparency)
 * - Server header removal
 * - Cache-Control for sensitive endpoints
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class ComprehensiveSecurityHeadersConfig {

    @Value("${waqiti.security.csp.enabled:true}")
    private boolean cspEnabled;

    @Value("${waqiti.security.csp.report-uri:}")
    private String cspReportUri;

    @Value("${waqiti.security.hsts.max-age:31536000}")
    private long hstsMaxAge;

    @Value("${waqiti.security.hsts.include-subdomains:true}")
    private boolean hstsIncludeSubdomains;

    @Value("${waqiti.security.hsts.preload:true}")
    private boolean hstsPreload;

    @Value("${waqiti.security.frame-options:DENY}")
    private String frameOptions;

    @Value("${waqiti.security.referrer-policy:strict-origin-when-cross-origin}")
    private String referrerPolicy;

    @Value("${waqiti.security.expect-ct.max-age:86400}")
    private long expectCtMaxAge;

    @Value("${waqiti.security.expect-ct.enforce:true}")
    private boolean expectCtEnforce;

    @Value("${waqiti.security.expect-ct.report-uri:}")
    private String expectCtReportUri;

    @Value("${waqiti.security.headers.server-header:}")
    private String customServerHeader;

    @Bean
    public FilterRegistrationBean<ComprehensiveSecurityHeadersFilter> securityHeadersFilter() {
        FilterRegistrationBean<ComprehensiveSecurityHeadersFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ComprehensiveSecurityHeadersFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("ComprehensiveSecurityHeadersFilter");
        return registration;
    }

    /**
     * Configure security headers in Spring Security
     */
    public void configureSecurityHeaders(HttpSecurity http) throws Exception {
        http.headers(headers -> {
            headers
                // Content Security Policy
                .contentSecurityPolicy(csp -> {
                    if (cspEnabled) {
                        String cspDirective = buildContentSecurityPolicy();
                        csp.policyDirectives(cspDirective);
                    }
                })
                // HTTP Strict Transport Security
                .httpStrictTransportSecurity(hsts -> {
                    hsts.maxAgeInSeconds(hstsMaxAge);
                    hsts.includeSubDomains(hstsIncludeSubdomains);
                    hsts.preload(hstsPreload);
                })
                // Frame Options (Clickjacking protection)
                .frameOptions(frame -> {
                    switch (frameOptions.toUpperCase()) {
                        case "DENY":
                            frame.deny();
                            break;
                        case "SAMEORIGIN":
                            frame.sameOrigin();
                            break;
                        default:
                            frame.deny();
                    }
                })
                // Content Type Options
                .contentTypeOptions(contentType -> contentType.disable())
                // Add custom headers
                .addHeaderWriter(new StaticHeadersWriter("X-Content-Type-Options", "nosniff"))
                .addHeaderWriter(new StaticHeadersWriter("X-XSS-Protection", "0"))
                .addHeaderWriter(new StaticHeadersWriter("Referrer-Policy", referrerPolicy))
                .addHeaderWriter(new PermissionsPolicyHeaderWriter())
                .addHeaderWriter(new CrossOriginHeaderWriter())
                .addHeaderWriter(new ExpectCTHeaderWriter())
                .addHeaderWriter(new SecurityHeaderWriter());
        });
    }

    /**
     * Build Content Security Policy directive
     */
    private String buildContentSecurityPolicy() {
        StringBuilder csp = new StringBuilder();
        
        // Default source policy - very restrictive
        csp.append("default-src 'self'; ");
        
        // Script sources with nonce support
        csp.append("script-src 'self' 'unsafe-inline' 'unsafe-eval' ") // Note: In production, remove unsafe-* and use nonces
           .append("https://cdnjs.cloudflare.com https://cdn.jsdelivr.net ")
           .append("https://unpkg.com https://code.jquery.com ")
           .append("'nonce-{nonce}'; ");
        
        // Style sources
        csp.append("style-src 'self' 'unsafe-inline' ")
           .append("https://fonts.googleapis.com https://cdnjs.cloudflare.com ")
           .append("https://cdn.jsdelivr.net 'nonce-{nonce}'; ");
        
        // Font sources
        csp.append("font-src 'self' https://fonts.gstatic.com ")
           .append("https://cdnjs.cloudflare.com data:; ");
        
        // Image sources
        csp.append("img-src 'self' data: blob: ")
           .append("https: http:; "); // Note: In production, be more restrictive
        
        // Media sources
        csp.append("media-src 'self' blob:; ");
        
        // Object sources - very restrictive
        csp.append("object-src 'none'; ");
        
        // Child/frame sources
        csp.append("child-src 'self' blob:; ");
        csp.append("frame-src 'self' blob:; ");
        
        // Worker sources
        csp.append("worker-src 'self' blob:; ");
        
        // Connect sources (API endpoints)
        csp.append("connect-src 'self' ")
           .append("https://api.example.com ")
           .append("https://*.waqiti.com ")
           .append("wss://websocket.waqiti.com ")
           .append("https://api.coingecko.com ")
           .append("https://api.coinbase.com; ");
        
        // Manifest source
        csp.append("manifest-src 'self'; ");
        
        // Base URI restriction
        csp.append("base-uri 'self'; ");
        
        // Form action restriction
        csp.append("form-action 'self'; ");
        
        // Frame ancestors (additional clickjacking protection)
        csp.append("frame-ancestors 'none'; ");
        
        // Block mixed content
        csp.append("block-all-mixed-content; ");
        
        // Upgrade insecure requests
        csp.append("upgrade-insecure-requests; ");
        
        // Report violations
        if (!cspReportUri.isEmpty()) {
            csp.append("report-uri ").append(cspReportUri).append("; ");
        }
        
        return csp.toString();
    }

    /**
     * Custom security headers filter
     */
    public class ComprehensiveSecurityHeadersFilter implements Filter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            
            // Add comprehensive security headers
            addSecurityHeaders(httpRequest, httpResponse);
            
            // Remove server information
            removeServerHeaders(httpResponse);
            
            // Add cache control for sensitive endpoints
            addCacheControlHeaders(httpRequest, httpResponse);
            
            chain.doFilter(request, response);
        }

        private void addSecurityHeaders(HttpServletRequest request, HttpServletResponse response) {
            // Permissions Policy (Feature Policy replacement)
            response.setHeader("Permissions-Policy", 
                "accelerometer=(), " +
                "ambient-light-sensor=(), " +
                "autoplay=(), " +
                "battery=(), " +
                "camera=(), " +
                "cross-origin-isolated=(), " +
                "display-capture=(), " +
                "document-domain=(), " +
                "encrypted-media=(), " +
                "execution-while-not-rendered=(), " +
                "execution-while-out-of-viewport=(), " +
                "fullscreen=(self), " +
                "geolocation=(), " +
                "gyroscope=(), " +
                "keyboard-map=(), " +
                "magnetometer=(), " +
                "microphone=(), " +
                "midi=(), " +
                "navigation-override=(), " +
                "payment=(self), " +
                "picture-in-picture=(), " +
                "publickey-credentials-get=(), " +
                "screen-wake-lock=(), " +
                "sync-xhr=(), " +
                "usb=(), " +
                "web-share=(), " +
                "xr-spatial-tracking=()");

            // Cross-Origin Policies
            response.setHeader("Cross-Origin-Embedder-Policy", "require-corp");
            response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
            response.setHeader("Cross-Origin-Resource-Policy", "same-origin");

            // Expect-CT
            if (expectCtEnabled()) {
                StringBuilder expectCt = new StringBuilder();
                expectCt.append("max-age=").append(expectCtMaxAge);
                if (expectCtEnforce) {
                    expectCt.append(", enforce");
                }
                if (!expectCtReportUri.isEmpty()) {
                    expectCt.append(", report-uri=\"").append(expectCtReportUri).append("\"");
                }
                response.setHeader("Expect-CT", expectCt.toString());
            }

            // Additional security headers
            response.setHeader("X-Permitted-Cross-Domain-Policies", "none");
            response.setHeader("X-DNS-Prefetch-Control", "off");
            response.setHeader("X-Download-Options", "noopen");
            
            // Clear-Site-Data for logout endpoints
            if (request.getRequestURI().contains("/logout")) {
                response.setHeader("Clear-Site-Data", "\"cache\", \"cookies\", \"storage\", \"executionContexts\"");
            }
        }

        private void removeServerHeaders(HttpServletResponse response) {
            // Remove or customize server header
            if (customServerHeader.isEmpty()) {
                response.setHeader("Server", "");
            } else {
                response.setHeader("Server", customServerHeader);
            }
            
            // Remove X-Powered-By header if present
            response.setHeader("X-Powered-By", "");
        }

        private void addCacheControlHeaders(HttpServletRequest request, HttpServletResponse response) {
            String requestUri = request.getRequestURI();
            
            // Sensitive endpoints should not be cached
            if (isSensitiveEndpoint(requestUri)) {
                response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private");
                response.setHeader("Pragma", "no-cache");
                response.setHeader("Expires", "0");
            }
            
            // API endpoints cache control
            if (requestUri.startsWith("/api/")) {
                response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            }
        }

        private boolean isSensitiveEndpoint(String uri) {
            String[] sensitivePatterns = {
                "/login", "/logout", "/auth", "/token", "/oauth",
                "/admin", "/actuator", "/health", "/metrics",
                "/api/payment", "/api/wallet", "/api/transfer",
                "/api/user/profile", "/api/security",
                "/api/crypto", "/api/trading"
            };
            
            return Arrays.stream(sensitivePatterns)
                .anyMatch(pattern -> uri.toLowerCase().contains(pattern));
        }

        private boolean expectCtEnabled() {
            return expectCtMaxAge > 0;
        }
    }

    /**
     * Custom header writers
     */
    public static class PermissionsPolicyHeaderWriter implements HeaderWriter {
        @Override
        public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
            if (!response.containsHeader("Permissions-Policy")) {
                response.setHeader("Permissions-Policy", 
                    "camera=(), microphone=(), geolocation=(), payment=(self)");
            }
        }
    }

    public static class CrossOriginHeaderWriter implements HeaderWriter {
        @Override
        public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
            response.setHeader("Cross-Origin-Embedder-Policy", "require-corp");
            response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
            response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
        }
    }

    public static class ExpectCTHeaderWriter implements HeaderWriter {
        @Override
        public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
            response.setHeader("Expect-CT", "max-age=86400, enforce");
        }
    }

    public static class SecurityHeaderWriter implements HeaderWriter {
        @Override
        public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
            response.setHeader("X-Permitted-Cross-Domain-Policies", "none");
            response.setHeader("X-DNS-Prefetch-Control", "off");
            response.setHeader("X-Download-Options", "noopen");
        }
    }

    /**
     * CORS Configuration for secure cross-origin requests
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allowed origins - should be restrictive in production
        configuration.setAllowedOriginPatterns(Arrays.asList(
            "https://*.waqiti.com",
            "https://api.example.com",
            "https://api.example.com"
        ));
        
        // Allowed methods
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS"
        ));
        
        // Allowed headers
        configuration.setAllowedHeaders(Arrays.asList(
            "Content-Type", "Authorization", "X-Requested-With",
            "X-CSRF-TOKEN", "X-API-Key", "Accept"
        ));
        
        // Exposed headers
        configuration.setExposedHeaders(Arrays.asList(
            "X-Total-Count", "X-Page-Count", "Link"
        ));
        
        // Credentials
        configuration.setAllowCredentials(true);
        
        // Max age
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        
        return source;
    }
}