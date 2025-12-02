package com.waqiti.common.security;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.List;

/**
 * Health indicator for security headers
 * Monitors and reports on the presence and configuration of security headers
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityHeadersHealthIndicator implements HealthIndicator {
    
    private final SecurityHeadersConfiguration.SecurityHeadersProperties properties;
    
    private static final List<String> REQUIRED_HEADERS = Arrays.asList(
        "Strict-Transport-Security",
        "X-Frame-Options",
        "X-Content-Type-Options",
        "X-XSS-Protection",
        "Content-Security-Policy",
        "Referrer-Policy",
        "Permissions-Policy"
    );
    
    private static final List<String> RECOMMENDED_HEADERS = Arrays.asList(
        "X-Permitted-Cross-Domain-Policies",
        "X-Download-Options",
        "X-DNS-Prefetch-Control",
        "Cross-Origin-Embedder-Policy",
        "Cross-Origin-Opener-Policy",
        "Cross-Origin-Resource-Policy",
        "Expect-CT"
    );
    
    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();
        
        try {
            // Check if security headers are enabled
            details.put("enabled", properties.isEnableSecurityHeaders());
            
            // Check HSTS configuration
            checkHstsConfiguration(details);
            
            // Check CSP configuration
            checkCspConfiguration(details);
            
            // Check reporting configuration
            checkReportingConfiguration(details);
            
            // Add header configuration summary
            details.put("requiredHeaders", REQUIRED_HEADERS);
            details.put("recommendedHeaders", RECOMMENDED_HEADERS);
            details.put("serviceName", properties.getServiceName());
            details.put("apiVersion", properties.getApiVersion());
            
            // Calculate security score
            int securityScore = calculateSecurityScore();
            details.put("securityScore", securityScore + "%");
            
            if (securityScore >= 90) {
                return Health.up()
                    .withDetails(details)
                    .build();
            } else if (securityScore >= 70) {
                return Health.status("WARNING")
                    .withDetails(details)
                    .withDetail("message", "Security headers configuration could be improved")
                    .build();
            } else {
                return Health.down()
                    .withDetails(details)
                    .withDetail("message", "Security headers configuration is insufficient")
                    .build();
            }
            
        } catch (Exception e) {
            log.error("Error checking security headers health", e);
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
    
    /**
     * Check HSTS configuration
     */
    private void checkHstsConfiguration(Map<String, Object> details) {
        Map<String, Object> hstsDetails = new HashMap<>();
        hstsDetails.put("maxAge", properties.getHstsMaxAge());
        hstsDetails.put("includeSubDomains", properties.isHstsIncludeSubDomains());
        hstsDetails.put("preload", properties.isHstsPreload());
        
        // Check if HSTS is properly configured
        boolean hstsValid = properties.getHstsMaxAge() >= 31536000L && // At least 1 year
                           properties.isHstsIncludeSubDomains() &&
                           properties.isHstsPreload();
        
        hstsDetails.put("valid", hstsValid);
        if (!hstsValid) {
            hstsDetails.put("recommendation", "Set HSTS max-age to at least 1 year and enable includeSubDomains and preload");
        }
        
        details.put("hsts", hstsDetails);
    }
    
    /**
     * Check CSP configuration
     */
    private void checkCspConfiguration(Map<String, Object> details) {
        Map<String, Object> cspDetails = new HashMap<>();
        String csp = properties.getContentSecurityPolicy();
        
        cspDetails.put("configured", csp != null && !csp.isEmpty());
        
        if (csp != null) {
            // Check for important CSP directives
            cspDetails.put("hasDefaultSrc", csp.contains("default-src"));
            cspDetails.put("hasScriptSrc", csp.contains("script-src"));
            cspDetails.put("hasObjectSrc", csp.contains("object-src"));
            cspDetails.put("hasFrameAncestors", csp.contains("frame-ancestors"));
            cspDetails.put("hasUpgradeInsecureRequests", csp.contains("upgrade-insecure-requests"));
            cspDetails.put("hasReportUri", csp.contains("report-uri") || csp.contains("report-to"));
            
            // Check for unsafe directives
            boolean hasUnsafeInline = csp.contains("'unsafe-inline'");
            boolean hasUnsafeEval = csp.contains("'unsafe-eval'");
            cspDetails.put("hasUnsafeInline", hasUnsafeInline);
            cspDetails.put("hasUnsafeEval", hasUnsafeEval);
            
            if (hasUnsafeInline || hasUnsafeEval) {
                cspDetails.put("warning", "CSP contains unsafe directives that weaken security");
            }
        }
        
        details.put("csp", cspDetails);
    }
    
    /**
     * Check reporting configuration
     */
    private void checkReportingConfiguration(Map<String, Object> details) {
        Map<String, Object> reportingDetails = new HashMap<>();
        
        reportingDetails.put("enabled", properties.isEnableSecurityReporting());
        
        if (properties.isEnableSecurityReporting()) {
            reportingDetails.put("cspReportEndpoint", properties.getCspReportEndpoint());
            reportingDetails.put("ctReportEndpoint", properties.getCtReportEndpoint());
            reportingDetails.put("nelReportEndpoint", properties.getNelReportEndpoint());
            
            // Check if endpoints are configured
            boolean endpointsConfigured = properties.getCspReportEndpoint() != null &&
                                        properties.getCtReportEndpoint() != null &&
                                        properties.getNelReportEndpoint() != null;
            
            reportingDetails.put("endpointsConfigured", endpointsConfigured);
            
            if (!endpointsConfigured) {
                reportingDetails.put("warning", "Security reporting is enabled but endpoints are not fully configured");
            }
        }
        
        details.put("reporting", reportingDetails);
    }
    
    /**
     * Calculate security score based on configuration
     */
    private int calculateSecurityScore() {
        int score = 0;
        int maxScore = 100;
        
        // HSTS configuration (20 points)
        if (properties.getHstsMaxAge() >= 31536000L) score += 10;
        if (properties.isHstsIncludeSubDomains()) score += 5;
        if (properties.isHstsPreload()) score += 5;
        
        // CSP configuration (30 points)
        String csp = properties.getContentSecurityPolicy();
        if (csp != null && !csp.isEmpty()) {
            score += 10;
            if (csp.contains("default-src")) score += 5;
            if (csp.contains("object-src 'none'")) score += 5;
            if (csp.contains("frame-ancestors")) score += 5;
            if (!csp.contains("'unsafe-inline'") && !csp.contains("'unsafe-eval'")) score += 5;
        }
        
        // Frame options (10 points)
        if (properties.isFrameDenyEnabled()) score += 10;
        
        // Security reporting (10 points)
        if (properties.isEnableSecurityReporting()) score += 5;
        if (properties.getCspReportEndpoint() != null) score += 5;
        
        // Additional headers (30 points)
        if (properties.isEnableCustomHeaders()) score += 15;
        if (properties.isEnableApiHeaders()) score += 15;
        
        return Math.min(score, maxScore);
    }
}