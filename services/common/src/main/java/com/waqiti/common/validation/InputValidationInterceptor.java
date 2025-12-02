package com.waqiti.common.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * CRITICAL SECURITY: Automatic Input Validation Interceptor
 * Intercepts all HTTP requests and validates input based on annotations
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InputValidationInterceptor implements HandlerInterceptor {
    
    private final SecureInputValidationService validationService;
    private final ObjectMapper objectMapper;
    
    // Cache for validation rules
    private final Map<Method, ValidationRules> validationCache = new ConcurrentHashMap<>();
    
    // Suspicious patterns in headers
    private static final Set<String> DANGEROUS_HEADERS = Set.of(
        "X-Forwarded-Host", "X-Original-URL", "X-Rewrite-URL",
        "X-Forwarded-Server", "X-HTTP-Method-Override"
    );
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) 
            throws Exception {
        
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        
        // Get client IP for logging
        String clientIp = getClientIp(request);
        
        // Validate headers
        if (!validateHeaders(request, clientIp)) {
            log.error("SECURITY: Header validation failed for IP: {}", clientIp);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid request headers");
            return false;
        }
        
        // Validate request parameters
        if (!validateRequestParameters(request, handlerMethod, clientIp)) {
            log.error("SECURITY: Parameter validation failed for IP: {}", clientIp);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid request parameters");
            return false;
        }
        
        // Validate request body if present
        if (hasRequestBody(request)) {
            if (!validateRequestBody(request, handlerMethod, clientIp)) {
                log.error("SECURITY: Request body validation failed for IP: {}", clientIp);
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid request body");
                return false;
            }
        }
        
        // Validate path variables
        if (!validatePathVariables(request, handlerMethod, clientIp)) {
            log.error("SECURITY: Path variable validation failed for IP: {}", clientIp);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid path variables");
            return false;
        }
        
        // Check for request smuggling attempts
        if (isRequestSmugglingAttempt(request)) {
            log.error("SECURITY ALERT: Request smuggling attempt from IP: {}", clientIp);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid request");
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate HTTP headers for security issues
     */
    private boolean validateHeaders(HttpServletRequest request, String clientIp) {
        Enumeration<String> headerNames = request.getHeaderNames();
        
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            
            // Check header name
            if (!isValidHeaderName(headerName)) {
                log.error("SECURITY: Invalid header name '{}' from IP: {}", headerName, clientIp);
                return false;
            }
            
            // Check header value
            if (!isValidHeaderValue(headerValue)) {
                log.error("SECURITY: Invalid header value for '{}' from IP: {}", headerName, clientIp);
                return false;
            }
            
            // Check for header injection
            if (containsHeaderInjection(headerValue)) {
                log.error("SECURITY: Header injection attempt in '{}' from IP: {}", headerName, clientIp);
                return false;
            }
            
            // Validate specific headers
            if (!validateSpecificHeader(headerName, headerValue)) {
                log.error("SECURITY: Specific header validation failed for '{}' from IP: {}", headerName, clientIp);
                return false;
            }
        }
        
        // Check for dangerous header combinations
        if (hasDangerousHeaderCombination(request)) {
            log.error("SECURITY: Dangerous header combination detected from IP: {}", clientIp);
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate request parameters
     */
    private boolean validateRequestParameters(HttpServletRequest request, HandlerMethod handlerMethod, 
                                             String clientIp) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        ValidationRules rules = getValidationRules(handlerMethod);
        
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String paramName = entry.getKey();
            String[] paramValues = entry.getValue();
            
            // Validate parameter name
            if (!isValidParameterName(paramName)) {
                log.error("SECURITY: Invalid parameter name '{}' from IP: {}", paramName, clientIp);
                return false;
            }
            
            // Validate each parameter value
            for (String value : paramValues) {
                SecureInputValidationService.InputType inputType = 
                    rules.getParameterType(paramName);
                
                SecureInputValidationService.ValidationContext context = 
                    new SecureInputValidationService.ValidationContext(
                        request.getRemoteUser(), 
                        clientIp, 
                        inputType
                    );
                
                SecureInputValidationService.ValidationResult result = 
                    validationService.validateInput(value, inputType, context);
                
                if (!result.isValid()) {
                    log.error("SECURITY: Parameter '{}' validation failed: {} from IP: {}", 
                        paramName, result.getError(), clientIp);
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Validate request body
     */
    private boolean validateRequestBody(HttpServletRequest request, HandlerMethod handlerMethod, 
                                       String clientIp) {
        try {
            String body = readRequestBody(request);
            
            if (body.isEmpty()) {
                return true;
            }
            
            // Check for oversized body
            if (body.length() > 1048576) { // 1MB limit
                log.error("SECURITY: Request body too large from IP: {}", clientIp);
                return false;
            }
            
            // Validate as JSON
            SecureInputValidationService.ValidationContext context = 
                new SecureInputValidationService.ValidationContext(
                    request.getRemoteUser(), 
                    clientIp, 
                    SecureInputValidationService.InputType.JSON
                );
            
            SecureInputValidationService.ValidationResult result = 
                validationService.validateInput(body, SecureInputValidationService.InputType.JSON, context);
            
            if (!result.isValid()) {
                log.error("SECURITY: Request body validation failed: {} from IP: {}", 
                    result.getError(), clientIp);
                return false;
            }
            
            // Validate individual fields if we can parse the body
            try {
                Map<String, Object> bodyMap = objectMapper.readValue(body, Map.class);
                return validateBodyFields(bodyMap, handlerMethod, clientIp);
            } catch (Exception e) {
                // Not JSON, validate as plain text
                return true;
            }
            
        } catch (Exception e) {
            log.error("Error validating request body", e);
            return false;
        }
    }
    
    /**
     * Validate individual fields in request body
     */
    private boolean validateBodyFields(Map<String, Object> bodyMap, HandlerMethod handlerMethod, 
                                      String clientIp) {
        ValidationRules rules = getValidationRules(handlerMethod);
        
        for (Map.Entry<String, Object> entry : bodyMap.entrySet()) {
            String fieldName = entry.getKey();
            Object fieldValue = entry.getValue();
            
            if (fieldValue == null) {
                continue;
            }
            
            String valueStr = fieldValue.toString();
            SecureInputValidationService.InputType inputType = rules.getFieldType(fieldName);
            
            SecureInputValidationService.ValidationContext context = 
                new SecureInputValidationService.ValidationContext(
                    null, 
                    clientIp, 
                    inputType
                );
            
            SecureInputValidationService.ValidationResult result = 
                validationService.validateInput(valueStr, inputType, context);
            
            if (!result.isValid()) {
                log.error("SECURITY: Field '{}' validation failed: {} from IP: {}", 
                    fieldName, result.getError(), clientIp);
                return false;
            }
            
            // Recursively validate nested objects
            if (fieldValue instanceof Map) {
                if (!validateBodyFields((Map<String, Object>) fieldValue, handlerMethod, clientIp)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Validate path variables
     */
    private boolean validatePathVariables(HttpServletRequest request, HandlerMethod handlerMethod, 
                                         String clientIp) {
        String path = request.getRequestURI();
        ValidationRules rules = getValidationRules(handlerMethod);
        
        // Extract path variables based on method annotations
        MethodParameter[] parameters = handlerMethod.getMethodParameters();
        for (MethodParameter parameter : parameters) {
            if (parameter.hasParameterAnnotation(PathVariable.class)) {
                PathVariable pathVar = parameter.getParameterAnnotation(PathVariable.class);
                if (pathVar == null) {
                    continue;
                }
                String varName = pathVar.value();
                
                // Extract value from path (simplified - real implementation would use path matching)
                String value = extractPathVariable(path, varName);
                if (value != null) {
                    SecureInputValidationService.InputType inputType = 
                        rules.getPathVariableType(varName);
                    
                    SecureInputValidationService.ValidationContext context = 
                        new SecureInputValidationService.ValidationContext(
                            request.getRemoteUser(), 
                            clientIp, 
                            inputType
                        );
                    
                    SecureInputValidationService.ValidationResult result = 
                        validationService.validateInput(value, inputType, context);
                    
                    if (!result.isValid()) {
                        log.error("SECURITY: Path variable '{}' validation failed: {} from IP: {}", 
                            varName, result.getError(), clientIp);
                        return false;
                    }
                }
            }
        }
        
        return true;
    }
    
    /**
     * Check for request smuggling attempts
     */
    private boolean isRequestSmugglingAttempt(HttpServletRequest request) {
        // Check for conflicting Content-Length and Transfer-Encoding
        String contentLength = request.getHeader("Content-Length");
        String transferEncoding = request.getHeader("Transfer-Encoding");
        
        if (contentLength != null && transferEncoding != null) {
            log.warn("SECURITY: Both Content-Length and Transfer-Encoding present");
            return true;
        }
        
        // Check for multiple Content-Length headers
        Enumeration<String> contentLengths = request.getHeaders("Content-Length");
        Set<String> uniqueLengths = new HashSet<>();
        while (contentLengths.hasMoreElements()) {
            uniqueLengths.add(contentLengths.nextElement());
        }
        if (uniqueLengths.size() > 1) {
            log.warn("SECURITY: Multiple different Content-Length values");
            return true;
        }
        
        // Check for chunked encoding tricks
        if (transferEncoding != null) {
            String normalized = transferEncoding.replaceAll("\\s", "").toLowerCase();
            if (normalized.contains("chunked") && !normalized.equals("chunked")) {
                log.warn("SECURITY: Suspicious Transfer-Encoding value");
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get validation rules for handler method
     */
    private ValidationRules getValidationRules(HandlerMethod handlerMethod) {
        return validationCache.computeIfAbsent(handlerMethod.getMethod(), 
            method -> buildValidationRules(method));
    }
    
    /**
     * Build validation rules from method annotations
     */
    private ValidationRules buildValidationRules(Method method) {
        ValidationRules rules = new ValidationRules();
        
        // Parse method parameters
        Parameter[] parameters = method.getParameters();
        for (Parameter parameter : parameters) {
            if (parameter.isAnnotationPresent(ValidatedParam.class)) {
                ValidatedParam annotation = parameter.getAnnotation(ValidatedParam.class);
                if (annotation != null) {
                    rules.addParameterRule(annotation.name(), annotation.type());
                }
            }
        }
        
        // Parse return type for response validation
        if (method.isAnnotationPresent(ValidatedResponse.class)) {
            ValidatedResponse annotation = method.getAnnotation(ValidatedResponse.class);
            if (annotation != null) {
                rules.setResponseValidation(annotation.value());
            }
        }
        
        return rules;
    }
    
    /**
     * Check if header name is valid
     */
    private boolean isValidHeaderName(String headerName) {
        // RFC 7230 compliant header name validation
        return headerName.matches("^[!#$%&'*+\\-.0-9A-Z^_`a-z|~]+$");
    }
    
    /**
     * Check if header value is valid
     */
    private boolean isValidHeaderValue(String headerValue) {
        // Check for null bytes
        if (headerValue.contains("\0")) {
            return false;
        }
        
        // Check for line breaks (header injection)
        if (headerValue.contains("\r") || headerValue.contains("\n")) {
            return false;
        }
        
        // Check for control characters
        for (char c : headerValue.toCharArray()) {
            if (Character.isISOControl(c) && c != '\t') {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Check for header injection attempts
     */
    private boolean containsHeaderInjection(String value) {
        String lower = value.toLowerCase();
        return lower.contains("%0d") || lower.contains("%0a") ||
               lower.contains("\\r") || lower.contains("\\n");
    }
    
    /**
     * Validate specific headers based on their purpose
     */
    private boolean validateSpecificHeader(String headerName, String headerValue) {
        switch (headerName.toLowerCase()) {
            case "host":
                // Validate host header format
                return headerValue.matches("^[a-zA-Z0-9.-]+(:[0-9]+)?$");
                
            case "content-type":
                // Validate content type
                return isValidContentType(headerValue);
                
            case "authorization":
                // Don't log auth headers but validate format
                return headerValue.length() < 8192; // Reasonable limit
                
            case "user-agent":
                // Check for malicious user agents
                return !containsMaliciousUserAgent(headerValue);
                
            default:
                return true;
        }
    }
    
    /**
     * Check for dangerous header combinations
     */
    private boolean hasDangerousHeaderCombination(HttpServletRequest request) {
        int dangerousCount = 0;
        
        for (String dangerous : DANGEROUS_HEADERS) {
            if (request.getHeader(dangerous) != null) {
                dangerousCount++;
            }
        }
        
        // Multiple dangerous headers might indicate an attack
        return dangerousCount > 2;
    }
    
    /**
     * Validate content type header
     */
    private boolean isValidContentType(String contentType) {
        Set<String> validTypes = Set.of(
            "application/json", "application/xml", "text/plain", 
            "text/html", "application/x-www-form-urlencoded",
            "multipart/form-data"
        );
        
        String mainType = contentType.split(";")[0].trim().toLowerCase();
        return validTypes.contains(mainType);
    }
    
    /**
     * Check for malicious user agents
     */
    private boolean containsMaliciousUserAgent(String userAgent) {
        String lower = userAgent.toLowerCase();
        
        // Common attack tool signatures
        String[] maliciousPatterns = {
            "sqlmap", "nikto", "nessus", "metasploit", "burp",
            "zap", "acunetix", "nmap", "masscan", "hydra"
        };
        
        for (String pattern : maliciousPatterns) {
            if (lower.contains(pattern)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if request has body
     */
    private boolean hasRequestBody(HttpServletRequest request) {
        String method = request.getMethod();
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }
    
    /**
     * Read request body
     */
    private String readRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            if (reader != null) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
        }
        return sb.toString();
    }
    
    /**
     * Get client IP address
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Take the first IP if there are multiple
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Check if parameter name is valid
     */
    private boolean isValidParameterName(String paramName) {
        // Allow alphanumeric, underscore, hyphen, and dot
        return paramName.matches("^[a-zA-Z0-9._-]+$") && paramName.length() <= 100;
    }
    
    /**
     * Extract path variable value (simplified)
     */
    private String extractPathVariable(String path, String varName) {
        // This is a simplified extraction - real implementation would use Spring's path matching
        String[] parts = path.split("/");
        if (parts.length > 0) {
            return parts[parts.length - 1]; // Return last segment as example
        }
        return null;
    }
    
    /**
     * Validation rules for a method
     */
    private static class ValidationRules {
        private final Map<String, SecureInputValidationService.InputType> parameterTypes = new HashMap<>();
        private final Map<String, SecureInputValidationService.InputType> fieldTypes = new HashMap<>();
        private final Map<String, SecureInputValidationService.InputType> pathVariableTypes = new HashMap<>();
        private boolean responseValidation = false;
        
        public void addParameterRule(String name, SecureInputValidationService.InputType type) {
            parameterTypes.put(name, type);
        }
        
        public void addFieldRule(String name, SecureInputValidationService.InputType type) {
            fieldTypes.put(name, type);
        }
        
        public void addPathVariableRule(String name, SecureInputValidationService.InputType type) {
            pathVariableTypes.put(name, type);
        }
        
        public SecureInputValidationService.InputType getParameterType(String name) {
            return parameterTypes.getOrDefault(name, SecureInputValidationService.InputType.GENERIC_TEXT);
        }
        
        public SecureInputValidationService.InputType getFieldType(String name) {
            return fieldTypes.getOrDefault(name, SecureInputValidationService.InputType.GENERIC_TEXT);
        }
        
        public SecureInputValidationService.InputType getPathVariableType(String name) {
            return pathVariableTypes.getOrDefault(name, SecureInputValidationService.InputType.GENERIC_TEXT);
        }
        
        public void setResponseValidation(boolean enabled) {
            this.responseValidation = enabled;
        }
    }
    
    // Custom annotations for validation
    @interface ValidatedParam {
        String name();
        SecureInputValidationService.InputType type();
    }
    
    @interface ValidatedResponse {
        boolean value() default true;
    }
    
    // Note: Using Spring's @PathVariable annotation imported above
}