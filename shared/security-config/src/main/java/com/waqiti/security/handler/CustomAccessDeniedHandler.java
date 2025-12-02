package com.waqiti.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom Access Denied Handler for handling authorization failures
 * 
 * Provides consistent error responses when access is denied
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomAccessDeniedHandler implements AccessDeniedHandler {
    
    private final ObjectMapper objectMapper;
    
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                      AccessDeniedException accessDeniedException) throws IOException {
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null ? authentication.getName() : "anonymous";
        
        log.warn("Access denied for user: {} attempting to access: {} {} - {}", 
                username, request.getMethod(), request.getRequestURI(), accessDeniedException.getMessage());
        
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now().toString());
        errorResponse.put("status", HttpServletResponse.SC_FORBIDDEN);
        errorResponse.put("error", "Forbidden");
        errorResponse.put("message", "Access denied");
        errorResponse.put("path", request.getRequestURI());
        errorResponse.put("method", request.getMethod());
        errorResponse.put("user", username);
        
        // Add additional context in development mode
        if (isDevelopmentMode()) {
            errorResponse.put("details", accessDeniedException.getMessage());
            errorResponse.put("requiredAuthorities", extractRequiredAuthorities(request));
            errorResponse.put("userAuthorities", extractUserAuthorities(authentication));
        }
        
        // Add correlation ID if present
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId != null) {
            errorResponse.put("correlationId", correlationId);
        }
        
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
    
    private boolean isDevelopmentMode() {
        String profiles = System.getProperty("spring.profiles.active");
        return profiles != null && (profiles.contains("dev") || profiles.contains("local"));
    }
    
    private String extractRequiredAuthorities(HttpServletRequest request) {
        // Try to extract required authorities from the request
        // This is simplified - in a real implementation, you might want to
        // analyze the security configuration to determine required authorities
        return "Check endpoint documentation for required roles/scopes";
    }
    
    private Object extractUserAuthorities(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return "No authorities";
        }
        return authentication.getAuthorities().toString();
    }
}