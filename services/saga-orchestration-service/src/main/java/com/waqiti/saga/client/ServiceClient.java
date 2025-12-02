package com.waqiti.saga.client;

import com.waqiti.common.security.SecureServiceCommunication;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service client for secure inter-service communication
 * Handles service discovery and load balancing
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceClient {
    
    private final DiscoveryClient discoveryClient;
    
    @Qualifier("secureRestTemplate")
    private final RestTemplate secureRestTemplate;
    
    // Cache service URLs for performance
    private final Map<String, String> serviceUrlCache = new ConcurrentHashMap<>();
    
    // Service name to URL mappings (fallback for when discovery is not available)
    private static final Map<String, String> SERVICE_URLS = Map.of(
        "wallet-service", "https://wallet-service:8443",
        "payment-service", "https://payment-service:8443",
        "notification-service", "https://notification-service:8443",
        "fraud-service", "https://fraud-service:8443",
        "user-service", "https://user-service:8443",
        "compliance-service", "https://compliance-service:8443",
        "analytics-service", "https://analytics-service:8443"
    );
    
    /**
     * Resolve service URL using service discovery or fallback
     */
    public String resolveServiceUrl(String serviceName) {
        // Check cache first
        String cachedUrl = serviceUrlCache.get(serviceName);
        if (cachedUrl != null) {
            return cachedUrl;
        }
        
        try {
            // Try service discovery
            List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
            
            if (!instances.isEmpty()) {
                // Simple round-robin (in production, use proper load balancer)
                ServiceInstance instance = instances.get(0);
                String url = instance.isSecure() ? 
                    "https://" + instance.getHost() + ":" + instance.getPort() :
                    "http://" + instance.getHost() + ":" + instance.getPort();
                
                serviceUrlCache.put(serviceName, url);
                log.debug("Resolved service URL via discovery: {} -> {}", serviceName, url);
                return url;
            }
            
        } catch (Exception e) {
            log.warn("Service discovery failed for {}, using fallback", serviceName, e);
        }
        
        // Fallback to configured URLs
        String fallbackUrl = SERVICE_URLS.get(serviceName);
        if (fallbackUrl != null) {
            serviceUrlCache.put(serviceName, fallbackUrl);
            log.debug("Using fallback URL for service: {} -> {}", serviceName, fallbackUrl);
            return fallbackUrl;
        }
        
        throw new ServiceNotFoundException("Service not found: " + serviceName);
    }
    
    /**
     * Get secure RestTemplate for service calls
     */
    public RestTemplate getSecureRestTemplate() {
        return secureRestTemplate;
    }
    
    /**
     * Clear service URL cache
     */
    public void clearCache() {
        serviceUrlCache.clear();
        log.info("Service URL cache cleared");
    }
    
    /**
     * Check if service is healthy
     */
    public boolean isServiceHealthy(String serviceName) {
        try {
            String serviceUrl = resolveServiceUrl(serviceName);
            String healthUrl = serviceUrl + "/actuator/health";
            
            Map<String, Object> health = secureRestTemplate.getForObject(healthUrl, Map.class);
            return health != null && "UP".equals(health.get("status"));
            
        } catch (Exception e) {
            log.warn("Health check failed for service: {}", serviceName, e);
            return false;
        }
    }
    
    public static class ServiceNotFoundException extends RuntimeException {
        public ServiceNotFoundException(String message) {
            super(message);
        }
    }
}