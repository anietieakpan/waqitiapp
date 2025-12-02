package com.waqiti.common.security.filters;

import com.waqiti.common.security.service.RateLimitingService;
import com.waqiti.common.security.model.RateLimitResult;
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
 * Rate limiting filter
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {
    
    private final RateLimitingService rateLimitingService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String clientId = extractClientId(request);
        String endpoint = request.getRequestURI();
        
        RateLimitResult result = rateLimitingService.checkRateLimit(clientId, endpoint);
        
        // Add rate limit headers
        response.setHeader("X-Rate-Limit-Limit", String.valueOf(result.getLimit()));
        response.setHeader("X-Rate-Limit-Remaining", String.valueOf(result.getRemaining()));
        response.setHeader("X-Rate-Limit-Reset", String.valueOf(result.getResetTime()));
        
        if (!result.isAllowed()) {
            log.warn("Rate limit exceeded for client {} on endpoint {}", clientId, endpoint);
            response.setStatus(429); // Too Many Requests
            response.setContentType("application/json");
            response.getWriter().write("""
                {
                    "error": "RATE_LIMIT_EXCEEDED",
                    "message": "Too many requests. Please try again later.",
                    "retryAfter": %d
                }
                """.formatted(result.getRetryAfter()));
            return;
        }
        
        filterChain.doFilter(request, response);
    }
    
    private String extractClientId(HttpServletRequest request) {
        // Try API key first
        String apiKey = request.getHeader("API-Key");
        if (apiKey != null) {
            return "api-key:" + apiKey;
        }
        
        // Fall back to IP address
        String clientIp = getClientIpAddress(request);
        return "ip:" + clientIp;
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
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
}