package com.waqiti.security.csrf;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * CSRF Protection Filter for Financial Endpoints
 *
 * CRITICAL SECURITY: Implements CSRF protection for state-changing operations
 * - Validates CSRF tokens on all POST, PUT, PATCH, DELETE requests
 * - Exempts safe methods (GET, HEAD, OPTIONS) as per OWASP guidelines
 * - Exempts public endpoints (login, registration)
 * - Exempts service-to-service API calls (identified by API key)
 * - Enforces double-submit cookie pattern for SPAs
 *
 * PCI DSS 6.5.9: Protects against Cross-Site Request Forgery (CSRF)
 * OWASP Top 10: A01:2021 - Broken Access Control
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CsrfProtectionFilter extends OncePerRequestFilter {

    private final CsrfTokenRepository csrfTokenRepository;
    private final ServiceRegistryClient serviceRegistryClient;
    private final ServiceTokenValidator serviceTokenValidator;

    // HTTP methods that require CSRF protection
    private static final List<String> PROTECTED_METHODS = Arrays.asList("POST", "PUT", "PATCH", "DELETE");

    // Endpoints exempt from CSRF protection
    private static final RequestMatcher EXEMPT_URLS = new OrRequestMatcher(
        // Public authentication endpoints
        new AntPathRequestMatcher("/api/v1/auth/login"),
        new AntPathRequestMatcher("/api/v1/auth/register"),
        new AntPathRequestMatcher("/api/v1/auth/refresh"),
        new AntPathRequestMatcher("/api/v1/auth/forgot-password"),
        new AntPathRequestMatcher("/api/v1/auth/reset-password"),
        new AntPathRequestMatcher("/api/v1/auth/verify-email"),
        new AntPathRequestMatcher("/api/v1/auth/resend-verification"),

        // Webhooks from payment providers (verified by signature)
        new AntPathRequestMatcher("/api/v1/webhooks/**"),

        // Service-to-service endpoints (verified by service tokens)
        new AntPathRequestMatcher("/internal/**"),

        // Actuator endpoints (protected by separate auth)
        new AntPathRequestMatcher("/actuator/**"),

        // Public endpoints
        new AntPathRequestMatcher("/swagger-ui/**"),
        new AntPathRequestMatcher("/v3/api-docs/**"),
        new AntPathRequestMatcher("/health"),
        new AntPathRequestMatcher("/info")
    );

    // Financial endpoints that MUST have CSRF protection
    private static final RequestMatcher CRITICAL_FINANCIAL_URLS = new OrRequestMatcher(
        new AntPathRequestMatcher("/api/v1/payments/**"),
        new AntPathRequestMatcher("/api/v1/transfers/**"),
        new AntPathRequestMatcher("/api/v1/withdrawals/**"),
        new AntPathRequestMatcher("/api/v1/wallets/*/transfer"),
        new AntPathRequestMatcher("/api/v1/wallets/*/withdraw"),
        new AntPathRequestMatcher("/api/v1/investments/*/buy"),
        new AntPathRequestMatcher("/api/v1/investments/*/sell"),
        new AntPathRequestMatcher("/api/v1/beneficiaries/add"),
        new AntPathRequestMatcher("/api/v1/beneficiaries/*/verify"),
        new AntPathRequestMatcher("/api/v1/cards/add"),
        new AntPathRequestMatcher("/api/v1/bank-accounts/add"),
        new AntPathRequestMatcher("/api/v1/scheduled-payments/create"),
        new AntPathRequestMatcher("/api/v1/limits/update")
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String method = request.getMethod();
        String requestUri = request.getRequestURI();

        // Allow safe methods without CSRF check
        if (!PROTECTED_METHODS.contains(method)) {
            // For GET requests on protected endpoints, generate and send CSRF token
            if ("GET".equals(method)) {
                provideCsrfToken(request, response);
            }
            filterChain.doFilter(request, response);
            return;
        }

        // Check if URL is exempt from CSRF protection
        if (EXEMPT_URLS.matches(request)) {
            log.debug("SECURITY: CSRF check exempted for: {} {}", method, requestUri);
            filterChain.doFilter(request, response);
            return;
        }

        // Check for service-to-service API key authentication
        if (isServiceToServiceRequest(request)) {
            log.debug("SECURITY: CSRF check exempted for service-to-service call: {} {}", method, requestUri);
            filterChain.doFilter(request, response);
            return;
        }

        // Validate CSRF token
        if (!validateCsrfToken(request)) {
            log.warn("SECURITY: CSRF validation failed for: {} {} from IP: {}",
                method, requestUri, getClientIp(request));

            // Enhanced logging for financial endpoints
            if (CRITICAL_FINANCIAL_URLS.matches(request)) {
                log.error("SECURITY ALERT: CSRF attack attempt on financial endpoint: {} {} from IP: {} User-Agent: {}",
                    method, requestUri, getClientIp(request), request.getHeader("User-Agent"));
            }

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"CSRF token validation failed\",\"code\":\"CSRF_INVALID\"}");
            return;
        }

        // CSRF validation passed - rotate token for critical financial operations
        if (CRITICAL_FINANCIAL_URLS.matches(request)) {
            rotateCsrfToken(request, response);
            log.info("SECURITY: CSRF token rotated for financial operation: {} {}", method, requestUri);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Validate CSRF token from request
     */
    private boolean validateCsrfToken(HttpServletRequest request) {
        // Load expected token from repository
        CsrfToken expectedToken = csrfTokenRepository.loadToken(request);
        if (expectedToken == null) {
            log.debug("SECURITY: No CSRF token found in repository");
            return false;
        }

        // Get actual token from request (header or parameter)
        String actualToken = getTokenFromRequest(request, expectedToken);
        if (actualToken == null || actualToken.isEmpty()) {
            log.debug("SECURITY: No CSRF token provided in request");
            return false;
        }

        // Constant-time comparison to prevent timing attacks
        boolean valid = constantTimeEquals(expectedToken.getToken(), actualToken);

        if (!valid) {
            log.debug("SECURITY: CSRF token mismatch");
        }

        return valid;
    }

    /**
     * Get CSRF token from request (header or parameter)
     */
    private String getTokenFromRequest(HttpServletRequest request, CsrfToken token) {
        // Try header first (preferred for API calls)
        String headerToken = request.getHeader(token.getHeaderName());
        if (headerToken != null && !headerToken.isEmpty()) {
            return headerToken;
        }

        // Fallback to request parameter (for forms)
        return request.getParameter(token.getParameterName());
    }

    /**
     * Constant-time string comparison to prevent timing attacks
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }

        return result == 0;
    }

    /**
     * Provide CSRF token for GET requests (for subsequent POST/PUT/DELETE)
     */
    private void provideCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = csrfTokenRepository.loadToken(request);
        if (token == null) {
            token = csrfTokenRepository.generateToken(request);
            csrfTokenRepository.saveToken(token, request, response);
        } else {
            // Refresh token in response header
            response.setHeader(token.getHeaderName(), token.getToken());
        }
    }

    /**
     * Rotate CSRF token after sensitive operations
     */
    private void rotateCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        csrfTokenRepository.rotateToken(request, response);
    }

    /**
     * Check if request is service-to-service communication
     *
     * PCI DSS 6.5.3: Insecure authentication and session management
     */
    private boolean isServiceToServiceRequest(HttpServletRequest request) {
        // Check for service API key header
        String apiKey = request.getHeader("X-Service-API-Key");
        if (apiKey != null && !apiKey.isEmpty()) {
            try {
                // Validate API key against service registry
                String serviceName = request.getHeader("X-Service-Name");
                if (serviceName == null || serviceName.isEmpty()) {
                    log.warn("Service API key provided but no service name header found");
                    return false;
                }

                boolean isValid = serviceRegistryClient.validateServiceApiKey(serviceName, apiKey);

                if (isValid) {
                    log.info("Valid service-to-service request from service: {}", serviceName);
                    // Set service name as request attribute for audit logging
                    request.setAttribute("authenticatedService", serviceName);
                    return true;
                } else {
                    log.warn("Invalid API key for service: {} from IP: {}",
                        serviceName, getClientIp(request));
                    return false;
                }
            } catch (Exception e) {
                log.error("Error validating service API key", e);
                return false;
            }
        }

        // Check for internal service token
        String serviceToken = request.getHeader("X-Internal-Service-Token");
        if (serviceToken != null && !serviceToken.isEmpty()) {
            try {
                // Validate service token (JWT-based)
                ServiceTokenValidationResult result = serviceTokenValidator.validateToken(serviceToken);

                if (result.isValid()) {
                    log.info("Valid internal service token from service: {}", result.getServiceName());

                    // Check token expiration
                    if (result.isExpired()) {
                        log.warn("Service token expired for service: {}", result.getServiceName());
                        return false;
                    }

                    // Check token permissions
                    String requestPath = request.getRequestURI();
                    if (!result.hasPermission(requestPath)) {
                        log.warn("Service {} lacks permission for path: {}",
                            result.getServiceName(), requestPath);
                        return false;
                    }

                    // Set service context for audit logging
                    request.setAttribute("authenticatedService", result.getServiceName());
                    request.setAttribute("serviceTokenId", result.getTokenId());

                    return true;
                } else {
                    log.warn("Invalid service token from IP: {}, reason: {}",
                        getClientIp(request), result.getFailureReason());
                    return false;
                }
            } catch (Exception e) {
                log.error("Error validating service token", e);
                return false;
            }
        }

        return false;
    }

    /**
     * Get client IP address from request
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}
