package com.waqiti.common.security.filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.security.AdvancedInputValidationFramework;
import com.waqiti.common.security.AdvancedInputValidationFramework.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Comprehensive Validation Filter
 * Provides request-level validation and sanitization before processing
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(2) // Execute after security headers filter
public class ComprehensiveValidationFilter extends OncePerRequestFilter {

    private final AdvancedInputValidationFramework validationFramework;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${security.validation.filter.enabled:true}")
    private boolean filterEnabled;

    @Value("${security.validation.filter.strict-mode:true}")
    private boolean strictMode;

    @Value("${security.validation.filter.max-request-size:1048576}") // 1MB
    private long maxRequestSize;

    @Value("${security.validation.filter.rate-limit.requests-per-minute:60}")
    private int requestsPerMinute;

    @Value("${security.validation.filter.block-suspicious-requests:true}")
    private boolean blockSuspiciousRequests;

    // Cache for request validation patterns
    private final Map<String, RequestValidationRule> validationRules = new ConcurrentHashMap<>();

    // Suspicious request patterns
    private static final Pattern[] SUSPICIOUS_PATTERNS = {
        Pattern.compile("(?i).*(union|select|insert|update|delete|drop|create|alter).*"),
        Pattern.compile("(?i).*(script|javascript|vbscript|onload|onerror).*"),
        Pattern.compile("(?i).*(eval|exec|system|cmd|shell).*"),
        Pattern.compile(".*[<>\"'%;()&+\\-]{5,}.*"), // Multiple suspicious characters
        Pattern.compile(".*\\.\\.[\\\\/].*"), // Path traversal
        Pattern.compile(".*(?:file|ftp|gopher|ldap|dict)://.*"), // Suspicious protocols
    };

    // File upload validation patterns
    private static final Set<String> ALLOWED_FILE_EXTENSIONS = Set.of(
        "jpg", "jpeg", "png", "gif", "pdf", "doc", "docx", "xls", "xlsx", "txt", "csv"
    );

    private static final Set<String> DANGEROUS_FILE_EXTENSIONS = Set.of(
        "exe", "bat", "cmd", "scr", "pif", "js", "vbs", "jar", "sh", "php", "asp", "jsp"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        if (!filterEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        // Wrap request and response for content inspection
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        try {
            // Pre-validation checks
            ValidationResult preValidation = performPreValidation(wrappedRequest);
            if (!preValidation.isValid()) {
                handleValidationFailure(wrappedRequest, wrappedResponse, preValidation);
                return;
            }

            // Request size validation
            if (wrappedRequest.getContentLength() > maxRequestSize) {
                handleValidationFailure(wrappedRequest, wrappedResponse, 
                    ValidationResult.failure("Request size exceeds maximum limit", ThreatLevel.MEDIUM));
                return;
            }

            // Rate limiting
            if (!checkRateLimit(wrappedRequest)) {
                handleValidationFailure(wrappedRequest, wrappedResponse, 
                    ValidationResult.failure("Rate limit exceeded", ThreatLevel.HIGH));
                return;
            }

            // Content validation for POST/PUT requests
            if (isContentValidationRequired(wrappedRequest)) {
                ValidationResult contentValidation = validateRequestContent(wrappedRequest);
                if (!contentValidation.isValid()) {
                    handleValidationFailure(wrappedRequest, wrappedResponse, contentValidation);
                    return;
                }
            }

            // Parameter validation
            ValidationResult paramValidation = validateRequestParameters(wrappedRequest);
            if (!paramValidation.isValid()) {
                handleValidationFailure(wrappedRequest, wrappedResponse, paramValidation);
                return;
            }

            // Header validation
            ValidationResult headerValidation = validateRequestHeaders(wrappedRequest);
            if (!headerValidation.isValid()) {
                handleValidationFailure(wrappedRequest, wrappedResponse, headerValidation);
                return;
            }

            // File upload validation
            if (isFileUploadRequest(wrappedRequest)) {
                ValidationResult fileValidation = validateFileUpload(wrappedRequest);
                if (!fileValidation.isValid()) {
                    handleValidationFailure(wrappedRequest, wrappedResponse, fileValidation);
                    return;
                }
            }

            // Record successful validation
            recordValidationSuccess(wrappedRequest);

            // Proceed with request processing
            filterChain.doFilter(wrappedRequest, wrappedResponse);

        } catch (Exception e) {
            log.error("Error in validation filter", e);
            handleValidationFailure(wrappedRequest, wrappedResponse, 
                ValidationResult.failure("Internal validation error", ThreatLevel.HIGH));
        } finally {
            wrappedResponse.copyBodyToResponse();
        }
    }

    /**
     * Perform initial validation checks
     */
    private ValidationResult performPreValidation(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        String method = request.getMethod();
        String userAgent = request.getHeader("User-Agent");

        // Check for suspicious URI patterns
        for (Pattern pattern : SUSPICIOUS_PATTERNS) {
            if (pattern.matcher(requestURI).matches()) {
                log.warn("Suspicious URI pattern detected: {}", requestURI);
                return ValidationResult.failure("Suspicious request pattern", ThreatLevel.HIGH);
            }
        }

        // Check for missing User-Agent (often indicates bot/automation)
        if (strictMode && (userAgent == null || userAgent.trim().isEmpty())) {
            return ValidationResult.failure("Missing User-Agent header", ThreatLevel.MEDIUM);
        }

        // Check for excessively long User-Agent
        if (userAgent != null && userAgent.length() > 500) {
            return ValidationResult.failure("User-Agent header too long", ThreatLevel.MEDIUM);
        }

        // Check for null bytes in URI
        if (requestURI.contains("\0")) {
            return ValidationResult.failure("Null byte in URI", ThreatLevel.HIGH);
        }

        return ValidationResult.success("Pre-validation passed");
    }

    /**
     * Validate request content (JSON, form data, etc.)
     */
    private ValidationResult validateRequestContent(ContentCachingRequestWrapper request) {
        try {
            byte[] content = request.getContentAsByteArray();
            if (content.length == 0) {
                return ValidationResult.success("No content to validate");
            }

            String contentType = request.getContentType();
            if (contentType == null) {
                return ValidationResult.failure("Missing Content-Type header", ThreatLevel.MEDIUM);
            }

            String body = new String(content, StandardCharsets.UTF_8);

            // Check for null bytes in content
            if (body.contains("\0")) {
                return ValidationResult.failure("Null byte in request body", ThreatLevel.HIGH);
            }

            // Validate based on content type
            if (contentType.contains("application/json")) {
                return validateJsonContent(body);
            } else if (contentType.contains("application/x-www-form-urlencoded")) {
                return validateFormContent(body);
            } else if (contentType.contains("multipart/form-data")) {
                return validateMultipartContent(request);
            }

            // For other content types, perform basic validation
            return validateGenericContent(body);

        } catch (Exception e) {
            log.error("Error validating request content", e);
            return ValidationResult.failure("Content validation error", ThreatLevel.HIGH);
        }
    }

    /**
     * Validate JSON content
     */
    private ValidationResult validateJsonContent(String jsonBody) {
        try {
            // Parse JSON to ensure it's well-formed
            objectMapper.readTree(jsonBody);

            // Check for suspicious patterns in JSON
            for (Pattern pattern : SUSPICIOUS_PATTERNS) {
                if (pattern.matcher(jsonBody).find()) {
                    return ValidationResult.failure("Suspicious pattern in JSON", ThreatLevel.HIGH);
                }
            }

            // Check JSON depth to prevent deep nesting attacks
            if (getJsonDepth(jsonBody) > 10) {
                return ValidationResult.failure("JSON nesting too deep", ThreatLevel.MEDIUM);
            }

            return ValidationResult.success("JSON validation passed");

        } catch (Exception e) {
            return ValidationResult.failure("Invalid JSON format", ThreatLevel.MEDIUM);
        }
    }

    /**
     * Validate form-encoded content
     */
    private ValidationResult validateFormContent(String formBody) {
        try {
            String[] pairs = formBody.split("&");
            
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    String key = java.net.URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                    String value = java.net.URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);

                    // Validate key and value
                    ValidationContext context = ValidationContext.builder()
                        .fieldName(key)
                        .validationType(ValidationType.SAFE_TEXT)
                        .sqlInjectionCheck(true)
                        .xssCheck(true)
                        .build();

                    ValidationResult result = validationFramework.validateInput(value, context);
                    if (!result.isValid()) {
                        return result;
                    }
                }
            }

            return ValidationResult.success("Form validation passed");

        } catch (Exception e) {
            return ValidationResult.failure("Form content validation error", ThreatLevel.MEDIUM);
        }
    }

    /**
     * Validate multipart content
     */
    private ValidationResult validateMultipartContent(HttpServletRequest request) {
        // Basic multipart validation - detailed validation would require parsing multipart data
        String contentType = request.getContentType();
        
        if (!contentType.contains("boundary=")) {
            return ValidationResult.failure("Invalid multipart format", ThreatLevel.MEDIUM);
        }

        return ValidationResult.success("Multipart validation passed");
    }

    /**
     * Validate generic content
     */
    private ValidationResult validateGenericContent(String content) {
        // Check for suspicious patterns
        for (Pattern pattern : SUSPICIOUS_PATTERNS) {
            if (pattern.matcher(content).find()) {
                return ValidationResult.failure("Suspicious content pattern", ThreatLevel.HIGH);
            }
        }

        return ValidationResult.success("Generic content validation passed");
    }

    /**
     * Validate request parameters
     */
    private ValidationResult validateRequestParameters(HttpServletRequest request) {
        Map<String, String[]> parameters = request.getParameterMap();

        for (Map.Entry<String, String[]> entry : parameters.entrySet()) {
            String paramName = entry.getKey();
            String[] paramValues = entry.getValue();

            // Validate parameter name
            if (!isValidParameterName(paramName)) {
                return ValidationResult.failure("Invalid parameter name: " + paramName, ThreatLevel.MEDIUM);
            }

            // Validate parameter values
            for (String value : paramValues) {
                if (value != null) {
                    ValidationContext context = ValidationContext.builder()
                        .fieldName(paramName)
                        .validationType(ValidationType.SAFE_TEXT)
                        .sqlInjectionCheck(true)
                        .xssCheck(true)
                        .maxLength(1000) // Reasonable limit for URL parameters
                        .build();

                    ValidationResult result = validationFramework.validateInput(value, context);
                    if (!result.isValid()) {
                        return result;
                    }
                }
            }
        }

        return ValidationResult.success("Parameter validation passed");
    }

    /**
     * Validate request headers
     */
    private ValidationResult validateRequestHeaders(HttpServletRequest request) {
        Enumeration<String> headerNames = request.getHeaderNames();

        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);

            // Skip validation for standard headers
            if (isStandardHeader(headerName)) {
                continue;
            }

            // Validate custom headers
            if (headerValue != null && headerValue.length() > 4096) {
                return ValidationResult.failure("Header value too long: " + headerName, ThreatLevel.MEDIUM);
            }

            // Check for suspicious patterns in custom headers
            if (headerValue != null) {
                for (Pattern pattern : SUSPICIOUS_PATTERNS) {
                    if (pattern.matcher(headerValue).find()) {
                        return ValidationResult.failure("Suspicious header value: " + headerName, ThreatLevel.HIGH);
                    }
                }
            }
        }

        return ValidationResult.success("Header validation passed");
    }

    /**
     * Validate file uploads
     */
    private ValidationResult validateFileUpload(HttpServletRequest request) {
        String contentType = request.getContentType();
        
        if (contentType != null && contentType.contains("multipart/form-data")) {
            // Extract filename from content-disposition if available
            String contentDisposition = request.getHeader("Content-Disposition");
            if (contentDisposition != null) {
                String filename = extractFilename(contentDisposition);
                if (filename != null) {
                    return validateFilename(filename);
                }
            }
        }

        return ValidationResult.success("File upload validation passed");
    }

    /**
     * Validate filename for security
     */
    private ValidationResult validateFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return ValidationResult.failure("Empty filename", ThreatLevel.MEDIUM);
        }

        // Check for path traversal
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return ValidationResult.failure("Path traversal in filename", ThreatLevel.HIGH);
        }

        // Check file extension
        String extension = getFileExtension(filename);
        if (extension != null) {
            if (DANGEROUS_FILE_EXTENSIONS.contains(extension.toLowerCase())) {
                return ValidationResult.failure("Dangerous file extension: " + extension, ThreatLevel.HIGH);
            }
            
            if (strictMode && !ALLOWED_FILE_EXTENSIONS.contains(extension.toLowerCase())) {
                return ValidationResult.failure("File extension not allowed: " + extension, ThreatLevel.MEDIUM);
            }
        }

        return ValidationResult.success("Filename validation passed");
    }

    /**
     * Check rate limiting
     */
    private boolean checkRateLimit(HttpServletRequest request) {
        if (!blockSuspiciousRequests) {
            return true;
        }

        String clientId = getClientIdentifier(request);
        String rateLimitKey = "validation:rate_limit:" + clientId;
        
        try {
            Long currentCount = redisTemplate.opsForValue().increment(rateLimitKey);
            if (currentCount == 1) {
                redisTemplate.expire(rateLimitKey, Duration.ofMinutes(1));
            }
            
            return currentCount <= requestsPerMinute;
            
        } catch (Exception e) {
            log.warn("Error checking rate limit", e);
            return true; // Allow request if rate limiting fails
        }
    }

    /**
     * Handle validation failure
     */
    private void handleValidationFailure(HttpServletRequest request, HttpServletResponse response, 
                                       ValidationResult result) throws IOException {
        
        // Log the incident
        log.warn("Request validation failed - URI: {}, Method: {}, Client: {}, Threat: {}, Message: {}", 
            request.getRequestURI(), request.getMethod(), getClientIdentifier(request), 
            result.getThreatLevel(), result.getMessage());

        // Record security incident
        recordSecurityIncident(request, result);

        // Set response
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> errorResponse = Map.of(
            "error", "Request validation failed",
            "message", result.getMessage(),
            "timestamp", Instant.now().toString()
        );

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    /**
     * Record validation success metrics
     */
    private void recordValidationSuccess(HttpServletRequest request) {
        try {
            String metricKey = "validation:success:" + request.getRequestURI() + ":" + 
                Instant.now().toEpochMilli();
            
            Map<String, Object> metric = Map.of(
                "uri", request.getRequestURI(),
                "method", request.getMethod(),
                "client", getClientIdentifier(request),
                "timestamp", Instant.now().toString()
            );
            
            redisTemplate.opsForValue().set(metricKey, objectMapper.writeValueAsString(metric), 
                Duration.ofHours(24));
                
        } catch (Exception e) {
            log.debug("Error recording validation success metric", e);
        }
    }

    /**
     * Record security incident
     */
    private void recordSecurityIncident(HttpServletRequest request, ValidationResult result) {
        try {
            String incidentKey = "security:incident:" + getClientIdentifier(request) + ":" + 
                Instant.now().toEpochMilli();
            
            Map<String, Object> incident = Map.of(
                "uri", request.getRequestURI(),
                "method", request.getMethod(),
                "client", getClientIdentifier(request),
                "userAgent", request.getHeader("User-Agent"),
                "threatLevel", result.getThreatLevel().name(),
                "message", result.getMessage(),
                "timestamp", Instant.now().toString()
            );
            
            redisTemplate.opsForValue().set(incidentKey, objectMapper.writeValueAsString(incident), 
                Duration.ofDays(30));
                
        } catch (Exception e) {
            log.warn("Error recording security incident", e);
        }
    }

    // Helper methods

    private boolean isContentValidationRequired(HttpServletRequest request) {
        String method = request.getMethod();
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    private boolean isFileUploadRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.contains("multipart/form-data");
    }

    private boolean isValidParameterName(String paramName) {
        return paramName.matches("^[a-zA-Z0-9_.-]+$") && paramName.length() <= 100;
    }

    private boolean isStandardHeader(String headerName) {
        Set<String> standardHeaders = Set.of(
            "Accept", "Accept-Encoding", "Accept-Language", "Authorization", "Cache-Control",
            "Connection", "Content-Length", "Content-Type", "Cookie", "Host", "Origin",
            "Referer", "User-Agent", "X-Requested-With"
        );
        return standardHeaders.contains(headerName);
    }

    private String getClientIdentifier(HttpServletRequest request) {
        // Try various methods to identify the client
        String clientId = request.getHeader("X-Client-ID");
        if (clientId != null) return clientId;
        
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null) return "api_" + apiKey.substring(0, Math.min(8, apiKey.length()));
        
        return request.getRemoteAddr();
    }

    private int getJsonDepth(String json) {
        int maxDepth = 0;
        int currentDepth = 0;
        
        for (char c : json.toCharArray()) {
            if (c == '{' || c == '[') {
                currentDepth++;
                maxDepth = Math.max(maxDepth, currentDepth);
            } else if (c == '}' || c == ']') {
                currentDepth--;
            }
        }
        
        return maxDepth;
    }

    private String extractFilename(String contentDisposition) {
        if (contentDisposition.contains("filename=")) {
            String[] parts = contentDisposition.split("filename=");
            if (parts.length > 1) {
                return parts[1].replaceAll("\"", "").trim();
            }
        }
        return null;
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1);
        }
        return null;
    }

    @Data
    public static class RequestValidationRule {
        private String path;
        private String method;
        private Map<String, ValidationType> parameterValidation;
        private boolean strictMode;
    }
}