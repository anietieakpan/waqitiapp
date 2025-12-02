package com.waqiti.common.multitenancy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Interceptor for handling multi-tenant context resolution
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantInterceptor implements HandlerInterceptor {

    private final TenantContextResolver tenantContextResolver;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            String tenantId = resolveTenantId(request);
            
            if (tenantId != null) {
                TenantContext.setCurrentTenant(tenantId);
                log.debug("Tenant context set to: {}", tenantId);
            } else {
                log.warn("No tenant ID found in request: {}", request.getRequestURI());
            }
            
            return true;
        } catch (Exception e) {
            log.error("Failed to set tenant context: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
        log.debug("Tenant context cleared");
    }

    private String resolveTenantId(HttpServletRequest request) {
        // Try header first
        String tenantId = request.getHeader("X-Tenant-ID");
        
        if (tenantId == null || tenantId.isEmpty()) {
            // Try subdomain extraction
            tenantId = extractTenantFromSubdomain(request);
        }
        
        if (tenantId == null || tenantId.isEmpty()) {
            // Try JWT token
            tenantId = tenantContextResolver.extractTenantFromToken(request);
        }
        
        return tenantId;
    }

    private String extractTenantFromSubdomain(HttpServletRequest request) {
        String serverName = request.getServerName();
        
        if (serverName != null && serverName.contains(".")) {
            String[] parts = serverName.split("\\.");
            if (parts.length >= 3) {
                return parts[0]; // First subdomain
            }
        }
        
        return null;
    }
}