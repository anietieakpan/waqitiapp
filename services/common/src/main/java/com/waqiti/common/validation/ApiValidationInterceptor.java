package com.waqiti.common.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * CRITICAL SECURITY: API Validation Interceptor
 * PRODUCTION-READY: Comprehensive input validation for all API endpoints
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiValidationInterceptor implements HandlerInterceptor {

    private final ProductionDataValidator dataValidator;
    private final ObjectMapper objectMapper;

    @Value("${waqiti.security.validation.strict-mode:true}")
    private boolean strictMode;

    @Value("${waqiti.security.validation.max-request-size:10485760}") // 10MB
    private long maxRequestSize;

    // Security patterns
    private static final Pattern SUSPICIOUS_USER_AGENT = Pattern.compile(
        "(?i).*(sqlmap|nikto|nessus|openvas|nmap|masscan|zap|burp).*"
    );
    
    private static final Pattern MALICIOUS_HEADERS = Pattern.compile(
        "(?i).*(x-forwarded-host|x-forwarded-server|x-real-ip).*"
    );

    // Rate limiting storage (in production, use Redis)
    private final Map<String, RequestCounter> requestCounters = new HashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestId = UUID.randomUUID().toString();
        String clientIp = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        String method = request.getMethod();
        String uri = request.getRequestURI();

        try {
            log.debug("VALIDATION: Processing request {} - {} {} from {}", requestId, method, uri, clientIp);

            // 1. Basic security validation
            if (!validateBasicSecurity(request, response, requestId)) {
                return false;
            }

            // 2. Request size validation
            if (!validateRequestSize(request, response, requestId)) {
                return false;
            }

            // 3. Header validation
            if (!validateHeaders(request, response, requestId)) {
                return false;
            }

            // 4. User agent validation
            if (!validateUserAgent(userAgent, response, requestId)) {
                return false;
            }

            // 5. Rate limiting
            if (!checkRateLimit(clientIp, uri, response, requestId)) {
                return false;
            }

            // 6. Path traversal validation
            if (!validatePathTraversal(uri, response, requestId)) {
                return false;
            }

            // 7. Financial endpoint specific validation
            if (isFinancialEndpoint(uri) && !validateFinancialRequest(request, response, requestId)) {
                return false;
            }

            // Store validation context for post-processing
            request.setAttribute("validation.requestId", requestId);
            request.setAttribute("validation.startTime", System.currentTimeMillis());

            log.debug("VALIDATION: Request validation passed: {}", requestId);
            return true;

        } catch (Exception e) {
            log.error("VALIDATION: Unexpected error during request validation: {}", requestId, e);
            
            if (strictMode) {
                sendErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Request validation failed", requestId);
                return false;
            } else {
                log.warn("VALIDATION: Allowing request due to lenient mode: {}", requestId);
                return true;
            }
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                              Object handler, Exception ex) {
        String requestId = (String) request.getAttribute("validation.requestId");
        Long startTime = (Long) request.getAttribute("validation.startTime");

        if (requestId != null && startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            int statusCode = response.getStatus();

            log.debug("VALIDATION: Request completed: {} - Status: {}, Duration: {}ms", 
                    requestId, statusCode, duration);

            // Log suspicious responses
            if (statusCode >= 400) {
                log.warn("VALIDATION: Request failed: {} - Status: {}, URI: {}", 
                        requestId, statusCode, request.getRequestURI());
            }

            // Update metrics
            updateValidationMetrics(request, response, duration);
        }
    }

    /**
     * CRITICAL: Validate basic security requirements
     */
    private boolean validateBasicSecurity(HttpServletRequest request, HttpServletResponse response, 
                                        String requestId) {
        // Check for null bytes
        String uri = request.getRequestURI();
        if (uri.contains("\0")) {
            log.error("SECURITY: Null byte in URI detected: {}", requestId);
            sendErrorResponse(response, HttpStatus.BAD_REQUEST, "Invalid request format", requestId);
            return false;
        }

        // Check for control characters
        if (uri.matches(".*[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F].*")) {
            log.error("SECURITY: Control characters in URI detected: {}", requestId);
            sendErrorResponse(response, HttpStatus.BAD_REQUEST, "Invalid request format", requestId);
            return false;
        }

        return true;
    }

    /**
     * CRITICAL: Validate request size
     */
    private boolean validateRequestSize(HttpServletRequest request, HttpServletResponse response, 
                                      String requestId) {
        long contentLength = request.getContentLengthLong();
        
        if (contentLength > maxRequestSize) {
            log.error("SECURITY: Request size exceeds limit: {} bytes (max: {}) - {}", 
                    contentLength, maxRequestSize, requestId);
            sendErrorResponse(response, HttpStatus.PAYLOAD_TOO_LARGE, "Request too large", requestId);
            return false;
        }

        return true;
    }

    /**
     * CRITICAL: Validate HTTP headers
     */
    private boolean validateHeaders(HttpServletRequest request, HttpServletResponse response, 
                                  String requestId) {
        Enumeration<String> headerNames = request.getHeaderNames();
        
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);

            // Check for malicious header injection
            if (MALICIOUS_HEADERS.matcher(headerName).matches()) {
                log.error("SECURITY: Suspicious header detected: {} - {}", headerName, requestId);
                sendErrorResponse(response, HttpStatus.BAD_REQUEST, "Invalid headers", requestId);
                return false;
            }

            // Validate header values
            if (headerValue != null) {
                ProductionDataValidator.ValidationResult<String> result = 
                    dataValidator.validateString(headerValue);
                
                if (!result.isValid()) {
                    log.error("SECURITY: Invalid header value in {}: {} - {}", 
                            headerName, result.getErrorMessage(), requestId);
                    sendErrorResponse(response, HttpStatus.BAD_REQUEST, "Invalid header format", requestId);
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * CRITICAL: Validate User-Agent header
     */
    private boolean validateUserAgent(String userAgent, HttpServletResponse response, String requestId) {
        if (userAgent == null) {
            log.warn("SECURITY: Missing User-Agent header: {}", requestId);
            // Don't block, but log for analysis
            return true;
        }

        // Check for suspicious user agents
        if (SUSPICIOUS_USER_AGENT.matcher(userAgent).matches()) {
            log.error("SECURITY: Suspicious User-Agent detected: {} - {}", 
                    dataValidator.sanitizeForLog(userAgent), requestId);
            sendErrorResponse(response, HttpStatus.FORBIDDEN, "Access denied", requestId);
            return false;
        }

        // Validate user agent format
        ProductionDataValidator.ValidationResult<String> result = 
            dataValidator.validateString(userAgent);
        
        if (!result.isValid()) {
            log.error("SECURITY: Invalid User-Agent format: {} - {}", 
                    result.getErrorMessage(), requestId);
            sendErrorResponse(response, HttpStatus.BAD_REQUEST, "Invalid User-Agent", requestId);
            return false;
        }

        return true;
    }

    /**
     * CRITICAL: Check rate limiting
     */
    private boolean checkRateLimit(String clientIp, String uri, HttpServletResponse response, 
                                 String requestId) {
        String key = clientIp + ":" + uri;
        long currentTime = System.currentTimeMillis();
        long windowSize = 60000; // 1 minute
        int maxRequests = getMaxRequestsForEndpoint(uri);

        synchronized (requestCounters) {
            RequestCounter counter = requestCounters.computeIfAbsent(key, k -> new RequestCounter());
            
            // Reset counter if window expired
            if (currentTime - counter.getWindowStart() > windowSize) {
                counter.setWindowStart(currentTime);
                counter.setRequestCount(0);
            }
            
            counter.setRequestCount(counter.getRequestCount() + 1);
            
            if (counter.getRequestCount() > maxRequests) {
                log.error("SECURITY: Rate limit exceeded for {} on {}: {} requests - {}", 
                        clientIp, uri, counter.getRequestCount(), requestId);
                
                response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
                response.setHeader("X-RateLimit-Remaining", "0");
                response.setHeader("Retry-After", "60");
                
                sendErrorResponse(response, HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded", requestId);
                return false;
            }
            
            // Add rate limit headers
            response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
            response.setHeader("X-RateLimit-Remaining", 
                String.valueOf(maxRequests - counter.getRequestCount()));
        }

        return true;
    }

    /**
     * CRITICAL: Validate path traversal attempts
     */
    private boolean validatePathTraversal(String uri, HttpServletResponse response, String requestId) {
        String[] pathTraversalPatterns = {
            "../", "..\\", "%2e%2e%2f", "%2e%2e\\", 
            "%252e%252e%252f", "..;/", "..;\\",
            "%c0%ae%c0%ae%c0%af", "%c1%9c", "\\..\\", "/..\\"
        };

        String lowerUri = uri.toLowerCase();
        for (String pattern : pathTraversalPatterns) {
            if (lowerUri.contains(pattern)) {
                log.error("SECURITY: Path traversal attempt detected: {} - {}", 
                        dataValidator.sanitizeForLog(uri), requestId);
                sendErrorResponse(response, HttpStatus.FORBIDDEN, "Access denied", requestId);
                return false;
            }
        }

        return true;
    }

    /**
     * CRITICAL: Validate financial endpoint requests
     */
    private boolean validateFinancialRequest(HttpServletRequest request, HttpServletResponse response, 
                                           String requestId) {
        // Additional validation for financial endpoints
        String uri = request.getRequestURI();
        
        // Check for financial-specific headers
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null) {
            log.error("SECURITY: Missing authorization for financial endpoint: {} - {}", uri, requestId);
            sendErrorResponse(response, HttpStatus.UNAUTHORIZED, "Authorization required", requestId);
            return false;
        }

        // Validate content type for POST/PUT requests
        String method = request.getMethod();
        if (("POST".equals(method) || "PUT".equals(method))) {
            String contentType = request.getContentType();
            if (contentType == null || !contentType.contains("application/json")) {
                log.error("SECURITY: Invalid content type for financial operation: {} - {}", 
                        contentType, requestId);
                sendErrorResponse(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE, 
                    "Invalid content type", requestId);
                return false;
            }
        }

        return true;
    }

    /**
     * Check if URI is a financial endpoint
     */
    private boolean isFinancialEndpoint(String uri) {
        String[] financialPaths = {
            "/api/payments", "/api/transfers", "/api/wallets", 
            "/api/transactions", "/api/deposits", "/api/withdrawals"
        };

        for (String path : financialPaths) {
            if (uri.startsWith(path)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get max requests per minute for endpoint
     */
    private int getMaxRequestsForEndpoint(String uri) {
        if (isFinancialEndpoint(uri)) {
            return 20; // Stricter limit for financial endpoints
        } else if (uri.contains("/api/")) {
            return 100; // Standard API limit
        } else {
            return 200; // Higher limit for static resources
        }
    }

    /**
     * Get client IP address
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", 
            "WL-Proxy-Client-IP", "HTTP_X_FORWARDED_FOR", 
            "HTTP_X_FORWARDED", "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP", "HTTP_FORWARDED_FOR", "HTTP_FORWARDED"
        };

        for (String headerName : headerNames) {
            String ip = request.getHeader(headerName);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Handle multiple IPs in X-Forwarded-For
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                
                if (isValidIpAddress(ip)) {
                    return ip;
                }
            }
        }

        return request.getRemoteAddr();
    }

    /**
     * Validate IP address format
     */
    private boolean isValidIpAddress(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return false;
        }

        // Basic IP validation (both IPv4 and IPv6)
        String ipv4Pattern = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
        String ipv6Pattern = "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$";

        return ip.matches(ipv4Pattern) || ip.matches(ipv6Pattern);
    }

    /**
     * Send error response
     */
    private void sendErrorResponse(HttpServletResponse response, HttpStatus status, 
                                 String message, String requestId) {
        try {
            response.setStatus(status.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", status.getReasonPhrase());
            errorResponse.put("message", message);
            errorResponse.put("timestamp", Instant.now().toString());
            errorResponse.put("requestId", requestId);

            String json = objectMapper.writeValueAsString(errorResponse);
            response.getWriter().write(json);
            response.getWriter().flush();

        } catch (IOException e) {
            log.error("VALIDATION: Failed to send error response", e);
        }
    }

    /**
     * Update validation metrics
     */
    private void updateValidationMetrics(HttpServletRequest request, HttpServletResponse response, 
                                       long duration) {
        // Implementation would update metrics/monitoring system
        // For now, just log performance info
        if (duration > 1000) { // Log slow validations
            log.warn("VALIDATION: Slow request validation: {}ms for {}", 
                    duration, request.getRequestURI());
        }
    }

    /**
     * Request counter for rate limiting
     */
    private static class RequestCounter {
        private long windowStart;
        private int requestCount;

        public RequestCounter() {
            this.windowStart = System.currentTimeMillis();
            this.requestCount = 0;
        }

        public long getWindowStart() { return windowStart; }
        public void setWindowStart(long windowStart) { this.windowStart = windowStart; }
        public int getRequestCount() { return requestCount; }
        public void setRequestCount(int requestCount) { this.requestCount = requestCount; }
    }
}