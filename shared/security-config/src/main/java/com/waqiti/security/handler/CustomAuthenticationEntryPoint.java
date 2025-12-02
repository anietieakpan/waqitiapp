package com.waqiti.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom Authentication Entry Point for handling authentication failures
 * 
 * Provides consistent error responses when authentication fails
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {
    
    private final ObjectMapper objectMapper;
    
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                        AuthenticationException authException) throws IOException {
        
        log.warn("Authentication failed for request: {} {} - {}", 
                request.getMethod(), request.getRequestURI(), authException.getMessage());
        
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now().toString());
        errorResponse.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        errorResponse.put("error", "Unauthorized");
        errorResponse.put("message", "Authentication required");
        errorResponse.put("path", request.getRequestURI());
        errorResponse.put("method", request.getMethod());
        
        // Add additional context in development mode
        if (isDevelopmentMode()) {
            errorResponse.put("details", authException.getMessage());
            errorResponse.put("type", authException.getClass().getSimpleName());
        }
        
        // Add correlation ID if present
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId != null) {
            errorResponse.put("correlationId", correlationId);
        }
        
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
    
    private boolean isDevelopmentMode() {
        // Check if running in development mode
        String profiles = System.getProperty("spring.profiles.active");
        return profiles != null && (profiles.contains("dev") || profiles.contains("local"));
    }
}