package com.waqiti.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * CSRF Protection Filter
 * 
 * Automatically handles CSRF token generation and validation for all requests
 * Provides comprehensive protection against Cross-Site Request Forgery attacks
 */
@Component
@Order(2) // After XSS filter
@RequiredArgsConstructor
@Slf4j
public class CSRFProtectionFilter implements Filter {

    private final CSRFProtectionService csrfProtectionService;
    private final ObjectMapper objectMapper;
    
    // HTTP methods that require CSRF protection
    private static final Set<String> PROTECTED_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");
    
    // Endpoints that should be excluded from CSRF protection
    private static final Set<String> EXCLUDED_PATHS = Set.of(
        "/api/v1/auth/login",
        "/api/v1/auth/register",
        "/api/v1/webhook",
        "/health",
        "/metrics",
        "/actuator",
        "/api/v1/public"
    );
    
    // Content types that should be excluded (typically API calls)
    private static final Set<String> EXCLUDED_CONTENT_TYPES = Set.of(
        "application/json",
        "application/xml",
        "text/xml"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String method = httpRequest.getMethod();
        String path = httpRequest.getRequestURI();
        String contentType = httpRequest.getContentType();
        
        // Skip CSRF protection for excluded paths
        if (shouldExcludePath(path)) {
            chain.doFilter(request, response);
            return;
        }
        
        // Skip CSRF protection for API requests with JSON content type
        // (These should use other protection mechanisms like JWT)
        if (shouldExcludeContentType(contentType)) {
            chain.doFilter(request, response);
            return;
        }
        
        try {
            if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
                // Generate and set CSRF token for safe methods
                handleSafeMethod(httpRequest, httpResponse);
            } else if (PROTECTED_METHODS.contains(method)) {
                // Validate CSRF token for unsafe methods
                if (!handleUnsafeMethod(httpRequest, httpResponse)) {
                    return; // Request was rejected
                }
            }
            
            chain.doFilter(request, response);
            
        } catch (Exception e) {
            log.error("CSRF filter error for {} {}", method, path, e);
            sendCSRFError(httpResponse, "CSRF protection error");
        }
    }

    private void handleSafeMethod(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        String sessionId = getSessionId(request);
        if (sessionId == null) {
            return; // No session, skip CSRF token generation
        }
        
        String userAgent = request.getHeader("User-Agent");
        String ipAddress = getClientIpAddress(request);
        
        // Generate new CSRF token
        CSRFProtectionService.CSRFToken csrfToken = csrfProtectionService.generateToken(
            sessionId, userAgent, ipAddress);
        
        if (csrfToken.enabled()) {
            // Set CSRF token cookie
            Cookie csrfCookie = new Cookie(csrfToken.cookieName(), csrfToken.rawToken());
            csrfCookie.setHttpOnly(false); // JavaScript needs to read this for AJAX requests
            csrfCookie.setSecure(true);
            csrfCookie.setPath("/");
            csrfCookie.setDomain(getDomainFromRequest(request));
            csrfCookie.setMaxAge(csrfToken.validitySeconds());
            csrfCookie.setAttribute("SameSite", "Strict");
            
            response.addCookie(csrfCookie);
            
            // Also set as response header for AJAX requests
            response.setHeader("X-CSRF-Token", csrfToken.rawToken());
            
            log.debug("Generated CSRF token for session: {}", sessionId);
        }
    }

    private boolean handleUnsafeMethod(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        String sessionId = getSessionId(request);
        if (sessionId == null) {
            sendCSRFError(response, "No session found");
            return false;
        }
        
        // Extract CSRF tokens
        String cookieToken = extractTokenFromCookie(request);
        String headerToken = request.getHeader("X-XSRF-TOKEN");
        String userAgent = request.getHeader("User-Agent");
        String ipAddress = getClientIpAddress(request);
        
        // Validate Origin/Referer headers
        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");
        
        if (!csrfProtectionService.validateOrigin(origin, referer)) {
            log.warn("CSRF validation failed: Invalid origin/referer for session: {}", sessionId);
            sendCSRFError(response, "Invalid origin");
            return false;
        }
        
        // Validate CSRF token
        CSRFProtectionService.CSRFValidationResult validationResult = 
            csrfProtectionService.validateToken(sessionId, cookieToken, headerToken, userAgent, ipAddress);
        
        if (!validationResult.valid()) {
            log.warn("CSRF validation failed for session {}: {}", sessionId, validationResult.errorMessage());
            sendCSRFError(response, validationResult.errorMessage());
            return false;
        }
        
        log.debug("CSRF token validated successfully for session: {}", sessionId);
        return true;
    }

    private String extractTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        
        for (Cookie cookie : cookies) {
            if ("XSRF-TOKEN".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String getSessionId(HttpServletRequest request) {
        // Try to get session ID from various sources
        
        // 1. From HTTP session
        if (request.getSession(false) != null) {
            return request.getSession().getId();
        }
        
        // 2. From JWT token (simplified extraction)
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                // Extract user ID from JWT as session identifier
                // This is simplified - in production, properly parse JWT
                String token = authorization.substring(7);
                String[] parts = token.split("\\.");
                if (parts.length == 3) {
                    // Use JWT jti (JWT ID) or sub (subject) as session ID
                    return java.util.UUID.nameUUIDFromBytes(token.getBytes()).toString();
                }
            } catch (Exception e) {
                log.debug("Failed to extract session from JWT", e);
            }
        }
        
        // 3. From custom session cookie
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("JSESSIONID".equals(cookie.getName()) || "SESSION".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        
        return null;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        // Check various headers for real IP address
        String[] headerNames = {
            "X-Forwarded-For",
            "X-Real-IP",
            "X-Client-IP",
            "CF-Connecting-IP", // Cloudflare
            "True-Client-IP"
        };
        
        for (String headerName : headerNames) {
            String ip = request.getHeader(headerName);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For can contain multiple IPs, use the first one
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }
        
        return request.getRemoteAddr();
    }

    private String getDomainFromRequest(HttpServletRequest request) {
        String serverName = request.getServerName();
        if (serverName.startsWith("www.")) {
            return serverName.substring(4);
        }
        return serverName;
    }

    private boolean shouldExcludePath(String path) {
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }

    private boolean shouldExcludeContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        
        return EXCLUDED_CONTENT_TYPES.stream()
            .anyMatch(excluded -> contentType.toLowerCase().contains(excluded));
    }

    private void sendCSRFError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        
        Map<String, Object> error = new HashMap<>();
        error.put("error", "CSRF_TOKEN_INVALID");
        error.put("message", message);
        error.put("timestamp", System.currentTimeMillis());
        error.put("requiresRefresh", true);
        
        response.getWriter().write(objectMapper.writeValueAsString(error));
        response.getWriter().flush();
    }
}