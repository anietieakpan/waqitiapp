package com.waqiti.common.security.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom authentication entry point for handling authentication failures
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {
    
    private final ObjectMapper objectMapper;
    
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                        AuthenticationException authException) throws IOException {
        
        log.warn("Authentication failed for request: {} {} - Reason: {}",
            request.getMethod(), request.getRequestURI(), authException.getMessage());
        
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        
        // Add security headers
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-XSS-Protection", "1; mode=block");
        
        Map<String, Object> errorResponse = createErrorResponse(request, authException);
        
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
    
    private Map<String, Object> createErrorResponse(HttpServletRequest request, 
                                                  AuthenticationException authException) {
        Map<String, Object> errorResponse = new HashMap<>();
        
        errorResponse.put("error", "authentication_required");
        errorResponse.put("message", "Authentication is required to access this resource");
        errorResponse.put("status", HttpStatus.UNAUTHORIZED.value());
        errorResponse.put("timestamp", LocalDateTime.now().toString());
        errorResponse.put("path", request.getRequestURI());
        
        // Determine specific error type
        String errorType = determineErrorType(authException);
        errorResponse.put("error_type", errorType);
        
        // Add helpful information based on error type
        Map<String, Object> details = new HashMap<>();
        switch (errorType) {
            case "TOKEN_EXPIRED":
                details.put("description", "The authentication token has expired");
                details.put("action", "Please refresh your token or login again");
                break;
            case "TOKEN_INVALID":
                details.put("description", "The authentication token is invalid or malformed");
                details.put("action", "Please provide a valid authentication token");
                break;
            case "TOKEN_MISSING":
                details.put("description", "No authentication token provided");
                details.put("action", "Please include a valid Bearer token in the Authorization header");
                break;
            default:
                details.put("description", "Authentication failed");
                details.put("action", "Please login to access this resource");
        }
        
        errorResponse.put("details", details);
        
        // Add WWW-Authenticate header information
        errorResponse.put("authentication_schemes", "Bearer");
        
        return errorResponse;
    }
    
    private String determineErrorType(AuthenticationException authException) {
        String exceptionMessage = authException.getMessage().toLowerCase();
        
        if (exceptionMessage.contains("expired")) {
            return "TOKEN_EXPIRED";
        } else if (exceptionMessage.contains("invalid") || exceptionMessage.contains("malformed")) {
            return "TOKEN_INVALID";
        } else if (exceptionMessage.contains("missing") || exceptionMessage.contains("not found")) {
            return "TOKEN_MISSING";
        } else if (exceptionMessage.contains("bearer")) {
            return "TOKEN_MISSING";
        } else {
            return "AUTHENTICATION_FAILED";
        }
    }
}