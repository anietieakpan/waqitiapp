package com.waqiti.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.web.filter.OncePerRequestFilter;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * HTTPS Enforcement and Security Headers Configuration
 * 
 * CRITICAL SECURITY IMPLEMENTATION:
 * - Forces HTTPS for all communications
 * - Implements HSTS (HTTP Strict Transport Security)
 * - Adds comprehensive security headers
 * - Provides CSP (Content Security Policy) configuration
 * - Implements secure cookie settings
 * 
 * Production Security Features:
 * - HSTS with preload support
 * - X-Frame-Options protection
 * - X-Content-Type-Options nosniff
 * - X-XSS-Protection
 * - Referrer Policy configuration
 * - Feature Policy headers
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(HttpsEnforcementConfiguration.SecurityHeadersProperties.class)
@Slf4j
public class HttpsEnforcementConfiguration {

    @Data
    @ConfigurationProperties(prefix = "waqiti.security.headers")
    public static class SecurityHeadersProperties {
        private boolean enabled = true;
        private Hsts hsts = new Hsts();
        private Csp csp = new Csp();
        private FrameOptions frameOptions = new FrameOptions();
        private ContentTypeOptions contentTypeOptions = new ContentTypeOptions();
        private XssProtection xssProtection = new XssProtection();
        private ReferrerPolicy referrerPolicy = new ReferrerPolicy();
        private FeaturePolicy featurePolicy = new FeaturePolicy();
        private List<String> allowedOrigins = List.of("https://api.example.com", "https://api.example.com");

        @Data
        public static class Hsts {
            private boolean enabled = true;
            private long maxAgeInSeconds = 31536000; // 1 year
            private boolean includeSubdomains = true;
            private boolean preload = true;
        }

        @Data
        public static class Csp {
            private boolean enabled = true;
            private String policy = "default-src 'self'; script-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data: https:; connect-src 'self' https:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'";
            private boolean reportOnly = false;
            private String reportUri;
        }

        @Data
        public static class FrameOptions {
            private boolean enabled = true;
            private String policy = "DENY";
        }

        @Data
        public static class ContentTypeOptions {
            private boolean enabled = true;
        }

        @Data
        public static class XssProtection {
            private boolean enabled = true;
            private String mode = "1; mode=block";
        }

        @Data
        public static class ReferrerPolicy {
            private boolean enabled = true;
            private String policy = "strict-origin-when-cross-origin";
        }

        @Data
        public static class FeaturePolicy {
            private boolean enabled = true;
            private String policy = "camera 'none'; microphone 'none'; geolocation 'self'; payment 'self'";
        }
    }

    private final SecurityHeadersProperties securityHeadersProperties;

    public HttpsEnforcementConfiguration(SecurityHeadersProperties securityHeadersProperties) {
        this.securityHeadersProperties = securityHeadersProperties;
    }

    /**
     * Main security filter chain with HTTPS enforcement
     */
    @Bean
    @Order(1)
    @ConditionalOnProperty(name = "waqiti.security.headers.enabled", havingValue = "true", matchIfMissing = true)
    public SecurityFilterChain httpsSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring HTTPS enforcement and security headers");

        http
            // HTTPS Enforcement
            .requiresChannel(channel -> 
                channel.requestMatchers("/**").requiresSecure()
            )
            
            // Security Headers Configuration
            .headers(headers -> {
                // HSTS Configuration
                if (securityHeadersProperties.getHsts().isEnabled()) {
                    headers.httpStrictTransportSecurity(hsts -> {
                        hsts.maxAgeInSeconds(securityHeadersProperties.getHsts().getMaxAgeInSeconds())
                            .includeSubDomains(securityHeadersProperties.getHsts().isIncludeSubdomains())
                            .preload(securityHeadersProperties.getHsts().isPreload());
                    });
                    log.info("HSTS enabled with max-age: {} seconds",
                        securityHeadersProperties.getHsts().getMaxAgeInSeconds());
                }

                // Frame Options
                if (securityHeadersProperties.getFrameOptions().isEnabled()) {
                    String framePolicy = securityHeadersProperties.getFrameOptions().getPolicy();
                    switch (framePolicy.toUpperCase()) {
                        case "DENY":
                            headers.frameOptions(frameOptions -> frameOptions.deny());
                            break;
                        case "SAMEORIGIN":
                            headers.frameOptions(frameOptions -> frameOptions.sameOrigin());
                            break;
                        default:
                            headers.frameOptions(frameOptions -> frameOptions.deny());
                    }
                    log.info("X-Frame-Options set to: {}", framePolicy);
                }

                // Content Type Options
                if (securityHeadersProperties.getContentTypeOptions().isEnabled()) {
                    headers.contentTypeOptions(contentTypeOptions -> {});
                    log.info("X-Content-Type-Options: nosniff enabled");
                }

                // XSS Protection
                if (securityHeadersProperties.getXssProtection().isEnabled()) {
                    headers.addHeaderWriter(new XXssProtectionHeaderWriter());
                    log.info("X-XSS-Protection enabled");
                }

                // Referrer Policy
                if (securityHeadersProperties.getReferrerPolicy().isEnabled()) {
                    ReferrerPolicyHeaderWriter.ReferrerPolicy policy =
                        ReferrerPolicyHeaderWriter.ReferrerPolicy.valueOf(
                            securityHeadersProperties.getReferrerPolicy().getPolicy()
                                .toUpperCase().replace("-", "_")
                        );
                    headers.referrerPolicy(referrerPolicy -> referrerPolicy.policy(policy));
                    log.info("Referrer-Policy set to: {}", policy);
                }

                // Cache Control for sensitive endpoints
                headers.cacheControl(cacheControl -> {});
            })

            // CORS Configuration
            .cors(cors -> cors.configurationSource(request -> {
                var corsConfig = new org.springframework.web.cors.CorsConfiguration();
                corsConfig.setAllowedOriginPatterns(securityHeadersProperties.getAllowedOrigins());
                corsConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                // SECURITY FIX (P1-005): Replace wildcard with explicit header whitelist
                corsConfig.setAllowedHeaders(List.of(
                    "Authorization", "Content-Type", "X-Requested-With", "X-CSRF-TOKEN",
                    "X-Session-ID", "Accept", "Origin", "Cache-Control", "X-File-Name"));
                corsConfig.setAllowCredentials(true);
                corsConfig.setMaxAge(3600L);
                return corsConfig;
            }))

            // CSRF Configuration
            .csrf(csrf -> csrf
                .csrfTokenRepository(org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers("/actuator/health", "/actuator/info")
            );

        log.info("HTTPS enforcement security filter chain configured successfully");
        return http.build();
    }

    /**
     * Security Headers Filter for custom headers
     */
    @Bean
    @ConditionalOnProperty(name = "waqiti.security.headers.enabled", havingValue = "true", matchIfMissing = true)
    public SecurityHeadersFilter securityHeadersFilter() {
        return new SecurityHeadersFilter(securityHeadersProperties);
    }

    /**
     * Custom filter for additional security headers
     */
    public static class SecurityHeadersFilter extends OncePerRequestFilter {
        private final SecurityHeadersProperties properties;

        public SecurityHeadersFilter(SecurityHeadersProperties properties) {
            this.properties = properties;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                      FilterChain filterChain) throws ServletException, IOException {
            
            // Content Security Policy
            if (properties.getCsp().isEnabled()) {
                String headerName = properties.getCsp().isReportOnly() ? 
                    "Content-Security-Policy-Report-Only" : "Content-Security-Policy";
                response.setHeader(headerName, properties.getCsp().getPolicy());
                
                if (properties.getCsp().getReportUri() != null) {
                    response.setHeader("Report-To", properties.getCsp().getReportUri());
                }
            }

            // Feature Policy / Permissions Policy
            if (properties.getFeaturePolicy().isEnabled()) {
                response.setHeader("Permissions-Policy", properties.getFeaturePolicy().getPolicy());
            }

            // Additional Security Headers
            response.setHeader("X-Permitted-Cross-Domain-Policies", "none");
            response.setHeader("X-Download-Options", "noopen");
            response.setHeader("Cross-Origin-Embedder-Policy", "require-corp");
            response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
            response.setHeader("Cross-Origin-Resource-Policy", "same-origin");

            // Secure Cookie Settings
            if (response.getHeader("Set-Cookie") != null) {
                String cookie = response.getHeader("Set-Cookie");
                if (!cookie.contains("Secure")) {
                    cookie += "; Secure";
                }
                if (!cookie.contains("SameSite")) {
                    cookie += "; SameSite=Strict";
                }
                response.setHeader("Set-Cookie", cookie);
            }

            filterChain.doFilter(request, response);
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            // Skip for health checks and metrics
            String path = request.getRequestURI();
            return path.startsWith("/actuator/health") || 
                   path.startsWith("/actuator/info") ||
                   path.startsWith("/actuator/prometheus");
        }
    }

    /**
     * HTTPS Redirect Filter for non-secure requests
     */
    @Bean
    @ConditionalOnProperty(name = "waqiti.security.https.enforce", havingValue = "true", matchIfMissing = true)
    public HttpsRedirectFilter httpsRedirectFilter() {
        return new HttpsRedirectFilter();
    }

    /**
     * Filter to redirect HTTP requests to HTTPS
     */
    public static class HttpsRedirectFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                      FilterChain filterChain) throws ServletException, IOException {
            
            // Check if request is coming through a proxy with X-Forwarded-Proto
            String xForwardedProto = request.getHeader("X-Forwarded-Proto");
            String scheme = request.getScheme();
            
            boolean isHttps = "https".equals(scheme) || "https".equals(xForwardedProto);
            
            if (!isHttps && !isHealthCheck(request)) {
                String httpsUrl = buildHttpsUrl(request);
                log.debug("Redirecting HTTP request to HTTPS: {}", httpsUrl);
                response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
                response.setHeader("Location", httpsUrl);
                response.setHeader("Strict-Transport-Security", 
                    "max-age=31536000; includeSubDomains; preload");
                return;
            }

            filterChain.doFilter(request, response);
        }

        private boolean isHealthCheck(HttpServletRequest request) {
            String path = request.getRequestURI();
            return path.startsWith("/actuator/health") || 
                   path.startsWith("/health") ||
                   path.equals("/");
        }

        private String buildHttpsUrl(HttpServletRequest request) {
            StringBuilder httpsUrl = new StringBuilder("https://");
            httpsUrl.append(request.getServerName());
            
            // Add port if not standard HTTPS port
            int port = request.getServerPort();
            if (port != 443 && port != 8443) {
                httpsUrl.append(":").append(port);
            }
            
            httpsUrl.append(request.getRequestURI());
            
            if (request.getQueryString() != null) {
                httpsUrl.append("?").append(request.getQueryString());
            }
            
            return httpsUrl.toString();
        }
    }

    /**
     * Secure Session Configuration
     */
    @Bean
    @ConditionalOnProperty(name = "waqiti.security.session.secure", havingValue = "true", matchIfMissing = true)
    public org.springframework.boot.web.servlet.ServletContextInitializer secureSessionConfig() {
        return servletContext -> {
            // Configure secure session cookies
            var sessionConfig = servletContext.getSessionCookieConfig();
            sessionConfig.setHttpOnly(true);
            sessionConfig.setSecure(true);
            sessionConfig.setName("WAQITI_SESSION");
            sessionConfig.setMaxAge(3600); // 1 hour
            sessionConfig.setAttribute("SameSite", "Strict");
            
            log.info("Secure session configuration applied");
        };
    }
}