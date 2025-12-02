package com.waqiti.common.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Service Request Interceptor
 * 
 * Intercepts outgoing HTTP requests to add standard headers,
 * correlation IDs, authentication tokens, and request metadata
 */
@Component
@Slf4j
public class ServiceRequestInterceptor implements ClientHttpRequestInterceptor {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String TIMESTAMP_HEADER = "X-Request-Timestamp";
    private static final String SOURCE_SERVICE_HEADER = "X-Source-Service";
    private static final String REQUEST_START_TIME_HEADER = "X-Request-Start-Time";

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, 
            byte[] body, 
            ClientHttpRequestExecution execution) throws IOException {
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Add correlation ID from MDC or generate new one
            addCorrelationId(request);
            
            // Add unique request ID
            addRequestId(request);
            
            // Add timestamp
            addTimestamp(request);
            
            // Add source service information
            addSourceService(request);
            
            // Add authentication token if available
            addAuthenticationToken(request);
            
            // Add request start time for performance monitoring
            request.getHeaders().add(REQUEST_START_TIME_HEADER, String.valueOf(startTime));
            
            // Log outgoing request
            logOutgoingRequest(request);
            
            // Execute request
            ClientHttpResponse response = execution.execute(request, body);
            
            // Log response and performance metrics
            logResponse(request, response, startTime);
            
            return response;
            
        } catch (Exception e) {
            log.error("Error in service request to {}: {}", request.getURI(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Add correlation ID to request headers
     */
    private void addCorrelationId(HttpRequest request) {
        String correlationId = getCurrentCorrelationId();
        if (correlationId != null) {
            request.getHeaders().add(CORRELATION_ID_HEADER, correlationId);
        } else {
            // Generate new correlation ID if none exists
            correlationId = UUID.randomUUID().toString();
            request.getHeaders().add(CORRELATION_ID_HEADER, correlationId);
            org.slf4j.MDC.put("correlationId", correlationId);
        }
    }

    /**
     * Add unique request ID
     */
    private void addRequestId(HttpRequest request) {
        String requestId = UUID.randomUUID().toString();
        request.getHeaders().add(REQUEST_ID_HEADER, requestId);
    }

    /**
     * Add request timestamp
     */
    private void addTimestamp(HttpRequest request) {
        request.getHeaders().add(TIMESTAMP_HEADER, Instant.now().toString());
    }

    /**
     * Add source service information
     */
    private void addSourceService(HttpRequest request) {
        String serviceName = getServiceName();
        if (serviceName != null) {
            request.getHeaders().add(SOURCE_SERVICE_HEADER, serviceName);
        }
    }

    /**
     * Add authentication token if available
     */
    private void addAuthenticationToken(HttpRequest request) {
        String authToken = getCurrentAuthToken();
        if (authToken != null && !authToken.trim().isEmpty()) {
            if (!authToken.startsWith("Bearer ")) {
                authToken = "Bearer " + authToken;
            }
            request.getHeaders().add("Authorization", authToken);
        }
    }

    /**
     * Log outgoing request details
     */
    private void logOutgoingRequest(HttpRequest request) {
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        String requestId = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        
        log.debug("Outgoing request: {} {} | Correlation: {} | Request: {} | Target: {}", 
            request.getMethod(), 
            request.getURI(), 
            correlationId, 
            requestId,
            extractTargetService(request.getURI().toString()));
    }

    /**
     * Log response and performance metrics
     */
    private void logResponse(HttpRequest request, ClientHttpResponse response, long startTime) {
        try {
            long duration = System.currentTimeMillis() - startTime;
            String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
            String requestId = request.getHeaders().getFirst(REQUEST_ID_HEADER);
            
            log.debug("Response received: {} {} | Status: {} | Duration: {}ms | Correlation: {} | Request: {}", 
                request.getMethod(), 
                request.getURI(), 
                response.getStatusCode(), 
                duration, 
                correlationId, 
                requestId);
            
            // Log performance warning for slow requests
            if (duration > 5000) { // 5 seconds
                log.warn("Slow service request detected: {} {} took {}ms | Correlation: {}", 
                    request.getMethod(), request.getURI(), duration, correlationId);
            }
            
            // Store metrics for monitoring
            storeRequestMetrics(request, response, duration);
            
        } catch (Exception e) {
            log.warn("Error logging response metrics", e);
        }
    }

    /**
     * Store request metrics for monitoring and analytics
     */
    private void storeRequestMetrics(HttpRequest request, ClientHttpResponse response, long duration) {
        try {
            String targetService = extractTargetService(request.getURI().toString());
            String method = request.getMethod().toString();
            String statusCode = response.getStatusCode().toString();
            
            // Implementation would store metrics in monitoring system
            // For example: Micrometer, Prometheus, CloudWatch, etc.
            
            log.trace("Storing request metrics: service={}, method={}, status={}, duration={}ms", 
                targetService, method, statusCode, duration);
            
        } catch (Exception e) {
            log.debug("Error storing request metrics", e);
        }
    }

    /**
     * Get current correlation ID from MDC
     */
    private String getCurrentCorrelationId() {
        return org.slf4j.MDC.get("correlationId");
    }

    /**
     * Get current authentication token from security context
     */
    private String getCurrentAuthToken() {
        try {
            org.springframework.security.core.Authentication authentication = 
                org.springframework.security.core.context.SecurityContextHolder
                    .getContext()
                    .getAuthentication();
            
            if (authentication != null && authentication.getCredentials() != null) {
                return authentication.getCredentials().toString();
            }
        } catch (Exception e) {
            log.debug("No authentication token found in security context");
        }
        return null;
    }

    /**
     * Get current service name from application properties
     */
    private String getServiceName() {
        // Try multiple sources for service name
        String serviceName = System.getProperty("spring.application.name");
        if (serviceName == null) {
            serviceName = System.getenv("SERVICE_NAME");
        }
        if (serviceName == null) {
            serviceName = "unknown-service";
        }
        return serviceName;
    }

    /**
     * Extract target service name from URL
     */
    private String extractTargetService(String url) {
        try {
            // Extract service name from URL patterns like:
            // http://user-service:8080/api/v1/users
            // http://localhost:8081/api/v1/wallets
            
            java.net.URI uri = java.net.URI.create(url);
            String host = uri.getHost();
            
            if (host != null) {
                // If host contains service name (e.g., user-service)
                if (host.contains("-service")) {
                    return host;
                }
                // If localhost, try to extract from port mapping
                if ("localhost".equals(host) || "127.0.0.1".equals(host)) {
                    return mapPortToService(uri.getPort());
                }
            }
            
            return "unknown-service";
            
        } catch (Exception e) {
            return "unknown-service";
        }
    }

    /**
     * Map port number to service name for local development
     */
    private String mapPortToService(int port) {
        // Common port mappings for local development
        switch (port) {
            case 8081: return "user-service";
            case 8082: return "wallet-service";
            case 8083: return "payment-service";
            case 8084: return "security-service";
            case 8085: return "analytics-service";
            case 8086: return "core-banking-service";
            case 8087: return "integration-service";
            case 8088: return "api-gateway";
            default: return "service-port-" + port;
        }
    }
}