package com.waqiti.common.security.filters;

import com.waqiti.common.security.service.ApiKeyService;
import com.waqiti.common.security.model.ApiKeyValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * API key authentication filter
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    
    private final ApiKeyService apiKeyService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String apiKey = request.getHeader("API-Key");
        
        if (apiKey != null) {
            ApiKeyValidationResult result = apiKeyService.validateApiKey(apiKey);
            
            if (!result.isValid()) {
                log.warn("Invalid API key attempted: {}", apiKey);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("""
                    {
                        "error": "INVALID_API_KEY",
                        "message": "The provided API key is invalid or expired"
                    }
                    """);
                return;
            }
            
            // Set authentication context
            request.setAttribute("apiKeyInfo", result.getApiKeyInfo());
            
            // Log API key usage
            apiKeyService.recordApiKeyUsage(apiKey, request.getRequestURI());
        }
        
        filterChain.doFilter(request, response);
    }
}