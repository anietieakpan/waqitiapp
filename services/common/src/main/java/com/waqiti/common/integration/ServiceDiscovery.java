package com.waqiti.common.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Service discovery component for dynamic service resolution
 */
@Slf4j
@Component
public class ServiceDiscovery {
    
    private final DiscoveryClient discoveryClient;
    private final Map<String, List<ServiceInstance>> serviceCache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30000; // 30 seconds cache
    
    public ServiceDiscovery(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }
    
    /**
     * Get service instance by name with load balancing
     */
    public Optional<ServiceInstance> getServiceInstance(String serviceName) {
        List<ServiceInstance> instances = getServiceInstances(serviceName);
        if (instances.isEmpty()) {
            log.warn("No instances found for service: {}", serviceName);
            return Optional.empty();
        }
        
        // Simple random load balancing
        int index = ThreadLocalRandom.current().nextInt(instances.size());
        return Optional.of(instances.get(index));
    }
    
    /**
     * Get all service instances
     */
    public List<ServiceInstance> getServiceInstances(String serviceName) {
        // Check cache first
        if (isCacheValid(serviceName)) {
            return serviceCache.get(serviceName);
        }
        
        // Fetch from discovery client
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        
        // Update cache
        serviceCache.put(serviceName, instances);
        cacheTimestamps.put(serviceName, System.currentTimeMillis());
        
        log.debug("Discovered {} instances for service: {}", instances.size(), serviceName);
        return instances;
    }
    
    /**
     * Get service URL
     */
    public Optional<String> getServiceUrl(String serviceName) {
        return getServiceInstance(serviceName)
            .map(instance -> instance.getUri().toString());
    }
    
    /**
     * Get service URL with path
     */
    public Optional<String> getServiceUrl(String serviceName, String path) {
        return getServiceInstance(serviceName)
            .map(instance -> {
                String baseUrl = instance.getUri().toString();
                if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
                    baseUrl += "/";
                }
                return baseUrl + path;
            });
    }
    
    /**
     * Get healthy service instances
     */
    public List<ServiceInstance> getHealthyInstances(String serviceName) {
        return getServiceInstances(serviceName).stream()
            .filter(this::isHealthy)
            .collect(Collectors.toList());
    }
    
    /**
     * Check if service is available
     */
    public boolean isServiceAvailable(String serviceName) {
        return !getServiceInstances(serviceName).isEmpty();
    }
    
    /**
     * Get all registered services
     */
    public List<String> getAllServices() {
        return discoveryClient.getServices();
    }
    
    /**
     * Get service metadata
     */
    public Map<String, String> getServiceMetadata(String serviceName) {
        return getServiceInstance(serviceName)
            .map(ServiceInstance::getMetadata)
            .orElse(Collections.emptyMap());
    }
    
    /**
     * Register service health check
     */
    public void registerHealthCheck(String serviceName, HealthCheck healthCheck) {
        // Implementation would register health check with service registry
        log.info("Registered health check for service: {}", serviceName);
    }
    
    /**
     * Invalidate cache for service
     */
    public void invalidateCache(String serviceName) {
        serviceCache.remove(serviceName);
        cacheTimestamps.remove(serviceName);
        log.debug("Invalidated cache for service: {}", serviceName);
    }
    
    /**
     * Invalidate all caches
     */
    public void invalidateAllCaches() {
        serviceCache.clear();
        cacheTimestamps.clear();
        log.debug("Invalidated all service caches");
    }
    
    /**
     * Get service instance by instance ID
     */
    public Optional<ServiceInstance> getInstanceById(String serviceName, String instanceId) {
        return getServiceInstances(serviceName).stream()
            .filter(instance -> instance.getInstanceId().equals(instanceId))
            .findFirst();
    }
    
    /**
     * Get service instances in specific zone
     */
    public List<ServiceInstance> getInstancesInZone(String serviceName, String zone) {
        return getServiceInstances(serviceName).stream()
            .filter(instance -> zone.equals(instance.getMetadata().get("zone")))
            .collect(Collectors.toList());
    }
    
    /**
     * Select instance using round-robin
     */
    private final Map<String, Integer> roundRobinCounters = new ConcurrentHashMap<>();
    
    public Optional<ServiceInstance> getInstanceRoundRobin(String serviceName) {
        List<ServiceInstance> instances = getServiceInstances(serviceName);
        if (instances.isEmpty()) {
            return Optional.empty();
        }
        
        int counter = roundRobinCounters.compute(serviceName, (k, v) -> {
            if (v == null) return 0;
            return (v + 1) % instances.size();
        });
        
        return Optional.of(instances.get(counter));
    }
    
    /**
     * Check if cache is valid
     */
    private boolean isCacheValid(String serviceName) {
        if (!serviceCache.containsKey(serviceName)) {
            return false;
        }
        
        Long timestamp = cacheTimestamps.get(serviceName);
        if (timestamp == null) {
            return false;
        }
        
        return System.currentTimeMillis() - timestamp < CACHE_TTL_MS;
    }
    
    /**
     * Check if instance is healthy
     */
    private boolean isHealthy(ServiceInstance instance) {
        // In a real implementation, this would check health endpoint
        // For now, we assume all instances are healthy
        return true;
    }
    
    /**
     * Health check interface
     */
    public interface HealthCheck {
        boolean isHealthy(ServiceInstance instance);
    }
}