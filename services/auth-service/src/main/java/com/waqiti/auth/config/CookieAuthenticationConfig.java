package com.waqiti.auth.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Enterprise-grade HTTP-only cookie configuration for secure token storage.
 *
 * Security Features:
 * - HTTP-only cookies (prevents XSS access)
 * - Secure flag (HTTPS only in production)
 * - SameSite=Strict (prevents CSRF)
 * - Path restrictions
 * - Domain restrictions
 * - Proper expiration handling
 *
 * Compliance:
 * - OWASP Session Management
 * - PCI-DSS 6.5.10 (Broken Authentication)
 * - GDPR Cookie Consent
 *
 * @author Waqiti Security Team
 * @version 2.0.0
 * @since 2025-10-11
 */
@Slf4j
@Component
@Configuration
public class CookieAuthenticationConfig {

    @Value("${waqiti.security.cookie.domain:waqiti.com}")
    private String cookieDomain;

    @Value("${waqiti.security.cookie.secure:true}")
    private boolean secureCookie;

    @Value("${waqiti.security.cookie.http-only:true}")
    private boolean httpOnly;

    @Value("${waqiti.security.cookie.same-site:Strict}")
    private String sameSite;

    @Value("${waqiti.security.cookie.path:/}")
    private String cookiePath;

    @Value("${waqiti.security.token.access-token-validity:900}") // 15 minutes
    private int accessTokenValidity;

    @Value("${waqiti.security.token.refresh-token-validity:604800}") // 7 days
    private int refreshTokenValidity;

    // Cookie names
    public static final String ACCESS_TOKEN_COOKIE = "waqiti_access_token";
    public static final String REFRESH_TOKEN_COOKIE = "waqiti_refresh_token";
    public static final String CSRF_TOKEN_COOKIE = "waqiti_csrf_token";
    public static final String SESSION_ID_COOKIE = "waqiti_session_id";

    /**
     * Creates a secure HTTP-only cookie for access token storage.
     *
     * Security Controls:
     * - HTTP-only: true (prevents JavaScript access)
     * - Secure: true in production (HTTPS only)
     * - SameSite: Strict (prevents CSRF)
     * - Max-Age: 15 minutes (short-lived)
     *
     * @param token The JWT access token
     * @param response HTTP response to add cookie
     */
    public void setAccessTokenCookie(String token, HttpServletResponse response) {
        Cookie cookie = createSecureCookie(ACCESS_TOKEN_COOKIE, token, accessTokenValidity);
        response.addCookie(cookie);

        // Add security headers
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-XSS-Protection", "1; mode=block");

        log.debug("Access token cookie set with expiration: {} seconds", accessTokenValidity);
    }

    /**
     * Creates a secure HTTP-only cookie for refresh token storage.
     *
     * Enhanced Security:
     * - Longer expiration (7 days) for user convenience
     * - HTTP-only and Secure flags enforced
     * - Path restricted to /api/auth/refresh
     * - SameSite=Strict for CSRF protection
     *
     * @param token The refresh token
     * @param response HTTP response to add cookie
     */
    public void setRefreshTokenCookie(String token, HttpServletResponse response) {
        Cookie cookie = createSecureCookie(REFRESH_TOKEN_COOKIE, token, refreshTokenValidity);
        // Restrict refresh token to refresh endpoint only
        cookie.setPath("/api/auth/refresh");
        response.addCookie(cookie);

        log.debug("Refresh token cookie set with expiration: {} seconds", refreshTokenValidity);
    }

    /**
     * Creates a CSRF token cookie (readable by JavaScript for X-CSRF-TOKEN header).
     *
     * Note: This cookie is NOT HTTP-only as it needs to be read by frontend
     * to include in request headers. The CSRF token itself provides protection.
     *
     * @param csrfToken The CSRF token
     * @param response HTTP response to add cookie
     */
    public void setCsrfTokenCookie(String csrfToken, HttpServletResponse response) {
        Cookie cookie = new Cookie(CSRF_TOKEN_COOKIE, csrfToken);
        cookie.setHttpOnly(false); // Must be readable by JavaScript
        cookie.setSecure(secureCookie);
        cookie.setPath(cookiePath);
        cookie.setDomain(cookieDomain);
        cookie.setMaxAge(accessTokenValidity);
        cookie.setAttribute("SameSite", sameSite);
        response.addCookie(cookie);

        log.debug("CSRF token cookie set");
    }

    /**
     * Retrieves access token from HTTP-only cookie.
     *
     * @param request HTTP request containing cookies
     * @return Optional containing the access token if present
     */
    public Optional<String> getAccessTokenFromCookie(HttpServletRequest request) {
        return getCookieValue(request, ACCESS_TOKEN_COOKIE);
    }

    /**
     * Retrieves refresh token from HTTP-only cookie.
     *
     * @param request HTTP request containing cookies
     * @return Optional containing the refresh token if present
     */
    public Optional<String> getRefreshTokenFromCookie(HttpServletRequest request) {
        return getCookieValue(request, REFRESH_TOKEN_COOKIE);
    }

    /**
     * Retrieves CSRF token from cookie.
     *
     * @param request HTTP request containing cookies
     * @return Optional containing the CSRF token if present
     */
    public Optional<String> getCsrfTokenFromCookie(HttpServletRequest request) {
        return getCookieValue(request, CSRF_TOKEN_COOKIE);
    }

    /**
     * Clears all authentication cookies (logout).
     *
     * Security: Ensures complete session cleanup
     * - Sets Max-Age=0 to delete cookies
     * - Maintains security flags
     * - Clears both access and refresh tokens
     *
     * @param response HTTP response to add deletion cookies
     */
    public void clearAuthenticationCookies(HttpServletResponse response) {
        clearCookie(ACCESS_TOKEN_COOKIE, cookiePath, response);
        clearCookie(REFRESH_TOKEN_COOKIE, "/api/auth/refresh", response);
        clearCookie(CSRF_TOKEN_COOKIE, cookiePath, response);
        clearCookie(SESSION_ID_COOKIE, cookiePath, response);

        log.info("All authentication cookies cleared");
    }

    /**
     * Creates a secure cookie with all security flags enabled.
     *
     * @param name Cookie name
     * @param value Cookie value
     * @param maxAge Maximum age in seconds
     * @return Configured secure cookie
     */
    private Cookie createSecureCookie(String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(httpOnly);
        cookie.setSecure(secureCookie);
        cookie.setPath(cookiePath);
        cookie.setDomain(cookieDomain);
        cookie.setMaxAge(maxAge);
        cookie.setAttribute("SameSite", sameSite);

        return cookie;
    }

    /**
     * Retrieves cookie value from request.
     *
     * @param request HTTP request
     * @param cookieName Cookie name to retrieve
     * @return Optional containing cookie value if present
     */
    private Optional<String> getCookieValue(HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }

        return Arrays.stream(request.getCookies())
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    /**
     * Clears a specific cookie.
     *
     * @param cookieName Cookie name to clear
     * @param path Cookie path
     * @param response HTTP response
     */
    private void clearCookie(String cookieName, String path, HttpServletResponse response) {
        Cookie cookie = new Cookie(cookieName, null);
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookie);
        cookie.setPath(path);
        cookie.setDomain(cookieDomain);
        cookie.setMaxAge(0); // Delete cookie
        cookie.setAttribute("SameSite", sameSite);
        response.addCookie(cookie);
    }

    /**
     * Validates cookie security configuration.
     *
     * @throws IllegalStateException if insecure configuration detected in production
     */
    public void validateConfiguration() {
        if (isProductionEnvironment() && !secureCookie) {
            throw new IllegalStateException(
                "SECURITY ERROR: Secure cookies MUST be enabled in production environment"
            );
        }

        if (!httpOnly) {
            log.warn("WARNING: HTTP-only cookies are disabled. This is a security risk!");
        }

        if (!"Strict".equals(sameSite) && !"Lax".equals(sameSite)) {
            log.warn("WARNING: SameSite attribute should be 'Strict' or 'Lax' for CSRF protection");
        }

        if (accessTokenValidity > 3600) { // 1 hour
            log.warn("WARNING: Access token validity exceeds recommended 1 hour");
        }

        log.info("Cookie authentication configuration validated successfully");
    }

    /**
     * Checks if running in production environment.
     *
     * @return true if production environment
     */
    private boolean isProductionEnvironment() {
        String environment = System.getenv("SPRING_PROFILES_ACTIVE");
        return environment != null && environment.contains("prod");
    }

    /**
     * Gets cookie configuration summary for audit logging.
     *
     * @return Configuration summary
     */
    public String getConfigurationSummary() {
        return String.format(
            "Cookie Config: domain=%s, secure=%s, httpOnly=%s, sameSite=%s, " +
            "accessTokenValidity=%ds, refreshTokenValidity=%ds",
            cookieDomain, secureCookie, httpOnly, sameSite,
            accessTokenValidity, refreshTokenValidity
        );
    }
}
