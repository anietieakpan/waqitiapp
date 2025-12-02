package com.waqiti.common.security.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom access denied handler for handling authorization failures
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {
    
    private final ObjectMapper objectMapper;
    
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                      AccessDeniedException accessDeniedException) throws IOException {
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null ? authentication.getName() : "anonymous";
        
        log.warn("Access denied for user: {} to resource: {} {} - Reason: {}",
            username, request.getMethod(), request.getRequestURI(), accessDeniedException.getMessage());
        
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        
        // Add security headers
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-XSS-Protection", "1; mode=block");
        
        Map<String, Object> errorResponse = createErrorResponse(request, accessDeniedException, authentication);
        
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
    
    private Map<String, Object> createErrorResponse(HttpServletRequest request,
                                                  AccessDeniedException accessDeniedException,
                                                  Authentication authentication) {
        Map<String, Object> errorResponse = new HashMap<>();
        
        errorResponse.put("error", "access_denied");
        errorResponse.put("message", "Insufficient privileges to access this resource");
        errorResponse.put("status", HttpStatus.FORBIDDEN.value());
        errorResponse.put("timestamp", LocalDateTime.now().toString());
        errorResponse.put("path", request.getRequestURI());
        
        // Add user context information
        if (authentication != null) {
            Map<String, Object> userContext = new HashMap<>();
            userContext.put("username", authentication.getName());
            userContext.put("authorities", authentication.getAuthorities().toString());
            userContext.put("authenticated", authentication.isAuthenticated());
            errorResponse.put("user_context", userContext);
        }
        
        // Determine required permissions
        String resource = request.getRequestURI();
        String method = request.getMethod();
        String requiredRole = determineRequiredRole(resource, method);
        
        Map<String, Object> details = new HashMap<>();
        details.put("description", "You do not have sufficient permissions to access this resource");
        details.put("required_role", requiredRole);
        details.put("action", "Contact your administrator to request access or verify your permissions");
        details.put("resource_path", resource);
        details.put("http_method", method);
        
        errorResponse.put("details", details);
        
        return errorResponse;
    }
    
    private String determineRequiredRole(String resource, String method) {
        // Admin endpoints
        if (resource.contains("/admin/")) {
            return "ROLE_ADMIN";
        }
        
        // Actuator endpoints
        if (resource.contains("/actuator/")) {
            return "ROLE_ACTUATOR";
        }
        
        // Compliance endpoints
        if (resource.contains("/compliance/")) {
            return "ROLE_COMPLIANCE_OFFICER or ROLE_ADMIN";
        }
        
        // Fraud endpoints
        if (resource.contains("/fraud/")) {
            return "ROLE_FRAUD_ANALYST or ROLE_ADMIN";
        }
        
        // KYC endpoints
        if (resource.contains("/kyc/")) {
            return "ROLE_KYC_AGENT or ROLE_ADMIN";
        }
        
        // Payment endpoints
        if (resource.contains("/payments/") || resource.contains("/transactions/") || resource.contains("/wallets/")) {
            return "ROLE_USER or ROLE_MERCHANT or ROLE_ADMIN";
        }
        
        // Default for authenticated endpoints
        return "Valid authentication required";
    }
}