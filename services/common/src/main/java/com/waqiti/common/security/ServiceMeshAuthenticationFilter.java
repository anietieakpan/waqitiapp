package com.waqiti.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Service Mesh Authentication Filter
 * Validates service-to-service calls using mTLS headers and service identity
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ServiceMeshAuthenticationFilter extends OncePerRequestFilter {

    @Value("${security.service-mesh.enabled:true}")
    private boolean enabled;
    
    @Value("${security.service-mesh.header:X-Service-Identity}")
    private String serviceIdentityHeader;
    
    @Value("${security.service-mesh.cert-header:X-Client-Certificate}")
    private String clientCertHeader;
    
    @Value("${spring.application.name}")
    private String currentServiceName;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip for health checks and public endpoints
        String path = request.getRequestURI();
        if (isPublicEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Validate service identity
        String serviceIdentity = request.getHeader(serviceIdentityHeader);
        String clientCert = request.getHeader(clientCertHeader);
        
        if (!isValidServiceCall(serviceIdentity, clientCert, request)) {
            log.warn("Invalid service mesh authentication from {} to {}", 
                    serviceIdentity != null ? serviceIdentity : "unknown", path);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Service mesh authentication failed");
            return;
        }

        // Add service mesh context
        request.setAttribute("service.caller", serviceIdentity);
        request.setAttribute("service.authenticated", true);
        
        filterChain.doFilter(request, response);
    }

    private boolean isValidServiceCall(String serviceIdentity, String clientCert, HttpServletRequest request) {
        // In production with Istio, the service mesh handles mTLS
        // This filter validates additional service identity headers
        
        if (serviceIdentity == null || serviceIdentity.isEmpty()) {
            // Check if call is from API Gateway
            String gatewayHeader = request.getHeader("X-Gateway-Request");
            return "true".equals(gatewayHeader);
        }

        // Validate service identity format
        if (!serviceIdentity.matches("^[a-z0-9-]+$")) {
            return false;
        }

        // Validate client certificate if present
        if (clientCert != null && !clientCert.isEmpty()) {
            return validateCertificateFingerprint(clientCert, serviceIdentity);
        }

        // Additional validation based on known services
        return isKnownService(serviceIdentity);
    }

    private boolean validateCertificateFingerprint(String clientCert, String serviceIdentity) {
        try {
            // Decode certificate
            byte[] certBytes = Base64.getDecoder().decode(clientCert);
            
            // Calculate fingerprint
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] fingerprint = md.digest(certBytes);
            String fingerprintHex = bytesToHex(fingerprint);
            
            // In production, validate against known service certificates
            // For now, log for monitoring
            log.debug("Service {} certificate fingerprint: {}", serviceIdentity, fingerprintHex);
            
            return true;
        } catch (Exception e) {
            log.error("Failed to validate certificate for service {}", serviceIdentity, e);
            return false;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private boolean isKnownService(String serviceIdentity) {
        // List of known services in the mesh
        return serviceIdentity.matches("^(user|payment|wallet|notification|security|analytics|" +
                "ml|audit|compliance|ledger|transaction|account|core-banking|integration|" +
                "event-sourcing|saga-orchestration|api-gateway)-service$");
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/actuator/health") ||
               path.startsWith("/actuator/info") ||
               path.equals("/") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/swagger");
    }
}