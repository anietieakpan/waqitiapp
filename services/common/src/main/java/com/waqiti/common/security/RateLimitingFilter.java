package com.waqiti.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.security.UserPrincipal;
import com.waqiti.common.security.service.ApiKeyService;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP Filter that applies rate limiting to all requests
 * Integrates with RateLimitingService to enforce rate limits
 */
@Slf4j
@Component
@Order(1) // Execute early in filter chain
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rate-limit.filter.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitingService rateLimitingService;
    private final ApiKeyService apiKeyService;
    
    // Cache for API key to user ID mappings (5 minute TTL)
    private final Map<String, String> apiKeyUserCache = new ConcurrentHashMap<>();
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        String clientIp = getClientIpAddress(request);
        
        // Check if IP is blocked first
        if (rateLimitingService.isIpBlocked(clientIp)) {
            handleBlockedIp(response, clientIp);
            return;
        }
        
        // Build rate limit request
        RateLimitRequest rateLimitRequest = buildRateLimitRequest(request);
        
        // Check rate limit
        RateLimitResult result = rateLimitingService.checkRateLimit(rateLimitRequest);
        
        if (result.isAllowed()) {
            // Add rate limit headers to response
            addRateLimitHeaders(response, result);
            
            // Continue with request processing
            filterChain.doFilter(request, response);
        } else {
            // Rate limit exceeded - return 429 Too Many Requests
            handleRateLimitExceeded(response, result);
        }
    }
    
    /**
     * Build rate limit request from HTTP request
     */
    private RateLimitRequest buildRateLimitRequest(HttpServletRequest request) {
        String clientIp = getClientIpAddress(request);
        String endpoint = request.getRequestURI();
        String userId = extractUserId(request);
        String userType = extractUserType(request);
        String apiKey = extractApiKey(request);
        String tenantId = extractTenantId(request);
        
        return RateLimitRequest.builder()
            .clientIp(clientIp)
            .endpoint(endpoint)
            .userId(userId)
            .userType(userType)
            .apiKey(apiKey)
            .tenantId(tenantId)
            .requestedTokens(1)
            .build();
    }
    
    /**
     * Extract client IP address considering proxies
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            // Take the first IP in case of multiple proxies
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Extract user ID from request headers or authentication context
     */
    private String extractUserId(HttpServletRequest request) {
        // 1. Try explicit user ID header first
        String userId = request.getHeader("X-User-ID");
        if (userId != null && !userId.trim().isEmpty()) {
            return userId.trim();
        }
        
        // 2. Try to extract from Spring Security context
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                // Handle different authentication types
                if (authentication instanceof JwtAuthenticationToken) {
                    JwtAuthenticationToken jwtToken = (JwtAuthenticationToken) authentication;
                    Jwt jwt = jwtToken.getToken();
                    
                    // Extract user ID from JWT claims
                    String userIdClaim = jwt.getClaimAsString("sub");
                    if (userIdClaim == null) {
                        userIdClaim = jwt.getClaimAsString("user_id");
                    }
                    if (userIdClaim == null) {
                        userIdClaim = jwt.getClaimAsString("userId");
                    }
                    
                    if (userIdClaim != null) {
                        log.debug("Extracted user ID from JWT: {}", userIdClaim);
                        return userIdClaim;
                    }
                } else if (authentication.getPrincipal() instanceof UserPrincipal) {
                    UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
                    return userPrincipal.getUserId();
                } else if (authentication.getPrincipal() instanceof String) {
                    // Username-based authentication
                    return (String) authentication.getPrincipal();
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract user ID from Spring Security context: {}", e.getMessage());
        }
        
        // 3. Try to extract from JWT token in Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                String userIdFromToken = extractUserIdFromJwtToken(token);
                if (userIdFromToken != null) {
                    return userIdFromToken;
                }
            } catch (Exception e) {
                log.debug("Could not extract user ID from JWT token: {}", e.getMessage());
            }
        }
        
        // 4. Try session-based authentication
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object sessionUserId = session.getAttribute("USER_ID");
            if (sessionUserId != null) {
                return sessionUserId.toString();
            }
            
            // Try authenticated user object in session
            Object userObject = session.getAttribute("AUTHENTICATED_USER");
            if (userObject instanceof com.waqiti.common.domain.UserIdentity) {
                com.waqiti.common.domain.UserIdentity user = (com.waqiti.common.domain.UserIdentity) userObject;
                return user.getId().toString();
            }
        }
        
        // 5. Try API key authentication mapping
        String apiKey = extractApiKey(request);
        if (apiKey != null) {
            String userIdFromApiKey = getUserIdForApiKey(apiKey);
            if (userIdFromApiKey != null) {
                return userIdFromApiKey;
            }
        }
        
        log.debug("No user ID could be extracted from request");
        return null;
    }
    
    /**
     * Extract user ID from JWT token string
     */
    private String extractUserIdFromJwtToken(String token) {
        try {
            // Parse JWT token manually (basic parsing without signature verification for rate limiting)
            String[] parts = token.split("\\.");
            if (parts.length == 3) {
                String payload = parts[1];
                
                // Decode base64 payload
                byte[] decodedBytes = Base64.getUrlDecoder().decode(payload);
                String decodedPayload = new String(decodedBytes);
                
                // Parse JSON payload to extract user claims
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(decodedPayload);
                
                // Try different claim names
                String userId = extractStringFromJsonNode(jsonNode, "sub");
                if (userId == null) {
                    userId = extractStringFromJsonNode(jsonNode, "user_id");
                }
                if (userId == null) {
                    userId = extractStringFromJsonNode(jsonNode, "userId");
                }
                
                return userId;
            }
        } catch (Exception e) {
            log.debug("Failed to parse JWT token for user ID: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Extract string value from JsonNode
     */
    private String extractStringFromJsonNode(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asText() : null;
    }
    
    /**
     * Get user ID associated with an API key
     */
    private String getUserIdForApiKey(String apiKey) {
        // In production, this would query the API key store/database
        // For now, implement basic caching mechanism
        try {
            if (apiKeyUserCache.containsKey(apiKey)) {
                return apiKeyUserCache.get(apiKey);
            }
            
            // Query API key service
            String userId = apiKeyService.getUserIdForApiKey(apiKey);
            if (userId != null) {
                // Cache for 5 minutes
                apiKeyUserCache.put(apiKey, userId);
                return userId;
            }
        } catch (Exception e) {
            log.debug("Failed to resolve user ID for API key: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Extract user type from request
     */
    private String extractUserType(HttpServletRequest request) {
        return request.getHeader("X-User-Type");
    }
    
    /**
     * Extract API key from request
     */
    private String extractApiKey(HttpServletRequest request) {
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null) {
            return apiKey;
        }
        
        // Check Authorization header for API key
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("ApiKey ")) {
            return authorization.substring(7);
        }
        
        return null;
    }
    
    /**
     * Extract tenant ID from request
     */
    private String extractTenantId(HttpServletRequest request) {
        return request.getHeader("X-Tenant-ID");
    }
    
    /**
     * Add rate limit information to response headers
     */
    private void addRateLimitHeaders(HttpServletResponse response, RateLimitResult result) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.getCapacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemainingTokens()));
        
        if (result.getResetTime() != null) {
            response.setHeader("X-RateLimit-Reset", 
                result.getResetTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        
        // Add custom headers for monitoring
        if (result.getRateLimitKey() != null) {
            response.setHeader("X-RateLimit-Key", result.getRateLimitKey());
        }
    }
    
    /**
     * Handle rate limit exceeded scenario
     */
    private void handleRateLimitExceeded(HttpServletResponse response, RateLimitResult result) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        
        // Add rate limit headers
        addRateLimitHeaders(response, result);
        
        // Add Retry-After header
        if (result.getRetryAfterSeconds() > 0) {
            response.setHeader("Retry-After", String.valueOf(result.getRetryAfterSeconds()));
        }
        
        // Write JSON error response
        String jsonResponse = String.format(
            "{\"error\":\"Rate limit exceeded\",\"message\":\"%s\",\"retryAfterSeconds\":%d}",
            result.getReason() != null ? result.getReason() : "Too many requests",
            result.getRetryAfterSeconds()
        );
        
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
        
        log.warn("RATE_LIMIT_EXCEEDED: {} - {}", result.getRateLimitKey(), result.getReason());
    }
    
    /**
     * Handle blocked IP scenario
     */
    private void handleBlockedIp(HttpServletResponse response, String clientIp) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json");
        
        String jsonResponse = String.format(
            "{\"error\":\"IP blocked\",\"message\":\"Your IP address has been temporarily blocked due to excessive requests\",\"clientIp\":\"%s\"}",
            clientIp
        );
        
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
        
        log.warn("BLOCKED_IP_ACCESS_ATTEMPT: {}", clientIp);
    }
    
    /**
     * Skip rate limiting for certain paths (health checks, static resources)
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        
        // Skip for health check endpoints
        if (path.startsWith("/actuator/health") || 
            path.startsWith("/health") ||
            path.startsWith("/ping")) {
            return true;
        }
        
        // Skip for static resources
        if (path.startsWith("/static/") || 
            path.startsWith("/css/") || 
            path.startsWith("/js/") || 
            path.startsWith("/images/") ||
            path.endsWith(".css") ||
            path.endsWith(".js") ||
            path.endsWith(".png") ||
            path.endsWith(".jpg") ||
            path.endsWith(".gif") ||
            path.endsWith(".ico")) {
            return true;
        }
        
        // Skip for WebSocket connections
        if ("websocket".equalsIgnoreCase(request.getHeader("Upgrade"))) {
            return true;
        }
        
        return false;
    }
}