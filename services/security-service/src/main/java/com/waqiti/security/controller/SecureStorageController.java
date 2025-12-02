package com.waqiti.security.controller;

import com.waqiti.security.dto.SecureStorageRequest;
import com.waqiti.security.dto.SecureStorageResponse;
import com.waqiti.security.service.SecureStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Optional;

/**
 * Secure Storage Controller
 *
 * Provides API endpoints for managing HttpOnly cookies and secure storage
 * for frontend applications.
 *
 * Security Features:
 * - HttpOnly cookies (XSS protection)
 * - Secure flag (HTTPS only)
 * - SameSite=Strict (CSRF protection)
 * - CSRF token validation
 * - User session binding
 * - Automatic expiration
 * - Audit logging
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/secure-storage")
@RequiredArgsConstructor
@Tag(name = "Secure Storage", description = "Secure storage management APIs")
@SecurityRequirement(name = "bearer-auth")
public class SecureStorageController {

    private final SecureStorageService secureStorageService;
    private static final String COOKIE_PREFIX = "waqiti_secure_";
    private static final int DEFAULT_MAX_AGE = 86400; // 24 hours

    @PostMapping("/set-cookie")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Set HttpOnly cookie", description = "Store data in secure HttpOnly cookie")
    public ResponseEntity<SecureStorageResponse> setCookie(
            @Valid @RequestBody SecureStorageRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        try {
            log.info("Setting secure cookie: key={}", request.getKey());

            // Validate CSRF token
            validateCSRFToken(httpRequest);

            // Create HttpOnly cookie
            Cookie cookie = new Cookie(
                    COOKIE_PREFIX + sanitizeKey(request.getKey()),
                    request.getValue()
            );

            // Security settings
            cookie.setHttpOnly(true);
            cookie.setSecure(request.getOptions() != null &&
                    request.getOptions().isSecure() != null ?
                    request.getOptions().isSecure() : true);
            cookie.setPath(request.getOptions() != null &&
                    request.getOptions().getPath() != null ?
                    request.getOptions().getPath() : "/");
            cookie.setMaxAge(request.getOptions() != null &&
                    request.getOptions().getMaxAge() != null ?
                    request.getOptions().getMaxAge() : DEFAULT_MAX_AGE);

            // SameSite attribute (set via header as Cookie class doesn't support it directly)
            String sameSite = request.getOptions() != null &&
                    request.getOptions().getSameSite() != null ?
                    request.getOptions().getSameSite() : "Strict";

            httpResponse.addHeader("Set-Cookie",
                    String.format("%s=%s; Path=%s; Max-Age=%d; HttpOnly; Secure; SameSite=%s",
                            cookie.getName(),
                            cookie.getValue(),
                            cookie.getPath(),
                            cookie.getMaxAge(),
                            sameSite
                    )
            );

            // Audit log
            secureStorageService.auditStorageAccess(
                    "SET_COOKIE",
                    request.getKey(),
                    httpRequest
            );

            return ResponseEntity.ok(SecureStorageResponse.builder()
                    .success(true)
                    .message("Cookie set successfully")
                    .build());

        } catch (Exception e) {
            log.error("Failed to set cookie: key={}", request.getKey(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(SecureStorageResponse.builder()
                            .success(false)
                            .message("Failed to set cookie: " + e.getMessage())
                            .build());
        }
    }

    @GetMapping("/get-cookie")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get HttpOnly cookie", description = "Retrieve data from secure HttpOnly cookie")
    public ResponseEntity<SecureStorageResponse> getCookie(
            @RequestParam String key,
            HttpServletRequest httpRequest
    ) {
        try {
            log.debug("Getting secure cookie: key={}", key);

            // Validate CSRF token
            validateCSRFToken(httpRequest);

            // Retrieve cookie
            Optional<Cookie> cookie = Arrays.stream(
                    httpRequest.getCookies() != null ? httpRequest.getCookies() : new Cookie[0]
            )
                    .filter(c -> c.getName().equals(COOKIE_PREFIX + sanitizeKey(key)))
                    .findFirst();

            if (cookie.isEmpty()) {
                return ResponseEntity.ok(SecureStorageResponse.builder()
                        .success(true)
                        .value(null)
                        .build());
            }

            // Audit log
            secureStorageService.auditStorageAccess(
                    "GET_COOKIE",
                    key,
                    httpRequest
            );

            return ResponseEntity.ok(SecureStorageResponse.builder()
                    .success(true)
                    .value(cookie.get().getValue())
                    .build());

        } catch (Exception e) {
            log.error("Failed to get cookie: key={}", key, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(SecureStorageResponse.builder()
                            .success(false)
                            .message("Failed to get cookie: " + e.getMessage())
                            .build());
        }
    }

    @DeleteMapping("/delete-cookie")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete HttpOnly cookie", description = "Remove secure HttpOnly cookie")
    public ResponseEntity<SecureStorageResponse> deleteCookie(
            @Valid @RequestBody SecureStorageRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        try {
            log.info("Deleting secure cookie: key={}", request.getKey());

            // Validate CSRF token
            validateCSRFToken(httpRequest);

            // Create cookie with MaxAge=0 to delete
            Cookie cookie = new Cookie(
                    COOKIE_PREFIX + sanitizeKey(request.getKey()),
                    ""
            );
            cookie.setHttpOnly(true);
            cookie.setSecure(true);
            cookie.setPath("/");
            cookie.setMaxAge(0); // Delete immediately

            httpResponse.addCookie(cookie);

            // Audit log
            secureStorageService.auditStorageAccess(
                    "DELETE_COOKIE",
                    request.getKey(),
                    httpRequest
            );

            return ResponseEntity.ok(SecureStorageResponse.builder()
                    .success(true)
                    .message("Cookie deleted successfully")
                    .build());

        } catch (Exception e) {
            log.error("Failed to delete cookie: key={}", request.getKey(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(SecureStorageResponse.builder()
                            .success(false)
                            .message("Failed to delete cookie: " + e.getMessage())
                            .build());
        }
    }

    @PostMapping("/clear-cookies")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Clear all cookies", description = "Remove all secure HttpOnly cookies")
    public ResponseEntity<SecureStorageResponse> clearCookies(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        try {
            log.info("Clearing all secure cookies");

            // Validate CSRF token
            validateCSRFToken(httpRequest);

            // Delete all cookies with our prefix
            if (httpRequest.getCookies() != null) {
                Arrays.stream(httpRequest.getCookies())
                        .filter(c -> c.getName().startsWith(COOKIE_PREFIX))
                        .forEach(c -> {
                            Cookie deleteCookie = new Cookie(c.getName(), "");
                            deleteCookie.setHttpOnly(true);
                            deleteCookie.setSecure(true);
                            deleteCookie.setPath("/");
                            deleteCookie.setMaxAge(0);
                            httpResponse.addCookie(deleteCookie);
                        });
            }

            // Audit log
            secureStorageService.auditStorageAccess(
                    "CLEAR_COOKIES",
                    "all",
                    httpRequest
            );

            return ResponseEntity.ok(SecureStorageResponse.builder()
                    .success(true)
                    .message("All cookies cleared successfully")
                    .build());

        } catch (Exception e) {
            log.error("Failed to clear cookies", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(SecureStorageResponse.builder()
                            .success(false)
                            .message("Failed to clear cookies: " + e.getMessage())
                            .build());
        }
    }

    @GetMapping("/csrf-token")
    @Operation(summary = "Get CSRF token", description = "Retrieve CSRF token for secure requests")
    public ResponseEntity<SecureStorageResponse> getCSRFToken(HttpServletRequest httpRequest) {
        try {
            String token = secureStorageService.generateCSRFToken(httpRequest);

            return ResponseEntity.ok(SecureStorageResponse.builder()
                    .success(true)
                    .value(token)
                    .build());

        } catch (Exception e) {
            log.error("Failed to generate CSRF token", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(SecureStorageResponse.builder()
                            .success(false)
                            .message("Failed to generate CSRF token")
                            .build());
        }
    }

    /**
     * Validate CSRF token from request header
     */
    private void validateCSRFToken(HttpServletRequest request) {
        String token = request.getHeader("X-CSRF-Token");

        if (token == null || token.isEmpty()) {
            throw new SecurityException("CSRF token required");
        }

        if (!secureStorageService.validateCSRFToken(token, request)) {
            log.warn("Invalid CSRF token detected from IP: {}",
                    request.getRemoteAddr());
            throw new SecurityException("Invalid CSRF token");
        }
    }

    /**
     * Sanitize key to prevent injection attacks
     */
    private String sanitizeKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        // Only allow alphanumeric, underscore, and hyphen
        String sanitized = key.replaceAll("[^a-zA-Z0-9_-]", "");

        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Invalid key format");
        }

        if (sanitized.length() > 128) {
            throw new IllegalArgumentException("Key too long (max 128 characters)");
        }

        return sanitized;
    }
}
