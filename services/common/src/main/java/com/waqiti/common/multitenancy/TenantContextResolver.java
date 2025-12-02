package com.waqiti.common.multitenancy;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

/**
 * Resolves tenant context from various sources
 */
@Slf4j
@Component
public class TenantContextResolver {
    
    private static final String TENANT_ID_CLAIM = "tenant_id";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    
    /**
     * Extract tenant ID from JWT token
     */
    public String extractTenantFromToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        
        try {
            String token = authHeader.substring(BEARER_PREFIX.length());
            
            // Parse the JWT token (without validation here since it's just for tenant extraction)
            // The actual validation happens in the security filter
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            
            // Decode the payload
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            
            // Simple JSON parsing to extract tenant_id
            if (payload.contains("\"" + TENANT_ID_CLAIM + "\"")) {
                int startIndex = payload.indexOf("\"" + TENANT_ID_CLAIM + "\"");
                if (startIndex != -1) {
                    int valueStart = payload.indexOf(":", startIndex) + 1;
                    int valueEnd = payload.indexOf(",", valueStart);
                    if (valueEnd == -1) {
                        valueEnd = payload.indexOf("}", valueStart);
                    }
                    if (valueStart != -1 && valueEnd != -1) {
                        String value = payload.substring(valueStart, valueEnd).trim();
                        // Remove quotes if present
                        value = value.replaceAll("\"", "");
                        return value.isEmpty() ? null : value;
                    }
                }
            }
            
            return null;
        } catch (Exception e) {
            log.debug("Failed to extract tenant from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract tenant ID from request header
     */
    public String extractTenantFromHeader(HttpServletRequest request, String headerName) {
        return request.getHeader(headerName);
    }
    
    /**
     * Extract tenant ID from subdomain
     */
    public String extractTenantFromSubdomain(HttpServletRequest request) {
        String host = request.getServerName();
        
        if (host != null && host.contains(".")) {
            String[] parts = host.split("\\.");
            if (parts.length >= 3) {
                // Return the first part as tenant ID (e.g., "tenant1" from "tenant1.app.com")
                return parts[0];
            }
        }
        
        return null;
    }
    
    /**
     * Extract tenant ID from request parameter
     */
    public String extractTenantFromParameter(HttpServletRequest request, String paramName) {
        return request.getParameter(paramName);
    }
}