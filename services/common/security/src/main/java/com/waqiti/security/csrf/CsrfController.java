package com.waqiti.security.csrf;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * CSRF Token Controller
 *
 * Provides CSRF tokens to frontend applications (SPAs, mobile apps)
 * for subsequent POST/PUT/DELETE requests.
 *
 * Usage:
 * 1. Frontend calls GET /api/v1/csrf/token on app initialization
 * 2. Frontend stores token and includes it in X-CSRF-TOKEN header for all state-changing requests
 * 3. Frontend refreshes token periodically (every 25 minutes) or on 403 CSRF errors
 */
@RestController
@RequestMapping("/api/v1/csrf")
@RequiredArgsConstructor
@Slf4j
public class CsrfController {

    private final CsrfTokenRepository csrfTokenRepository;

    /**
     * Get CSRF token for frontend applications
     *
     * @return CSRF token with metadata
     */
    @GetMapping("/token")
    public ResponseEntity<Map<String, Object>> getCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        // Load or generate CSRF token
        CsrfToken token = csrfTokenRepository.loadToken(request);

        if (token == null) {
            token = csrfTokenRepository.generateToken(request);
            csrfTokenRepository.saveToken(token, request, response);
            log.debug("SECURITY: Generated new CSRF token for client");
        } else {
            // Token already exists, just return it
            log.debug("SECURITY: Returning existing CSRF token");
        }

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("token", token.getToken());
        responseBody.put("headerName", token.getHeaderName());
        responseBody.put("parameterName", token.getParameterName());
        responseBody.put("expiresInMinutes", 30);

        return ResponseEntity.ok(responseBody);
    }

    /**
     * Validate if current CSRF token is still valid
     *
     * @return token validity status
     */
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(HttpServletRequest request) {
        CsrfToken token = csrfTokenRepository.loadToken(request);

        Map<String, Object> responseBody = new HashMap<>();

        if (token == null) {
            responseBody.put("valid", false);
            responseBody.put("message", "No CSRF token found");
            return ResponseEntity.ok(responseBody);
        }

        boolean expired = csrfTokenRepository.isTokenExpired(token.getToken());

        responseBody.put("valid", !expired);
        responseBody.put("message", expired ? "Token expired" : "Token valid");

        if (expired) {
            log.debug("SECURITY: CSRF token expired for validation request");
        }

        return ResponseEntity.ok(responseBody);
    }

    /**
     * Refresh CSRF token (generates new token)
     *
     * @return new CSRF token
     */
    @GetMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        // Rotate the token
        CsrfToken newToken = csrfTokenRepository.rotateToken(request, response);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("token", newToken.getToken());
        responseBody.put("headerName", newToken.getHeaderName());
        responseBody.put("parameterName", newToken.getParameterName());
        responseBody.put("expiresInMinutes", 30);
        responseBody.put("message", "Token refreshed successfully");

        log.info("SECURITY: CSRF token refreshed for client");

        return ResponseEntity.ok(responseBody);
    }
}
