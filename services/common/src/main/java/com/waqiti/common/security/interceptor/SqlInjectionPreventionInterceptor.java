package com.waqiti.common.security.interceptor;

import com.waqiti.common.security.sql.SqlInjectionPrevention;
import com.waqiti.common.security.audit.SecurityAuditLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Enumeration;
import java.util.Map;

/**
 * CRITICAL SECURITY: SQL Injection Prevention Interceptor
 * 
 * This interceptor validates all HTTP request parameters for potential SQL injection attempts
 * before they reach the controller layer. It provides a defense-in-depth approach to security.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SqlInjectionPreventionInterceptor implements HandlerInterceptor {

    private final SqlInjectionPrevention sqlInjectionPrevention;
    private final SecurityAuditLogger securityAuditLogger;

    // Parameters that are particularly high-risk for SQL injection
    private static final String[] HIGH_RISK_PARAMETERS = {
        "search", "query", "filter", "sort", "orderBy", "where", "having", 
        "accountNumber", "accountName", "description", "reference", "memo",
        "transactionId", "paymentId", "customerId", "userId", "email"
    };

    // Financial endpoints that require extra scrutiny
    private static final String[] FINANCIAL_ENDPOINTS = {
        "/api/accounts", "/api/payments", "/api/transactions", "/api/transfers",
        "/api/deposits", "/api/withdrawals", "/api/balances", "/api/statements",
        "/api/loans", "/api/credit", "/api/investments", "/api/trading"
    };

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) 
            throws Exception {
        
        String requestURI = request.getRequestURI();
        String method = request.getMethod();
        String userAgent = request.getHeader("User-Agent");
        String remoteAddr = getClientIpAddress(request);
        String userId = extractUserId(request);
        
        try {
            // Check if this is a financial endpoint requiring extra security
            boolean isFinancialEndpoint = isFinancialEndpoint(requestURI);
            
            // Validate all request parameters
            boolean hasSecurityViolation = false;
            
            // Check URL parameters
            Enumeration<String> parameterNames = request.getParameterNames();
            while (parameterNames.hasMoreElements()) {
                String paramName = parameterNames.nextElement();
                String[] paramValues = request.getParameterValues(paramName);
                
                for (String paramValue : paramValues) {
                    if (paramValue != null) {
                        // Check for SQL injection patterns
                        if (sqlInjectionPrevention.containsSqlInjectionPayload(paramValue)) {
                            logSecurityViolation("SQL_INJECTION_ATTEMPT_PARAMETER", requestURI, 
                                paramName, paramValue, userId, userAgent, remoteAddr);
                            hasSecurityViolation = true;
                        }
                        
                        // Extra validation for high-risk parameters
                        if (isHighRiskParameter(paramName)) {
                            if (!sqlInjectionPrevention.isInputSafe(paramValue)) {
                                logSecurityViolation("HIGH_RISK_PARAMETER_VIOLATION", requestURI, 
                                    paramName, paramValue, userId, userAgent, remoteAddr);
                                hasSecurityViolation = true;
                            }
                        }
                        
                        // Extra strict validation for financial endpoints
                        if (isFinancialEndpoint && paramValue.length() > 1000) {
                            logSecurityViolation("OVERSIZED_PARAMETER_FINANCIAL", requestURI, 
                                paramName, "OVERSIZED", userId, userAgent, remoteAddr);
                            hasSecurityViolation = true;
                        }
                    }
                }
            }
            
            // Validate query string as a whole
            String queryString = request.getQueryString();
            if (queryString != null && !queryString.isEmpty()) {
                if (sqlInjectionPrevention.containsSqlInjectionPayload(queryString)) {
                    logSecurityViolation("SQL_INJECTION_ATTEMPT_QUERY_STRING", requestURI, 
                        "queryString", queryString, userId, userAgent, remoteAddr);
                    hasSecurityViolation = true;
                }
                
                // Check for excessive query string length (potential buffer overflow)
                if (queryString.length() > 4096) {
                    logSecurityViolation("OVERSIZED_QUERY_STRING", requestURI, 
                        "queryString", "OVERSIZED", userId, userAgent, remoteAddr);
                    hasSecurityViolation = true;
                }
            }
            
            // Validate request headers for injection attempts
            validateHeaders(request, requestURI, userId, userAgent, remoteAddr);
            
            // If security violation detected, block the request
            if (hasSecurityViolation) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Invalid request parameters\",\"code\":\"SECURITY_VIOLATION\"}");
                
                // Log the blocked request
                securityAuditLogger.logSecurityViolation("REQUEST_BLOCKED_SQL_INJECTION", userId,
                    "Request blocked due to SQL injection attempt",
                    Map.of("uri", requestURI, "method", method, "remoteAddr", remoteAddr,
                          "userAgent", userAgent != null ? userAgent : "unknown"));
                
                return false; // Block the request
            }
            
            // Log successful security validation for financial endpoints
            if (isFinancialEndpoint) {
                securityAuditLogger.logSecurityEvent("FINANCIAL_REQUEST_VALIDATED", userId,
                    "Financial endpoint request passed security validation",
                    Map.of("uri", requestURI, "method", method, "remoteAddr", remoteAddr));
            }
            
            return true; // Allow the request to proceed
            
        } catch (Exception e) {
            log.error("Error in SQL injection prevention interceptor", e);
            
            // In case of interceptor error, log and allow request (fail open for availability)
            // but alert security team
            securityAuditLogger.logSecurityEvent("INTERCEPTOR_ERROR", userId,
                "SQL injection interceptor error occurred",
                Map.of("uri", requestURI, "error", e.getMessage()));
            
            return true;
        }
    }

    /**
     * Validate request headers for potential injection attempts
     */
    private void validateHeaders(HttpServletRequest request, String requestURI, String userId, 
                               String userAgent, String remoteAddr) {
        
        // Headers that should not contain SQL injection patterns
        String[] securityHeaders = {"X-Search-Query", "X-Filter", "X-Sort-By", "X-Custom-Query"};
        
        for (String headerName : securityHeaders) {
            String headerValue = request.getHeader(headerName);
            if (headerValue != null) {
                if (sqlInjectionPrevention.containsSqlInjectionPayload(headerValue)) {
                    logSecurityViolation("SQL_INJECTION_ATTEMPT_HEADER", requestURI, 
                        headerName, headerValue, userId, userAgent, remoteAddr);
                }
            }
        }
        
        // Check for suspicious User-Agent patterns
        if (userAgent != null) {
            if (sqlInjectionPrevention.containsSqlInjectionPayload(userAgent)) {
                logSecurityViolation("MALICIOUS_USER_AGENT", requestURI, 
                    "User-Agent", userAgent, userId, userAgent, remoteAddr);
            }
        }
    }

    /**
     * Check if the parameter is considered high-risk for SQL injection
     */
    private boolean isHighRiskParameter(String paramName) {
        if (paramName == null) return false;
        
        String lowerParamName = paramName.toLowerCase();
        for (String highRiskParam : HIGH_RISK_PARAMETERS) {
            if (lowerParamName.contains(highRiskParam.toLowerCase())) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Check if the endpoint is a financial endpoint requiring extra security
     */
    private boolean isFinancialEndpoint(String requestURI) {
        if (requestURI == null) return false;
        
        for (String financialEndpoint : FINANCIAL_ENDPOINTS) {
            if (requestURI.startsWith(financialEndpoint)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Extract user ID from request (from JWT token, session, etc.)
     */
    private String extractUserId(HttpServletRequest request) {
        // Try to extract from Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // In a real implementation, decode JWT token to get user ID
            return "user-from-jwt"; // Placeholder
        }
        
        // Try to extract from session
        if (request.getSession(false) != null) {
            Object userId = request.getSession().getAttribute("userId");
            if (userId != null) {
                return userId.toString();
            }
        }
        
        // Try custom header
        String customUserId = request.getHeader("X-User-ID");
        if (customUserId != null) {
            return customUserId;
        }
        
        return "anonymous";
    }

    /**
     * Get the real client IP address (considering proxies)
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Take the first IP from the chain
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }
        
        return request.getRemoteAddr();
    }

    /**
     * Log security violation with detailed context
     */
    private void logSecurityViolation(String violationType, String requestURI, String paramName, 
                                    String paramValue, String userId, String userAgent, String remoteAddr) {
        
        // Log the violation
        log.error("SECURITY VIOLATION: {} - URI: {} Param: {} User: {} IP: {}", 
            violationType, requestURI, paramName, userId, remoteAddr);
        
        // Mask sensitive parameter value for logging
        String maskedValue = paramValue != null && paramValue.length() > 10 ? 
            paramValue.substring(0, 5) + "***" + paramValue.substring(paramValue.length() - 3) : "***";
        
        // Audit log the security violation
        securityAuditLogger.logSecurityViolation(violationType, userId,
            "SQL injection attempt detected in request parameter",
            Map.of("uri", requestURI, "paramName", paramName, "paramValue", maskedValue,
                  "userAgent", userAgent != null ? userAgent : "unknown", "remoteAddr", remoteAddr));
        
        // In production, this could also:
        // - Send real-time alerts to security team
        // - Update threat intelligence systems
        // - Trigger automatic IP blocking
        // - Generate security incident tickets
    }

    /**
     * Rate limiting check (placeholder for future implementation)
     */
    private boolean isRateLimited(String remoteAddr, String userId) {
        // Implement rate limiting logic here
        // Could use Redis, in-memory cache, or dedicated rate limiting service
        return false;
    }

    /**
     * Check if IP is in security blacklist (placeholder for future implementation)
     */
    private boolean isBlacklistedIP(String remoteAddr) {
        // Implement IP blacklist checking here
        // Could integrate with threat intelligence feeds
        return false;
    }
}