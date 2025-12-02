package com.waqiti.common.config.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.io.IOException;

/**
 * CRITICAL: Security Headers Configuration
 *
 * SECURITY REQUIREMENTS:
 * - OWASP Top 10 A05:2021 - Security Misconfiguration
 * - OWASP Secure Headers Project
 * - PCI DSS 4.0 Requirement 6.5.10: Broken Authentication and Session Management
 * - NIST SP 800-53: SC-8 Transmission Confidentiality
 *
 * SECURITY HEADERS IMPLEMENTED:
 * 1. HSTS (HTTP Strict Transport Security) - Forces HTTPS
 * 2. X-Frame-Options - Prevents clickjacking
 * 3. X-Content-Type-Options - Prevents MIME-sniffing
 * 4. X-XSS-Protection - Enables XSS filter
 * 5. Content-Security-Policy - Prevents XSS and data injection
 * 6. Referrer-Policy - Controls referrer information
 * 7. Permissions-Policy - Controls browser features
 * 8. X-Permitted-Cross-Domain-Policies - Controls cross-domain access
 *
 * BUSINESS IMPACT:
 * - Prevents XSS attacks (Cross-Site Scripting)
 * - Prevents clickjacking and UI redressing attacks
 * - Prevents MIME-type confusion attacks
 * - Protects against man-in-the-middle attacks
 * - Reduces attack surface for browser-based vulnerabilities
 * - Maintains PCI DSS and GDPR compliance
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 * @since 2025-10-09
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "waqiti.security.headers.enabled", havingValue = "true", matchIfMissing = true)
public class SecurityHeadersConfig {

    @Value("${waqiti.security.https.hsts.enabled:true}")
    private boolean hstsEnabled;

    @Value("${waqiti.security.https.hsts.max-age:31536000}")
    private long hstsMaxAge;

    @Value("${waqiti.security.https.hsts.include-subdomains:true}")
    private boolean hstsIncludeSubdomains;

    @Value("${waqiti.security.https.hsts.preload:true}")
    private boolean hstsPreload;

    @Value("${waqiti.security.headers.csp.enabled:true}")
    private boolean cspEnabled;

    @Value("${waqiti.security.headers.csp.policy:default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'}")
    private String cspPolicy;

    @Value("${waqiti.security.headers.frame-options:DENY}")
    private String frameOptions;

    @Value("${waqiti.security.headers.referrer-policy:strict-origin-when-cross-origin}")
    private String referrerPolicy;

    @Value("${waqiti.security.headers.permissions-policy:camera=(), microphone=(), geolocation=(self), payment=(self)}")
    private String permissionsPolicy;

    /**
     * Security headers filter
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public SecurityHeadersFilter securityHeadersFilter() {
        log.info("SECURITY: Security headers filter enabled - HSTS: {}, CSP: {}, X-Frame-Options: {}",
            hstsEnabled, cspEnabled, frameOptions);

        return new SecurityHeadersFilter(
            hstsEnabled, hstsMaxAge, hstsIncludeSubdomains, hstsPreload,
            cspEnabled, cspPolicy, frameOptions, referrerPolicy, permissionsPolicy
        );
    }

    /**
     * Filter that adds comprehensive security headers to all responses
     */
    @Slf4j
    public static class SecurityHeadersFilter implements Filter {

        private final boolean hstsEnabled;
        private final String hstsHeaderValue;
        private final boolean cspEnabled;
        private final String cspPolicy;
        private final String frameOptions;
        private final String referrerPolicy;
        private final String permissionsPolicy;

        public SecurityHeadersFilter(boolean hstsEnabled, long hstsMaxAge, boolean hstsIncludeSubdomains,
                                    boolean hstsPreload, boolean cspEnabled, String cspPolicy,
                                    String frameOptions, String referrerPolicy, String permissionsPolicy) {
            this.hstsEnabled = hstsEnabled;
            this.cspEnabled = cspEnabled;
            this.cspPolicy = cspPolicy;
            this.frameOptions = frameOptions;
            this.referrerPolicy = referrerPolicy;
            this.permissionsPolicy = permissionsPolicy;

            // Build HSTS header value
            if (hstsEnabled) {
                StringBuilder hstsValue = new StringBuilder("max-age=").append(hstsMaxAge);
                if (hstsIncludeSubdomains) {
                    hstsValue.append("; includeSubDomains");
                }
                if (hstsPreload) {
                    hstsValue.append("; preload");
                }
                this.hstsHeaderValue = hstsValue.toString();
            } else {
                this.hstsHeaderValue = null;
            }

            log.info("SECURITY: Security headers filter initialized");
            log.info("  - HSTS: {}", hstsEnabled ? hstsHeaderValue : "disabled");
            log.info("  - CSP: {}", cspEnabled ? "enabled" : "disabled");
            log.info("  - X-Frame-Options: {}", frameOptions);
            log.info("  - Referrer-Policy: {}", referrerPolicy);
            log.info("  - Permissions-Policy: {}", permissionsPolicy);
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {

            HttpServletResponse httpResponse = (HttpServletResponse) response;

            // 1. HSTS (HTTP Strict Transport Security)
            // Forces browser to only use HTTPS for all future requests
            if (hstsEnabled && hstsHeaderValue != null) {
                httpResponse.setHeader("Strict-Transport-Security", hstsHeaderValue);
            }

            // 2. X-Frame-Options
            // Prevents clickjacking by controlling whether browser allows page to be framed
            httpResponse.setHeader("X-Frame-Options", frameOptions);

            // 3. X-Content-Type-Options
            // Prevents MIME-sniffing, forcing browser to respect declared content type
            httpResponse.setHeader("X-Content-Type-Options", "nosniff");

            // 4. X-XSS-Protection
            // Enables browser's XSS filtering (legacy but still useful for old browsers)
            httpResponse.setHeader("X-XSS-Protection", "1; mode=block");

            // 5. Content-Security-Policy (CSP)
            // Prevents XSS, clickjacking, and other code injection attacks
            if (cspEnabled && cspPolicy != null && !cspPolicy.isEmpty()) {
                httpResponse.setHeader("Content-Security-Policy", cspPolicy);
            }

            // 6. Referrer-Policy
            // Controls how much referrer information is included with requests
            httpResponse.setHeader("Referrer-Policy", referrerPolicy);

            // 7. Permissions-Policy (formerly Feature-Policy)
            // Controls which browser features and APIs can be used
            httpResponse.setHeader("Permissions-Policy", permissionsPolicy);

            // 8. X-Permitted-Cross-Domain-Policies
            // Controls cross-domain access from Flash and PDF documents
            httpResponse.setHeader("X-Permitted-Cross-Domain-Policies", "none");

            // 9. X-Download-Options
            // Prevents IE from executing downloads in site's context (IE-specific)
            httpResponse.setHeader("X-Download-Options", "noopen");

            // 10. Cache-Control for sensitive endpoints
            // Prevent caching of sensitive data
            String requestURI = ((jakarta.servlet.http.HttpServletRequest) request).getRequestURI();
            if (isSensitiveEndpoint(requestURI)) {
                httpResponse.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private");
                httpResponse.setHeader("Pragma", "no-cache");
                httpResponse.setHeader("Expires", "0");
            }

            chain.doFilter(request, response);
        }

        /**
         * Determine if endpoint handles sensitive data
         */
        private boolean isSensitiveEndpoint(String uri) {
            return uri.contains("/api/") ||
                   uri.contains("/payment") ||
                   uri.contains("/wallet") ||
                   uri.contains("/transaction") ||
                   uri.contains("/user") ||
                   uri.contains("/account") ||
                   uri.contains("/auth") ||
                   uri.contains("/kyc") ||
                   uri.contains("/compliance");
        }

        @Override
        public void init(FilterConfig filterConfig) throws ServletException {
            log.info("SECURITY: Security headers filter started");
        }

        @Override
        public void destroy() {
            log.info("SECURITY: Security headers filter stopped");
        }
    }
}
