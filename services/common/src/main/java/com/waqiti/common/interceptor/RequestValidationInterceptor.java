package com.waqiti.common.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.error.SecurityAuditService;
import com.waqiti.common.exception.ValidationException;
import com.waqiti.common.exception.GenericValidationException;
import com.waqiti.common.security.SqlInjectionPreventionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class RequestValidationInterceptor implements HandlerInterceptor {
    
    private final SqlInjectionPreventionService sqlInjectionService;
    private final SecurityAuditService securityAuditService;
    private final ObjectMapper objectMapper;
    
    private static final int MAX_REQUEST_SIZE = 10_000_000;
    private static final int MAX_HEADER_SIZE = 8192;
    private static final int MAX_PARAM_LENGTH = 2000;
    private static final int MAX_PARAMS_COUNT = 100;
    
    private static final Pattern SUSPICIOUS_PATTERNS = Pattern.compile(
        ".*(\\.\\.[\\\\/]|javascript:|<script|eval\\(|exec\\(|onclick=|onerror=).*",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Set<String> ALLOWED_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    
    private static final Set<String> REQUIRED_HEADERS = Set.of(
        "User-Agent",
        "Accept"
    );
    
    private static final List<Pattern> MALICIOUS_USER_AGENTS = Arrays.asList(
        Pattern.compile(".*(?:nikto|sqlmap|havij|acunetix|nessus|masscan|zap|burpsuite).*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*(?:bot|crawler|spider).*", Pattern.CASE_INSENSITIVE)
    );
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) 
            throws Exception {
        
        String requestId = UUID.randomUUID().toString();
        request.setAttribute("requestId", requestId);
        request.setAttribute("requestStartTime", System.currentTimeMillis());
        
        try {
            validateHttpMethod(request);
            validateRequestSize(request);
            validateHeaders(request);
            validateUserAgent(request);
            validateParameters(request);
            validatePathTraversal(request);
            validateContentType(request);
            validateSuspiciousPatterns(request);
            
            logRequestDetails(request, requestId);
            
            return true;
            
        } catch (ValidationException e) {
            handleValidationFailure(request, response, e, requestId);
            return false;
        } catch (Exception e) {
            log.error("Unexpected error in request validation: requestId={}", requestId, e);
            handleValidationFailure(request, response,
                new GenericValidationException("Request validation failed"), requestId);
            return false;
        }
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                               Object handler, Exception ex) {
        
        Long startTime = (Long) request.getAttribute("requestStartTime");
        String requestId = (String) request.getAttribute("requestId");
        
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            
            if (duration > 10000) {
                log.warn("Slow request detected: requestId={}, uri={}, duration={}ms",
                    requestId, request.getRequestURI(), duration);
            }
            
            if (handler instanceof HandlerMethod) {
                HandlerMethod handlerMethod = (HandlerMethod) handler;
                log.debug("Request completed: requestId={}, method={}, uri={}, status={}, duration={}ms",
                    requestId, request.getMethod(), request.getRequestURI(), 
                    response.getStatus(), duration);
            }
        }
    }
    
    private void validateHttpMethod(HttpServletRequest request) {
        String method = request.getMethod();
        
        if (!ALLOWED_METHODS.contains(method)) {
            log.warn("Invalid HTTP method: method={}, uri={}, ip={}", 
                method, request.getRequestURI(), getClientIp(request));
            throw new IllegalArgumentException("Invalid HTTP method: " + method);
        }
    }
    
    private void validateRequestSize(HttpServletRequest request) {
        long contentLength = request.getContentLengthLong();
        
        if (contentLength > MAX_REQUEST_SIZE) {
            log.warn("Request size exceeds limit: size={}, limit={}, uri={}, ip={}",
                contentLength, MAX_REQUEST_SIZE, request.getRequestURI(), getClientIp(request));
            
            securityAuditService.reportSecurityViolation(
                getClientIp(request),
                "REQUEST_SIZE_EXCEEDED",
                String.format("Request size %d exceeds limit %d", contentLength, MAX_REQUEST_SIZE),
                request.getHeader("User-Agent")
            );
            
            throw new IllegalArgumentException("Request size exceeds maximum allowed limit");
        }
    }
    
    private void validateHeaders(HttpServletRequest request) {
        Enumeration<String> headerNames = request.getHeaderNames();
        int headerCount = 0;
        
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            headerCount++;
            
            if (headerValue != null && headerValue.length() > MAX_HEADER_SIZE) {
                log.warn("Header exceeds size limit: header={}, size={}, uri={}, ip={}",
                    headerName, headerValue.length(), request.getRequestURI(), getClientIp(request));
                throw new IllegalArgumentException("Header size exceeds limit: " + headerName);
            }
            
            if (headerCount > 100) {
                log.warn("Too many headers: count={}, uri={}, ip={}",
                    headerCount, request.getRequestURI(), getClientIp(request));
                throw new IllegalArgumentException("Too many request headers");
            }
            
            if (containsSqlInjection(headerValue)) {
                log.warn("SQL injection detected in header: header={}, uri={}, ip={}",
                    headerName, request.getRequestURI(), getClientIp(request));
                
                securityAuditService.reportSecurityViolation(
                    getClientIp(request),
                    "SQL_INJECTION",
                    String.format("SQL injection detected in header: %s", headerName),
                    request.getHeader("User-Agent")
                );
                
                throw new IllegalArgumentException("Invalid header value");
            }
        }
    }
    
    private void validateUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        
        if (userAgent == null || userAgent.trim().isEmpty()) {
            log.warn("Missing User-Agent header: uri={}, ip={}", 
                request.getRequestURI(), getClientIp(request));
            
            if (isFinancialEndpoint(request)) {
                throw new IllegalArgumentException("User-Agent header is required");
            }
        }
        
        if (userAgent != null) {
            for (Pattern pattern : MALICIOUS_USER_AGENTS) {
                if (pattern.matcher(userAgent).matches()) {
                    log.warn("Suspicious User-Agent detected: userAgent={}, uri={}, ip={}",
                        userAgent, request.getRequestURI(), getClientIp(request));
                    
                    securityAuditService.reportSecurityViolation(
                        getClientIp(request),
                        "SUSPICIOUS_USER_AGENT",
                        String.format("Suspicious User-Agent detected: %s", userAgent),
                        userAgent
                    );
                    
                    throw new IllegalArgumentException("Suspicious User-Agent detected");
                }
            }
        }
    }
    
    private void validateParameters(HttpServletRequest request) {
        Map<String, String[]> paramMap = request.getParameterMap();
        
        if (paramMap.size() > MAX_PARAMS_COUNT) {
            log.warn("Too many parameters: count={}, uri={}, ip={}",
                paramMap.size(), request.getRequestURI(), getClientIp(request));
            throw new IllegalArgumentException("Too many request parameters");
        }
        
        for (Map.Entry<String, String[]> entry : paramMap.entrySet()) {
            String paramName = entry.getKey();
            String[] paramValues = entry.getValue();
            
            if (paramName.length() > MAX_PARAM_LENGTH) {
                log.warn("Parameter name too long: param={}, length={}, uri={}, ip={}",
                    paramName, paramName.length(), request.getRequestURI(), getClientIp(request));
                throw new IllegalArgumentException("Parameter name too long");
            }
            
            for (String paramValue : paramValues) {
                if (paramValue != null && paramValue.length() > MAX_PARAM_LENGTH) {
                    log.warn("Parameter value too long: param={}, length={}, uri={}, ip={}",
                        paramName, paramValue.length(), request.getRequestURI(), getClientIp(request));
                    throw new IllegalArgumentException("Parameter value too long: " + paramName);
                }
                
                if (containsSqlInjection(paramValue)) {
                    log.warn("SQL injection detected in parameter: param={}, uri={}, ip={}",
                        paramName, request.getRequestURI(), getClientIp(request));
                    
                    securityAuditService.reportSecurityViolation(
                        getClientIp(request),
                        "SQL_INJECTION",
                        String.format("SQL injection detected in parameter: %s", paramName),
                        request.getHeader("User-Agent")
                    );
                    
                    throw new IllegalArgumentException("Invalid parameter value: " + paramName);
                }
                
                if (containsXssAttempt(paramValue)) {
                    log.warn("XSS attempt detected in parameter: param={}, uri={}, ip={}",
                        paramName, request.getRequestURI(), getClientIp(request));
                    
                    securityAuditService.reportSecurityViolation(
                        getClientIp(request),
                        "XSS",
                        String.format("XSS attempt detected in parameter: %s", paramName),
                        request.getHeader("User-Agent")
                    );
                    
                    throw new IllegalArgumentException("Invalid parameter value: " + paramName);
                }
            }
        }
    }
    
    private void validatePathTraversal(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String decodedUri = java.net.URLDecoder.decode(uri, StandardCharsets.UTF_8);
        
        if (decodedUri.contains("../") || decodedUri.contains("..\\") || 
            decodedUri.contains("%2e%2e") || decodedUri.contains("%252e%252e")) {
            
            log.warn("Path traversal attempt detected: uri={}, ip={}",
                uri, getClientIp(request));
            
            securityAuditService.reportSecurityViolation(
                getClientIp(request),
                "PATH_TRAVERSAL",
                String.format("Path traversal attempt: %s", decodedUri),
                request.getHeader("User-Agent")
            );
            
            throw new IllegalArgumentException("Invalid request path");
        }
    }
    
    private void validateContentType(HttpServletRequest request) {
        if (Arrays.asList("POST", "PUT", "PATCH").contains(request.getMethod())) {
            String contentType = request.getContentType();
            
            if (contentType != null && !isAllowedContentType(contentType)) {
                log.warn("Unsupported content type: contentType={}, uri={}, ip={}",
                    contentType, request.getRequestURI(), getClientIp(request));
                throw new IllegalArgumentException("Unsupported content type");
            }
        }
    }
    
    private void validateSuspiciousPatterns(HttpServletRequest request) {
        String queryString = request.getQueryString();
        
        if (queryString != null && SUSPICIOUS_PATTERNS.matcher(queryString).matches()) {
            log.warn("Suspicious pattern in query string: uri={}, query={}, ip={}",
                request.getRequestURI(), queryString, getClientIp(request));
            
            securityAuditService.reportSecurityViolation(
                getClientIp(request),
                "SUSPICIOUS_PATTERN",
                String.format("Suspicious pattern in query: %s", queryString),
                request.getHeader("User-Agent")
            );
            
            throw new IllegalArgumentException("Suspicious request pattern detected");
        }
    }
    
    private boolean containsSqlInjection(String value) {
        if (value == null) {
            return false;
        }
        
        try {
            return sqlInjectionService.containsSqlInjection(value);
        } catch (Exception e) {
            log.error("Error checking SQL injection", e);
            return false;
        }
    }
    
    private boolean containsXssAttempt(String value) {
        if (value == null) {
            return false;
        }
        
        String lowerValue = value.toLowerCase();
        return lowerValue.contains("<script") || 
               lowerValue.contains("javascript:") ||
               lowerValue.contains("onerror=") ||
               lowerValue.contains("onclick=") ||
               lowerValue.contains("onload=");
    }
    
    private boolean isAllowedContentType(String contentType) {
        String normalizedType = contentType.toLowerCase().split(";")[0].trim();
        return normalizedType.equals("application/json") ||
               normalizedType.equals("application/x-www-form-urlencoded") ||
               normalizedType.equals("multipart/form-data") ||
               normalizedType.equals("text/plain");
    }
    
    private boolean isFinancialEndpoint(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.contains("/payment") || 
               uri.contains("/wallet") || 
               uri.contains("/transaction") ||
               uri.contains("/transfer");
    }
    
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
    
    private String getCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                return auth.getName();
            }
        } catch (Exception e) {
            log.debug("Could not get current user ID", e);
        }
        return "anonymous";
    }
    
    private void logRequestDetails(HttpServletRequest request, String requestId) {
        log.debug("Request validation passed: requestId={}, method={}, uri={}, ip={}, user={}",
            requestId, request.getMethod(), request.getRequestURI(), 
            getClientIp(request), getCurrentUserId());
    }
    
    private void handleValidationFailure(HttpServletRequest request, HttpServletResponse response,
                                        ValidationException e, String requestId) throws IOException {
        
        log.warn("Request validation failed: requestId={}, method={}, uri={}, ip={}, reason={}",
            requestId, request.getMethod(), request.getRequestURI(), 
            getClientIp(request), e.getMessage());
        
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        Map<String, Object> errorResponse = Map.of(
            "timestamp", LocalDateTime.now().toString(),
            "status", HttpStatus.BAD_REQUEST.value(),
            "error", "Bad Request",
            "message", "Request validation failed",
            "requestId", requestId,
            "path", request.getRequestURI()
        );
        
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}