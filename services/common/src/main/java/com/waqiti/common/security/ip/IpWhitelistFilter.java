/**
 * IP Whitelist Security Filter
 * Intercepts all requests to validate IP addresses against whitelist
 * Provides comprehensive request filtering with detailed logging
 */
package com.waqiti.common.security.ip;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance IP filtering with request context awareness
 * Integrates with Spring Security for authenticated user tracking
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1) // Execute early in filter chain
@ConditionalOnProperty(name = "security.ip-whitelist.filter.enabled", havingValue = "true", matchIfMissing = true)
public class IpWhitelistFilter extends OncePerRequestFilter {

    private final IpWhitelistService ipWhitelistService;
    private final ObjectMapper objectMapper;

    // Request tracking
    private final ConcurrentHashMap<String, RequestInfo> activeRequests = new ConcurrentHashMap<>();

    @Value("${security.ip-whitelist.filter.bypass-paths:/health,/metrics,/actuator/**}")
    private String bypassPaths;

    @Value("${security.ip-whitelist.filter.include-user-agent:true}")
    private boolean includeUserAgent;

    @Value("${security.ip-whitelist.filter.detailed-logging:true}")
    private boolean detailedLogging;

    @Value("${security.ip-whitelist.filter.response-headers:true}")
    private boolean addResponseHeaders;

    @Value("${security.ip-whitelist.filter.block-response-type:json}")
    private String blockResponseType;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String requestId = generateRequestId();
        String clientIp = extractClientIpAddress(request);
        String requestPath = request.getRequestURI();
        String userAgent = includeUserAgent ? request.getHeader("User-Agent") : null;
        
        // Track request
        RequestInfo requestInfo = RequestInfo.builder()
            .requestId(requestId)
            .ipAddress(clientIp)
            .requestPath(requestPath)
            .userAgent(userAgent)
            .startTime(Instant.now())
            .method(request.getMethod())
            .build();
        
        activeRequests.put(requestId, requestInfo);

        try {
            // Check if path should be bypassed
            if (shouldBypassFilter(requestPath)) {
                if (detailedLogging) {
                    log.debug("Bypassing IP filter for path: {}", requestPath);
                }
                filterChain.doFilter(request, response);
                return;
            }

            // Get current user context
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String userId = authentication != null ? authentication.getName() : "anonymous";

            // Validate IP address
            IpWhitelistService.IpValidationResult validationResult = ipWhitelistService
                .validateIpAccess(clientIp, userAgent, userId);

            if (validationResult.isAllowed()) {
                // Add security headers
                if (addResponseHeaders) {
                    addSecurityHeaders(response, validationResult);
                }
                
                // Log successful access
                if (detailedLogging) {
                    log.debug("IP access granted for {} from {} - Risk Score: {}", 
                        userId, clientIp, validationResult.getRiskScore());
                }
                
                // Update request info
                requestInfo.setAllowed(true);
                requestInfo.setUserId(userId);
                requestInfo.setRiskScore(validationResult.getRiskScore());
                
                // Continue with request
                filterChain.doFilter(request, response);
                
            } else {
                // Block the request
                handleBlockedRequest(request, response, validationResult, requestInfo);
            }

        } catch (Exception e) {
            log.error("Error in IP whitelist filter for IP: {}", clientIp, e);
            
            // Fail secure - block request on error
            handleFilterError(request, response, e, requestInfo);
            
        } finally {
            // Clean up request tracking
            requestInfo.setEndTime(Instant.now());
            activeRequests.remove(requestId);
            
            // Log request completion
            if (detailedLogging) {
                long duration = requestInfo.getEndTime().toEpochMilli() - 
                              requestInfo.getStartTime().toEpochMilli();
                log.debug("Request {} completed in {}ms - IP: {}, Path: {}, Allowed: {}", 
                    requestId, duration, clientIp, requestPath, requestInfo.isAllowed());
            }
        }
    }

    /**
     * Handle blocked request
     */
    private void handleBlockedRequest(HttpServletRequest request, HttpServletResponse response,
                                    IpWhitelistService.IpValidationResult validationResult,
                                    RequestInfo requestInfo) throws IOException {
        
        requestInfo.setAllowed(false);
        requestInfo.setBlockReason(validationResult.getDenialReason());
        
        // Set response status and headers
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setHeader("X-Request-Blocked", "true");
        response.setHeader("X-Block-Reason", validationResult.getDenialReason());
        
        // Log blocked request
        log.warn("IP access denied for {} from {} - Reason: {}", 
            requestInfo.getUserId(), requestInfo.getIpAddress(), validationResult.getDenialReason());
        
        // Send appropriate response
        sendBlockedResponse(response, validationResult);
    }

    /**
     * Handle filter errors
     */
    private void handleFilterError(HttpServletRequest request, HttpServletResponse response,
                                 Exception error, RequestInfo requestInfo) throws IOException {
        
        requestInfo.setAllowed(false);
        requestInfo.setBlockReason("Filter error: " + error.getMessage());
        
        // Set error response
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.setHeader("X-Request-Error", "true");
        
        // Send error response
        sendErrorResponse(response, error);
    }

    /**
     * Send blocked response based on configuration
     */
    private void sendBlockedResponse(HttpServletResponse response, 
                                   IpWhitelistService.IpValidationResult validationResult) throws IOException {
        
        if ("json".equalsIgnoreCase(blockResponseType)) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Access Denied");
            errorResponse.put("message", validationResult.getDenialReason());
            errorResponse.put("timestamp", Instant.now());
            errorResponse.put("status", HttpStatus.FORBIDDEN.value());
            
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
            
        } else if ("html".equalsIgnoreCase(blockResponseType)) {
            response.setContentType(MediaType.TEXT_HTML_VALUE);
            
            String htmlResponse = buildHtmlBlockedResponse(validationResult);
            response.getWriter().write(htmlResponse);
            
        } else {
            // Plain text response
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            response.getWriter().write("Access Denied: " + validationResult.getDenialReason());
        }
        
        response.getWriter().flush();
    }

    /**
     * Send error response
     */
    private void sendErrorResponse(HttpServletResponse response, Exception error) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Internal Server Error");
        errorResponse.put("message", "Request processing failed");
        errorResponse.put("timestamp", Instant.now());
        errorResponse.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        response.getWriter().flush();
    }

    /**
     * Add security headers to response
     */
    private void addSecurityHeaders(HttpServletResponse response, 
                                  IpWhitelistService.IpValidationResult validationResult) {
        response.setHeader("X-IP-Validated", "true");
        response.setHeader("X-IP-Risk-Score", String.valueOf(validationResult.getRiskScore()));
        
        if (validationResult.getGeoLocation() != null) {
            response.setHeader("X-IP-Location", validationResult.getGeoLocation());
        }
        
        if (validationResult.getWhitelistMatch() != null) {
            response.setHeader("X-Whitelist-Match", "true");
        }
    }

    /**
     * Extract client IP address from request
     */
    private String extractClientIpAddress(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For",
            "X-Real-IP", 
            "X-Originating-IP",
            "X-Cluster-Client-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
        };

        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (isValidIpAddress(ip)) {
                // Handle comma-separated IPs (X-Forwarded-For can contain multiple IPs)
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }

        // Fall back to remote address
        String remoteAddr = request.getRemoteAddr();
        return isValidIpAddress(remoteAddr) ? remoteAddr : "unknown";
    }

    /**
     * Check if IP address is valid
     */
    private boolean isValidIpAddress(String ip) {
        return ip != null && 
               !ip.isEmpty() && 
               !"unknown".equalsIgnoreCase(ip) &&
               !"0:0:0:0:0:0:0:1".equals(ip) &&
               !"::1".equals(ip);
    }

    /**
     * Check if request path should bypass the filter
     */
    private boolean shouldBypassFilter(String requestPath) {
        if (bypassPaths == null || bypassPaths.isEmpty()) {
            return false;
        }

        String[] paths = bypassPaths.split(",");
        for (String path : paths) {
            String trimmedPath = path.trim();
            
            if (trimmedPath.endsWith("/**")) {
                // Wildcard matching
                String basePath = trimmedPath.substring(0, trimmedPath.length() - 3);
                if (requestPath.startsWith(basePath)) {
                    return true;
                }
            } else if (trimmedPath.endsWith("/*")) {
                // Single level wildcard
                String basePath = trimmedPath.substring(0, trimmedPath.length() - 2);
                if (requestPath.startsWith(basePath) && 
                    requestPath.substring(basePath.length()).indexOf('/') == -1) {
                    return true;
                }
            } else if (requestPath.equals(trimmedPath)) {
                // Exact match
                return true;
            }
        }

        return false;
    }

    /**
     * Generate unique request ID
     */
    private String generateRequestId() {
        return "req-" + System.currentTimeMillis() + "-" + 
               Integer.toHexString(System.identityHashCode(Thread.currentThread()));
    }

    /**
     * Build HTML blocked response
     */
    private String buildHtmlBlockedResponse(IpWhitelistService.IpValidationResult validationResult) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Access Denied</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 50px; text-align: center; }
                    .container { max-width: 600px; margin: 0 auto; }
                    .error { color: #d32f2f; }
                    .timestamp { color: #666; font-size: 0.9em; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1 class="error">Access Denied</h1>
                    <p>Your request has been blocked due to security policies.</p>
                    <p><strong>Reason:</strong> %s</p>
                    <p class="timestamp">Time: %s</p>
                    <hr>
                    <p><small>If you believe this is an error, please contact support.</small></p>
                </div>
            </body>
            </html>
            """.formatted(validationResult.getDenialReason(), Instant.now());
    }

    /**
     * Get active request statistics
     */
    public Map<String, Object> getActiveRequestStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeRequests", activeRequests.size());
        stats.put("allowedRequests", activeRequests.values().stream().mapToLong(r -> r.isAllowed() ? 1 : 0).sum());
        stats.put("blockedRequests", activeRequests.values().stream().mapToLong(r -> !r.isAllowed() ? 1 : 0).sum());
        
        Map<String, Long> requestsByIp = new HashMap<>();
        activeRequests.values().forEach(req -> 
            requestsByIp.merge(req.getIpAddress(), 1L, Long::sum));
        stats.put("requestsByIp", requestsByIp);
        
        return stats;
    }

    /**
     * Request information tracking
     */
    @lombok.Data
    @lombok.Builder
    private static class RequestInfo {
        private String requestId;
        private String ipAddress;
        private String requestPath;
        private String method;
        private String userAgent;
        private String userId;
        private Instant startTime;
        private Instant endTime;
        private boolean allowed;
        private String blockReason;
        private int riskScore;
    }
}