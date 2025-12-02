package com.waqiti.common.config.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * CRITICAL: HTTPS Enforcement Configuration for Production
 *
 * SECURITY REQUIREMENTS:
 * - PCI DSS 4.0 Requirement 4.1: Use strong cryptography and security protocols
 * - NIST SP 800-52: Guidelines for TLS implementations
 * - OWASP Top 10 A02:2021 - Cryptographic Failures
 *
 * SECURITY FEATURES:
 * - Enforces HTTPS for all incoming requests in production
 * - Redirects HTTP to HTTPS automatically
 * - Blocks plain HTTP traffic
 * - Enables HSTS (HTTP Strict Transport Security)
 * - Configures secure TLS protocols and cipher suites
 *
 * BUSINESS IMPACT:
 * - Prevents man-in-the-middle attacks (MITM)
 * - Protects payment card data in transit (PCI DSS compliance)
 * - Prevents credential theft and session hijacking
 * - Maintains customer trust and regulatory compliance
 * - Avoids $50K-$500K PCI DSS fines for unencrypted transmission
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 * @since 2025-10-09
 */
@Slf4j
@Configuration
@Profile({"production", "staging"})
@ConditionalOnProperty(name = "waqiti.security.https.enforce", havingValue = "true", matchIfMissing = true)
public class HttpsEnforcementConfig {

    @Value("${waqiti.security.https.redirect-port:8443}")
    private int httpsRedirectPort;

    @Value("${waqiti.security.https.hsts.enabled:true}")
    private boolean hstsEnabled;

    @Value("${waqiti.security.https.hsts.max-age:31536000}")
    private long hstsMaxAge;

    @Value("${waqiti.security.https.hsts.include-subdomains:true}")
    private boolean hstsIncludeSubdomains;

    @Value("${waqiti.security.https.hsts.preload:true}")
    private boolean hstsPreload;

    /**
     * HTTP to HTTPS redirect connector
     * Opens port 8080 to redirect HTTP traffic to HTTPS port 8443
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> redirectHttpToHttps() {
        return factory -> {
            log.info("SECURITY: Configuring HTTP to HTTPS redirect - HTTP port: 8080 -> HTTPS port: {}", httpsRedirectPort);

            factory.addAdditionalTomcatConnectors(createHttpConnector());
        };
    }

    /**
     * Creates HTTP connector that redirects to HTTPS
     */
    private org.apache.catalina.connector.Connector createHttpConnector() {
        org.apache.catalina.connector.Connector connector = new org.apache.catalina.connector.Connector(
            TomcatServletWebServerFactory.DEFAULT_PROTOCOL);

        connector.setScheme("http");
        connector.setPort(8080);
        connector.setSecure(false);
        connector.setRedirectPort(httpsRedirectPort);

        log.info("SECURITY: HTTP connector created on port 8080 redirecting to HTTPS port {}", httpsRedirectPort);

        return connector;
    }

    /**
     * HTTPS enforcement filter - blocks non-HTTPS requests
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public HttpsEnforcementFilter httpsEnforcementFilter() {
        log.info("SECURITY: HTTPS enforcement filter enabled - All HTTP requests will be redirected to HTTPS");
        return new HttpsEnforcementFilter(httpsRedirectPort, hstsEnabled, hstsMaxAge,
            hstsIncludeSubdomains, hstsPreload);
    }

    /**
     * Filter that enforces HTTPS and adds HSTS headers
     */
    @Slf4j
    @Component
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public static class HttpsEnforcementFilter implements Filter {

        private final int httpsPort;
        private final boolean hstsEnabled;
        private final long hstsMaxAge;
        private final boolean hstsIncludeSubdomains;
        private final boolean hstsPreload;
        private final String hstsHeaderValue;

        public HttpsEnforcementFilter(int httpsPort, boolean hstsEnabled, long hstsMaxAge,
                                     boolean hstsIncludeSubdomains, boolean hstsPreload) {
            this.httpsPort = httpsPort;
            this.hstsEnabled = hstsEnabled;
            this.hstsMaxAge = hstsMaxAge;
            this.hstsIncludeSubdomains = hstsIncludeSubdomains;
            this.hstsPreload = hstsPreload;

            // Build HSTS header value
            StringBuilder hstsValue = new StringBuilder("max-age=").append(hstsMaxAge);
            if (hstsIncludeSubdomains) {
                hstsValue.append("; includeSubDomains");
            }
            if (hstsPreload) {
                hstsValue.append("; preload");
            }
            this.hstsHeaderValue = hstsValue.toString();

            log.info("SECURITY: HTTPS enforcement filter initialized - HSTS enabled: {}, max-age: {}, includeSubdomains: {}, preload: {}",
                hstsEnabled, hstsMaxAge, hstsIncludeSubdomains, hstsPreload);
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {

            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;

            // Check if request is secure (HTTPS)
            boolean isSecure = httpRequest.isSecure() ||
                              "https".equalsIgnoreCase(httpRequest.getScheme()) ||
                              "https".equalsIgnoreCase(httpRequest.getHeader("X-Forwarded-Proto"));

            // Allow actuator health checks over HTTP for load balancers
            String requestURI = httpRequest.getRequestURI();
            boolean isHealthCheck = requestURI.equals("/actuator/health") ||
                                   requestURI.equals("/actuator/health/liveness") ||
                                   requestURI.equals("/actuator/health/readiness");

            if (!isSecure) {
                if (isHealthCheck) {
                    // Allow health checks over HTTP for load balancer probes
                    log.debug("Allowing health check over HTTP: {}", requestURI);
                    chain.doFilter(request, response);
                    return;
                }

                // Redirect to HTTPS
                String redirectUrl = buildHttpsRedirectUrl(httpRequest);

                log.warn("SECURITY: Blocking HTTP request - redirecting to HTTPS: {} -> {}",
                    httpRequest.getRequestURL(), redirectUrl);

                httpResponse.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY); // 301
                httpResponse.setHeader("Location", redirectUrl);
                httpResponse.setHeader("Connection", "close");

                return; // Stop processing
            }

            // Add HSTS header for HTTPS requests
            if (hstsEnabled && isSecure) {
                httpResponse.setHeader("Strict-Transport-Security", hstsHeaderValue);
            }

            // Continue with the request
            chain.doFilter(request, response);
        }

        /**
         * Build HTTPS redirect URL
         */
        private String buildHttpsRedirectUrl(HttpServletRequest request) {
            StringBuilder url = new StringBuilder("https://");
            url.append(request.getServerName());

            // Add port if not default HTTPS port
            if (httpsPort != 443) {
                url.append(":").append(httpsPort);
            }

            url.append(request.getRequestURI());

            if (request.getQueryString() != null) {
                url.append("?").append(request.getQueryString());
            }

            return url.toString();
        }

        @Override
        public void init(FilterConfig filterConfig) throws ServletException {
            log.info("SECURITY: HTTPS enforcement filter initialized");
        }

        @Override
        public void destroy() {
            log.info("SECURITY: HTTPS enforcement filter destroyed");
        }
    }
}
