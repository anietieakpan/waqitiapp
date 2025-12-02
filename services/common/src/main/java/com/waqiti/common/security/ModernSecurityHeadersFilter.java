package com.waqiti.common.security;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Modern security headers filter using Jakarta Servlet API
 * Compatible with Spring Boot 3.x
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ModernSecurityHeadersFilter extends OncePerRequestFilter {
    
    private static final String NONCE_ATTRIBUTE = "csp-nonce";
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    
    private final SecurityHeadersConfiguration.SecurityHeadersProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        // Generate CSP nonce for inline scripts
        String nonce = generateNonce();
        request.setAttribute(NONCE_ATTRIBUTE, nonce);
        
        // Add request tracking headers
        addRequestTrackingHeaders(request, response);
        
        // Add comprehensive security headers
        addSecurityHeaders(response, nonce);
        
        // Add API-specific headers
        if (properties.isEnableApiHeaders()) {
            addApiHeaders(response);
        }
        
        // Add custom headers
        if (properties.isEnableCustomHeaders()) {
            addCustomSecurityHeaders(response);
        }
        
        // Continue with the filter chain
        filterChain.doFilter(request, response);
    }
    
    /**
     * Generate a secure nonce for CSP
     */
    private String generateNonce() {
        byte[] nonceBytes = new byte[16];
        secureRandom.nextBytes(nonceBytes);
        return Base64.getEncoder().encodeToString(nonceBytes);
    }
    
    /**
     * Add request tracking headers for distributed tracing
     */
    private void addRequestTrackingHeaders(HttpServletRequest request, HttpServletResponse response) {
        // Add or propagate request ID
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }
        response.setHeader(REQUEST_ID_HEADER, requestId);
        
        // Add or propagate correlation ID
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
    }
    
    /**
     * Add comprehensive security headers
     */
    private void addSecurityHeaders(HttpServletResponse response, String nonce) {
        // Strict-Transport-Security
        response.setHeader("Strict-Transport-Security", 
            String.format("max-age=%d; includeSubDomains; preload", properties.getHstsMaxAge()));
        
        // X-Frame-Options
        response.setHeader("X-Frame-Options", properties.isFrameDenyEnabled() ? "DENY" : "SAMEORIGIN");
        
        // X-Content-Type-Options
        response.setHeader("X-Content-Type-Options", "nosniff");
        
        // X-XSS-Protection (for legacy browsers)
        response.setHeader("X-XSS-Protection", "1; mode=block");
        
        // Referrer-Policy
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        
        // Content-Security-Policy with nonce
        String csp = properties.getContentSecurityPolicy().replace("{nonce}", nonce);
        response.setHeader("Content-Security-Policy", csp);
        
        // Permissions-Policy
        response.setHeader("Permissions-Policy", properties.getPermissionsPolicy());
        
        // Feature-Policy (legacy)
        response.setHeader("Feature-Policy", properties.getFeaturePolicy());
        
        // Cache-Control for sensitive data
        if (!isStaticResource(response)) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
        }
        
        // Additional security headers
        response.setHeader("X-Permitted-Cross-Domain-Policies", "none");
        response.setHeader("X-Download-Options", "noopen");
        response.setHeader("X-DNS-Prefetch-Control", "off");
        
        // Cross-Origin headers
        response.setHeader("Cross-Origin-Embedder-Policy", "require-corp");
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
        
        // Expect-CT for Certificate Transparency
        response.setHeader("Expect-CT", 
            String.format("max-age=86400, enforce, report-uri=\"%s\"", properties.getExpectCtReportUri()));
        
        // Network Error Logging (NEL)
        if (properties.isEnableSecurityReporting()) {
            response.setHeader("NEL", String.format(
                "{\"report_to\":\"nel-endpoint\",\"max_age\":31536000,\"include_subdomains\":true,\"failure_fraction\":0.01}"));
            
            // Report-To header for various reporting endpoints
            response.setHeader("Report-To", String.format(
                "{\"group\":\"csp-endpoint\",\"max_age\":31536000,\"endpoints\":[{\"url\":\"%s\"}]}, " +
                "{\"group\":\"nel-endpoint\",\"max_age\":31536000,\"endpoints\":[{\"url\":\"%s\"}]}",
                properties.getCspReportEndpoint(),
                properties.getNelReportEndpoint()
            ));
        }
    }
    
    /**
     * Add API-specific headers
     */
    private void addApiHeaders(HttpServletResponse response) {
        response.setHeader("X-API-Version", properties.getApiVersion());
        response.setHeader("X-Service-Name", properties.getServiceName());
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Response-Time", String.valueOf(System.currentTimeMillis()));
    }
    
    /**
     * Add custom security headers
     */
    private void addCustomSecurityHeaders(HttpServletResponse response) {
        // Remove server information
        response.setHeader("Server", "Waqiti");
        
        // Add security contact
        response.setHeader("X-Security-Contact", "security@example.com");
        
        // Add security policy
        response.setHeader("X-Security-Policy", "https://api.example.com/security");
        
        // Public Key Pins (if enabled)
        // Note: Use with caution as incorrect pinning can lock out users
        // response.setHeader("Public-Key-Pins", "pin-sha256=\"base64==\"; max-age=5184000; includeSubDomains");
    }
    
    /**
     * Check if the response is for a static resource
     */
    private boolean isStaticResource(HttpServletResponse response) {
        String contentType = response.getContentType();
        if (contentType == null) {
            return false;
        }
        
        return contentType.startsWith("image/") ||
               contentType.startsWith("text/css") ||
               contentType.startsWith("application/javascript") ||
               contentType.startsWith("font/");
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        // Don't filter actuator endpoints
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health") || 
               path.startsWith("/actuator/prometheus") ||
               path.startsWith("/actuator/metrics");
    }
}