package com.waqiti.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Comprehensive security headers configuration for all Waqiti services
 * Implements OWASP recommended security headers
 */
@Configuration
@EnableConfigurationProperties(SecurityHeadersConfiguration.SecurityHeadersProperties.class)
@Slf4j
public class SecurityHeadersConfiguration {
    
    private final SecurityHeadersProperties properties;
    
    public SecurityHeadersConfiguration(SecurityHeadersProperties properties) {
        this.properties = properties;
        log.info("Initializing security headers configuration");
    }
    
    /**
     * Configure comprehensive security headers
     */
    public void configureSecurityHeaders(HttpSecurity http) throws Exception {
        http.headers(headers -> {
            headers
            // X-Frame-Options: Prevent clickjacking attacks
            .frameOptions(frameOptions -> {
                if (properties.isFrameDenyEnabled()) {
                    frameOptions.deny();
                } else {
                    frameOptions.sameOrigin();
                }
            })
            
            // X-Content-Type-Options: Prevent MIME type sniffing
            .contentTypeOptions(contentTypeOptions -> {})
            
            // Strict-Transport-Security: Force HTTPS
            .httpStrictTransportSecurity(hsts -> hsts
                .maxAgeInSeconds(properties.getHstsMaxAge())
                .includeSubDomains(properties.isHstsIncludeSubDomains())
                .preload(properties.isHstsPreload())
            )
            
            // Content-Security-Policy: Control resource loading
            .contentSecurityPolicy(csp -> csp
                .policyDirectives(properties.getContentSecurityPolicy())
            )
            
            // X-XSS-Protection: Enable XSS filter (legacy browsers)
            .xssProtection(xss -> xss
                .disable()
            )
            
            // Referrer-Policy: Control referrer information
            .referrerPolicy(referrerPolicy -> referrerPolicy
                .policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
            )
            
            // Permissions-Policy: Control browser features
            .permissionsPolicy(permissions -> permissions
                .policy(properties.getPermissionsPolicy())
            )
            
            // Add custom headers
            .and()
            .addHeaderWriter(new StaticHeadersWriter("Cache-Control", "no-cache, no-store, must-revalidate"))
            .addHeaderWriter(new StaticHeadersWriter("Pragma", "no-cache"))
            .addHeaderWriter(new StaticHeadersWriter("Expires", "0"))
            .addHeaderWriter(new StaticHeadersWriter("X-Permitted-Cross-Domain-Policies", "none"))
            .addHeaderWriter(new StaticHeadersWriter("X-Download-Options", "noopen"))
            .addHeaderWriter(new StaticHeadersWriter("X-DNS-Prefetch-Control", "off"))
            .addHeaderWriter(new StaticHeadersWriter("Feature-Policy", properties.getFeaturePolicy()))
            .addHeaderWriter(new StaticHeadersWriter("Clear-Site-Data", "\"cache\", \"cookies\", \"storage\""))
            .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Embedder-Policy", "require-corp"))
            .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Opener-Policy", "same-origin"))
            .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Resource-Policy", "same-origin"))
            .addHeaderWriter(new StaticHeadersWriter("X-API-Version", properties.getApiVersion()))
            .addHeaderWriter(new StaticHeadersWriter("X-Service-Name", properties.getServiceName()))
            .addHeaderWriter(new StaticHeadersWriter("Expect-CT", 
                "max-age=86400, enforce, report-uri=\"" + properties.getExpectCtReportUri() + "\""));
        });
    }
    
    /**
     * Security headers properties configuration
     */
    @ConfigurationProperties(prefix = "waqiti.security.headers")
    @Data
    public static class SecurityHeadersProperties {
        
        // HSTS Configuration
        private long hstsMaxAge = 31536000L; // 1 year
        private boolean hstsIncludeSubDomains = true;
        private boolean hstsPreload = true;
        
        // Frame Options
        private boolean frameDenyEnabled = true;
        
        // Content Security Policy
        private String contentSecurityPolicy = 
            "default-src 'self'; " +
            "script-src 'self' 'strict-dynamic' 'nonce-{nonce}' https://api.example.com; " +
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
            "img-src 'self' data: https: blob:; " +
            "font-src 'self' https://fonts.gstatic.com; " +
            "connect-src 'self' https://api.example.com wss://ws.waqiti.com https://api.example.com; " +
            "media-src 'self'; " +
            "object-src 'none'; " +
            "frame-src 'self' https://api.example.com; " +
            "base-uri 'self'; " +
            "form-action 'self'; " +
            "frame-ancestors 'none'; " +
            "block-all-mixed-content; " +
            "upgrade-insecure-requests; " +
            "report-uri https://api.example.com/report; " +
            "report-to csp-endpoint";
        
        // Permissions Policy
        private String permissionsPolicy = 
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
            "geolocation=(self), " +
            "gyroscope=(), " +
            "layout-animations=(self), " +
            "legacy-image-formats=(), " +
            "magnetometer=(), " +
            "microphone=(), " +
            "midi=(), " +
            "navigation-override=(), " +
            "oversized-images=(), " +
            "payment=(self), " +
            "picture-in-picture=(), " +
            "publickey-credentials-get=(self), " +
            "screen-wake-lock=(), " +
            "sync-xhr=(), " +
            "usb=(), " +
            "web-share=(), " +
            "xr-spatial-tracking=()";
        
        // Feature Policy (legacy)
        private String featurePolicy = 
            "accelerometer 'none'; " +
            "ambient-light-sensor 'none'; " +
            "autoplay 'none'; " +
            "battery 'none'; " +
            "camera 'none'; " +
            "display-capture 'none'; " +
            "document-domain 'none'; " +
            "encrypted-media 'none'; " +
            "fullscreen 'self'; " +
            "geolocation 'self'; " +
            "gyroscope 'none'; " +
            "layout-animations 'self'; " +
            "legacy-image-formats 'none'; " +
            "magnetometer 'none'; " +
            "microphone 'none'; " +
            "midi 'none'; " +
            "oversized-images 'none'; " +
            "payment 'self'; " +
            "picture-in-picture 'none'; " +
            "publickey-credentials-get 'self'; " +
            "sync-xhr 'none'; " +
            "usb 'none'; " +
            "wake-lock 'none'; " +
            "xr-spatial-tracking 'none'";
        
        // API Configuration
        private String apiVersion = "1.0";
        private String serviceName = "waqiti-service";
        
        // Certificate Transparency
        private String expectCtReportUri = "https://api.example.com/report";
        
        // Custom headers
        private boolean enableCustomHeaders = true;
        private boolean enableApiHeaders = true;
        private boolean enableSecurityReporting = true;
        private boolean enableSecurityHeaders = true;
        
        // Report endpoints
        private String cspReportEndpoint = "https://api.example.com/report";
        private String ctReportEndpoint = "https://api.example.com/report";
        private String nelReportEndpoint = "https://api.example.com/report";
        
        public boolean isEnableSecurityHeaders() {
            return enableSecurityHeaders;
        }
    }
}