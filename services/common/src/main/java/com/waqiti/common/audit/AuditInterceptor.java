package com.waqiti.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.annotation.Audited;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * HTTP interceptor for automatic audit logging of API calls
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditInterceptor implements HandlerInterceptor {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    // Thread-local storage for request timing
    private static final ThreadLocal<Long> requestStartTime = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Object>> requestContext = new ThreadLocal<>();
    
    // Cache for method audit configurations
    private final Map<Method, AuditConfig> auditConfigCache = new ConcurrentHashMap<>();
    
    // Sensitive parameter names that should be masked
    private static final Set<String> SENSITIVE_PARAMS = Set.of(
        "password", "pin", "cvv", "ssn", "creditcard", "cardnumber",
        "accountnumber", "routingnumber", "secret", "token", "apikey"
    );
    
    // Paths to exclude from audit logging
    private static final Set<String> EXCLUDED_PATHS = Set.of(
        "/health", "/actuator", "/swagger", "/v3/api-docs", "/favicon.ico"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, 
                           Object handler) throws Exception {
        // Skip non-handler methods and excluded paths
        if (!(handler instanceof HandlerMethod) || isExcludedPath(request.getRequestURI())) {
            return true;
        }
        
        // Record request start time
        requestStartTime.set(System.currentTimeMillis());
        
        // Initialize request context
        Map<String, Object> context = new HashMap<>();
        context.put("requestId", UUID.randomUUID().toString());
        context.put("method", request.getMethod());
        context.put("path", request.getRequestURI());
        context.put("queryString", request.getQueryString());
        requestContext.set(context);
        
        // Check if method requires pre-logging
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        AuditConfig config = getAuditConfig(handlerMethod.getMethod());
        
        if (config != null && config.isLogRequest()) {
            logRequestDetails(request, handlerMethod, config);
        }
        
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                          Object handler, ModelAndView modelAndView) throws Exception {
        // Post-processing if needed
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                               Object handler, Exception ex) throws Exception {
        try {
            // Skip non-handler methods
            if (!(handler instanceof HandlerMethod)) {
                return;
            }
            
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            AuditConfig config = getAuditConfig(handlerMethod.getMethod());
            
            // Calculate request duration
            Long startTime = requestStartTime.get();
            long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;
            
            // Get request context
            Map<String, Object> context = requestContext.get();
            if (context == null) {
                context = new HashMap<>();
            }
            
            // Add response information
            context.put("responseStatus", response.getStatus());
            context.put("duration", duration);
            
            if (ex != null) {
                context.put("error", ex.getClass().getSimpleName());
                context.put("errorMessage", ex.getMessage());
            }
            
            // Determine if this needs audit logging
            if (shouldAuditLog(request, response, config)) {
                // Add method-specific audit information
                if (config != null) {
                    enrichContextWithAuditConfig(context, config, request);
                }
                
                // Log the API call
                auditService.logApiCall(
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    duration,
                    context
                );
                
                // Log additional audit events based on annotations
                if (config != null && config.isLogDataAccess()) {
                    logDataAccessIfApplicable(handlerMethod, request, response);
                }
            }
            
        } finally {
            // Clean up thread locals
            requestStartTime.remove();
            requestContext.remove();
        }
    }

    private AuditConfig getAuditConfig(Method method) {
        return auditConfigCache.computeIfAbsent(method, m -> {
            // Check for @Audited annotation
            Audited audited = m.getAnnotation(Audited.class);
            if (audited != null) {
                return AuditConfig.builder()
                    .eventType(audited.eventType())
                    .eventCategory(audited.eventCategory())
                    .resourceType(audited.resourceType())
                    .action(audited.action())
                    .logRequest(audited.logRequest())
                    .logResponse(audited.logResponse())
                    .logDataAccess(audited.logDataAccess())
                    .maskSensitiveData(audited.maskSensitiveData())
                    .includePayload(audited.includePayload())
                    .build();
            }
            
            // Check for controller-level annotation
            Audited classAudited = m.getDeclaringClass().getAnnotation(Audited.class);
            if (classAudited != null) {
                return AuditConfig.builder()
                    .eventType(classAudited.eventType())
                    .eventCategory(classAudited.eventCategory())
                    .resourceType(classAudited.resourceType())
                    .action(classAudited.action())
                    .logRequest(classAudited.logRequest())
                    .logResponse(classAudited.logResponse())
                    .logDataAccess(classAudited.logDataAccess())
                    .maskSensitiveData(classAudited.maskSensitiveData())
                    .includePayload(classAudited.includePayload())
                    .build();
            }
            
            return null;
        });
    }

    private boolean shouldAuditLog(HttpServletRequest request, HttpServletResponse response, 
                                  AuditConfig config) {
        // Always audit authentication endpoints
        if (request.getRequestURI().contains("/auth/") || 
            request.getRequestURI().contains("/login")) {
            return true;
        }
        
        // Always audit failed requests
        if (response.getStatus() >= 400) {
            return true;
        }
        
        // Audit based on configuration
        if (config != null) {
            return true;
        }
        
        // Audit state-changing operations
        return !request.getMethod().equals("GET") && !request.getMethod().equals("HEAD");
    }

    private void logRequestDetails(HttpServletRequest request, HandlerMethod handlerMethod, 
                                  AuditConfig config) {
        try {
            Map<String, Object> requestDetails = new HashMap<>();
            
            // Add request parameters (masked if sensitive)
            Map<String, String> parameters = extractParameters(request, config.isMaskSensitiveData());
            if (!parameters.isEmpty()) {
                requestDetails.put("parameters", parameters);
            }
            
            // Add request headers (excluding sensitive ones)
            Map<String, String> headers = extractHeaders(request);
            requestDetails.put("headers", headers);
            
            // Add request body if configured
            if (config.isIncludePayload() && request.getContentLength() > 0) {
                String body = extractRequestBody(request);
                if (body != null && !body.isEmpty()) {
                    requestDetails.put("requestBody", 
                        config.isMaskSensitiveData() ? maskSensitiveData(body) : body);
                }
            }
            
            Map<String, Object> context = requestContext.get();
            context.put("requestDetails", requestDetails);
            
        } catch (Exception e) {
            log.warn("Failed to log request details", e);
        }
    }

    private void logDataAccessIfApplicable(HandlerMethod handlerMethod, 
                                         HttpServletRequest request, 
                                         HttpServletResponse response) {
        try {
            // Extract resource information from path variables
            Map<String, String> pathVariables = extractPathVariables(request);
            String resourceId = pathVariables.get("id");
            if (resourceId == null) {
                resourceId = pathVariables.values().stream().findFirst().orElse(null);
            }
            
            if (resourceId != null) {
                Method method = handlerMethod.getMethod();
                Audited audited = method.getAnnotation(Audited.class);
                
                String resourceType = audited != null && !audited.resourceType().isEmpty() 
                    ? audited.resourceType() 
                    : inferResourceType(request.getRequestURI());
                
                String action = request.getMethod().equals("GET") ? "SELECT" : 
                               request.getMethod().equals("POST") ? "INSERT" :
                               request.getMethod().equals("PUT") ? "UPDATE" :
                               request.getMethod().equals("DELETE") ? "DELETE" : "UNKNOWN";
                
                // Create a proper DataAccessAuditLog
                DataAccessAuditLog dataLog = DataAccessAuditLog.builder()
                    .userId(extractUserId(request))
                    .tableName(resourceType)
                    .operation(action)
                    .recordId(resourceId)
                    .ipAddress(request.getRemoteAddr())
                    .sessionId(request.getSession(false) != null ? request.getSession(false).getId() : null)
                    .build();
                
                auditService.logDataAccess(dataLog);
            }
        } catch (Exception e) {
            log.warn("Failed to log data access audit", e);
        }
    }

    private Map<String, String> extractParameters(HttpServletRequest request, boolean maskSensitive) {
        Map<String, String> parameters = new HashMap<>();
        
        Enumeration<String> paramNames = request.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            String paramValue = request.getParameter(paramName);
            
            if (maskSensitive && isSensitiveParam(paramName)) {
                parameters.put(paramName, "***MASKED***");
            } else {
                parameters.put(paramName, paramValue);
            }
        }
        
        return parameters;
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            
            // Skip sensitive headers
            if (!isSensitiveHeader(headerName)) {
                headers.put(headerName, request.getHeader(headerName));
            }
        }
        
        return headers;
    }

    private String extractRequestBody(HttpServletRequest request) throws IOException {
        // Note: This requires a wrapper to read request body multiple times
        // Check if this is a readable request (not multipart/form-data)
        String contentType = request.getContentType();
        if (contentType != null && (contentType.contains("multipart/") || contentType.contains("form-data"))) {
            return "[Binary content - not logged]";
        }
        
        // Check request method - only POST, PUT, PATCH typically have bodies
        String method = request.getMethod();
        if (!"POST".equals(method) && !"PUT".equals(method) && !"PATCH".equals(method)) {
            return null;
        }
        
        try {
            // Try to get cached request body from attribute (set by request wrapper)
            Object cachedBody = request.getAttribute("cachedRequestBody");
            if (cachedBody != null) {
                return sanitizeRequestBody(cachedBody.toString());
            }
            
            // Try to read from input stream if available
            // Note: This will only work if a ContentCachingRequestWrapper is used
            if (request.getInputStream() != null && request.getContentLength() > 0) {
                // Limit body size to prevent memory issues
                int maxBodySize = 10000; // 10KB limit for audit logging
                if (request.getContentLength() > maxBodySize) {
                    return String.format("[Large request body - %d bytes, truncated]", request.getContentLength());
                }
                
                // Read the request body
                byte[] buffer = new byte[request.getContentLength()];
                int bytesRead = request.getInputStream().read(buffer);
                if (bytesRead > 0) {
                    String body = new String(buffer, 0, bytesRead, "UTF-8");
                    return sanitizeRequestBody(body);
                }
            }
            
            // If using Spring's ContentCachingRequestWrapper
            if (request.getClass().getName().contains("ContentCachingRequestWrapper")) {
                // Use reflection to avoid direct dependency
                try {
                    java.lang.reflect.Method getContentAsByteArray = request.getClass().getMethod("getContentAsByteArray");
                    byte[] content = (byte[]) getContentAsByteArray.invoke(request);
                    if (content != null && content.length > 0) {
                        return sanitizeRequestBody(new String(content, "UTF-8"));
                    }
                } catch (Exception e) {
                    log.debug("Failed to extract cached request body: {}", e.getMessage());
                }
            }
            
            return null;
        } catch (Exception e) {
            log.debug("Error extracting request body for audit: {}", e.getMessage());
            return "[Error reading request body]";
        }
    }
    
    private String sanitizeRequestBody(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        
        try {
            // Parse as JSON and mask sensitive fields
            Map<String, Object> jsonBody = objectMapper.readValue(body, Map.class);
            maskSensitiveData(jsonBody);
            return objectMapper.writeValueAsString(jsonBody);
        } catch (Exception e) {
            // If not JSON, return first 1000 chars
            if (body.length() > 1000) {
                return body.substring(0, 1000) + "...[truncated]";
            }
            return body;
        }
    }
    
    private void maskSensitiveData(Map<String, Object> data) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey().toLowerCase();
            if (SENSITIVE_PARAMS.stream().anyMatch(key::contains)) {
                data.put(entry.getKey(), "[REDACTED]");
            } else if (entry.getValue() instanceof Map) {
                maskSensitiveData((Map<String, Object>) entry.getValue());
            } else if (entry.getValue() instanceof List) {
                List<?> list = (List<?>) entry.getValue();
                for (Object item : list) {
                    if (item instanceof Map) {
                        maskSensitiveData((Map<String, Object>) item);
                    }
                }
            }
        }
    }

    private Map<String, String> extractPathVariables(HttpServletRequest request) {
        // Extract path variables from request attributes
        // This would be populated by Spring MVC
        Map<String, String> pathVars = new HashMap<>();
        
        // Implementation depends on Spring MVC configuration
        
        return pathVars;
    }

    private boolean isExcludedPath(String path) {
        return EXCLUDED_PATHS.stream().anyMatch(path::contains);
    }

    private boolean isSensitiveParam(String paramName) {
        String lowerParam = paramName.toLowerCase();
        return SENSITIVE_PARAMS.stream().anyMatch(lowerParam::contains);
    }

    private boolean isSensitiveHeader(String headerName) {
        String lowerHeader = headerName.toLowerCase();
        return lowerHeader.contains("authorization") || 
               lowerHeader.contains("cookie") || 
               lowerHeader.contains("token") ||
               lowerHeader.contains("api-key");
    }

    private String extractUserId(HttpServletRequest request) {
        // Try to extract user ID from various sources
        
        // Check JWT token
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // Extract user ID from JWT token - simplified implementation
            return "user-from-jwt";
        }
        
        // Check session
        if (request.getSession(false) != null) {
            Object userId = request.getSession().getAttribute("userId");
            if (userId != null) {
                return userId.toString();
            }
        }
        
        // Check request attribute (may be set by authentication filter)
        Object userIdAttr = request.getAttribute("userId");
        if (userIdAttr != null) {
            return userIdAttr.toString();
        }
        
        // Default
        return "anonymous";
    }
    
    private String maskSensitiveData(String data) {
        // Simple implementation - in production, use more sophisticated masking
        return data.replaceAll("\"password\"\\s*:\\s*\"[^\"]+\"", "\"password\":\"***MASKED***\"")
                   .replaceAll("\"pin\"\\s*:\\s*\"[^\"]+\"", "\"pin\":\"***MASKED***\"")
                   .replaceAll("\"cvv\"\\s*:\\s*\"[^\"]+\"", "\"cvv\":\"***MASKED***\"")
                   .replaceAll("\"cardNumber\"\\s*:\\s*\"[^\"]+\"", "\"cardNumber\":\"***MASKED***\"");
    }

    private String inferResourceType(String uri) {
        // Simple inference based on URI patterns
        if (uri.contains("/users/")) return "USER";
        if (uri.contains("/accounts/")) return "ACCOUNT";
        if (uri.contains("/transactions/")) return "TRANSACTION";
        if (uri.contains("/payments/")) return "PAYMENT";
        if (uri.contains("/cards/")) return "CARD";
        return "UNKNOWN";
    }

    private boolean isPiiResource(String resourceType) {
        return Set.of("USER", "ACCOUNT", "CARD", "KYC_DOCUMENT").contains(resourceType);
    }
    
    private void enrichContextWithAuditConfig(Map<String, Object> context, AuditConfig config, HttpServletRequest request) {
        if (config.getEventType() != null) {
            context.put("eventType", config.getEventType());
        }
        if (config.getEventCategory() != null) {
            context.put("eventCategory", config.getEventCategory());
        }
        if (config.getResourceType() != null) {
            context.put("resourceType", config.getResourceType());
        }
        if (config.getAction() != null) {
            context.put("action", config.getAction());
        }
        context.put("maskSensitiveData", config.isMaskSensitiveData());
        context.put("includePayload", config.isIncludePayload());
    }

    @lombok.Builder
    @lombok.Data
    private static class AuditConfig {
        private String eventType;
        private String eventCategory;
        private String resourceType;
        private String action;
        private boolean logRequest;
        private boolean logResponse;
        private boolean logDataAccess;
        private boolean maskSensitiveData;
        private boolean includePayload;
    }
}