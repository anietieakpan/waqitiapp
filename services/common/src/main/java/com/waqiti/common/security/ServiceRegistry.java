package com.waqiti.common.security;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service Registry for tracking service health and configuration
 * Used by the secret rotation service to coordinate rotations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceRegistry {
    
    private final DiscoveryClient discoveryClient;
    private final RestTemplate restTemplate;
    
    // Service tracking
    private final Map<String, ServiceInfo> services = new ConcurrentHashMap<>();
    private final Map<String, HealthStatus> healthStatus = new ConcurrentHashMap<>();
    private final ScheduledExecutorService healthChecker = Executors.newScheduledThreadPool(5);
    
    @Value("${service.health.check.interval.seconds:30}")
    private int healthCheckInterval;
    
    @Value("${service.health.check.timeout.seconds:10}")
    private int healthCheckTimeout;
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing Service Registry");
        
        // Discover and register all services
        discoverServices();
        
        // Start health monitoring
        startHealthMonitoring();
        
        log.info("Service Registry initialized with {} services", services.size());
    }
    
    /**
     * Register a service for monitoring
     */
    public void registerService(ServiceRegistration registration) {
        log.info("Registering service: {}", registration.getServiceName());
        
        ServiceInfo serviceInfo = ServiceInfo.builder()
            .serviceName(registration.getServiceName())
            .instances(registration.getInstances())
            .healthCheckEndpoint(registration.getHealthCheckEndpoint())
            .configurationEndpoint(registration.getConfigurationEndpoint())
            .dependsOn(registration.getDependsOn())
            .supportedRotationStrategies(registration.getSupportedRotationStrategies())
            .gracefulShutdownSupported(registration.isGracefulShutdownSupported())
            .registeredAt(Instant.now())
            .build();
        
        services.put(registration.getServiceName(), serviceInfo);
        
        // Initialize health status
        healthStatus.put(registration.getServiceName(), HealthStatus.builder()
            .serviceName(registration.getServiceName())
            .healthy(true)
            .lastCheck(Instant.now())
            .build());
    }
    
    /**
     * Check if a service is healthy
     */
    public boolean isHealthy(String serviceName) {
        HealthStatus status = healthStatus.get(serviceName);
        if (status == null) {
            log.warn("Unknown service: {}", serviceName);
            return false;
        }
        
        // Consider service unhealthy if we haven't checked recently
        if (Duration.between(status.getLastCheck(), Instant.now()).toMinutes() > 5) {
            log.warn("Stale health status for service: {}", serviceName);
            return false;
        }
        
        return status.isHealthy();
    }
    
    /**
     * Get all registered services
     */
    public List<String> getAll() {
        return new ArrayList<>(services.keySet());
    }
    
    /**
     * Get services that depend on a given service
     */
    public List<String> getDependentServices(String serviceName) {
        return services.values().stream()
            .filter(service -> service.getDependsOn().contains(serviceName))
            .map(ServiceInfo::getServiceName)
            .collect(Collectors.toList());
    }
    
    /**
     * Get service dependency order for rotation planning
     */
    public Map<Integer, List<String>> getServicesByDependencyOrder() {
        Map<Integer, List<String>> dependencyLevels = new HashMap<>();
        Set<String> processed = new HashSet<>();
        
        // Calculate dependency levels
        for (String serviceName : services.keySet()) {
            if (!processed.contains(serviceName)) {
                int level = calculateDependencyLevel(serviceName, processed, new HashSet<>());
                dependencyLevels.computeIfAbsent(level, k -> new ArrayList<>()).add(serviceName);
                processed.add(serviceName);
            }
        }
        
        return dependencyLevels;
    }
    
    /**
     * Update service configuration (used during secret rotation)
     */
    public void updateServiceConfiguration(String serviceName, Object newConfiguration) {
        ServiceInfo service = services.get(serviceName);
        if (service == null) {
            log.warn("Attempted to update configuration for unknown service: {}", serviceName);
            return;
        }
        
        try {
            String configEndpoint = service.getConfigurationEndpoint();
            if (configEndpoint != null) {
                // Get random healthy instance
                ServiceInstance instance = getHealthyInstance(serviceName);
                if (instance != null) {
                    String url = String.format("http://%s:%d%s", 
                        instance.getHost(), instance.getPort(), configEndpoint);
                    
                    restTemplate.postForEntity(url, newConfiguration, String.class);
                    log.info("Updated configuration for service: {}", serviceName);
                }
            }
        } catch (Exception e) {
            log.error("Failed to update configuration for service: {}", serviceName, e);
            throw new ServiceRegistryException("Configuration update failed", e);
        }
    }
    
    /**
     * Trigger graceful restart of service instances
     */
    public void gracefulRestart(String serviceName) {
        ServiceInfo service = services.get(serviceName);
        if (service == null || !service.isGracefulShutdownSupported()) {
            throw new ServiceRegistryException("Graceful restart not supported for: " + serviceName);
        }
        
        try {
            List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
            
            for (ServiceInstance instance : instances) {
                String url = String.format("http://%s:%d/actuator/shutdown", 
                    instance.getHost(), instance.getPort());
                
                restTemplate.postForEntity(url, null, String.class);
                log.info("Triggered graceful shutdown for instance: {}:{}", 
                    instance.getHost(), instance.getPort());
                
                // Wait before restarting next instance
                Thread.sleep(30000); // 30 seconds
            }
        } catch (Exception e) {
            log.error("Graceful restart failed for service: {}", serviceName, e);
            throw new ServiceRegistryException("Graceful restart failed", e);
        }
    }
    
    /**
     * Perform health check on specific service
     */
    public HealthCheckResult performHealthCheck(String serviceName) {
        ServiceInfo service = services.get(serviceName);
        if (service == null) {
            return HealthCheckResult.builder()
                .serviceName(serviceName)
                .healthy(false)
                .message("Service not registered")
                .build();
        }
        
        try {
            List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
            if (instances.isEmpty()) {
                return HealthCheckResult.builder()
                    .serviceName(serviceName)
                    .healthy(false)
                    .message("No instances found")
                    .checkedAt(Instant.now())
                    .build();
            }
            
            int healthyInstances = 0;
            List<String> errors = new ArrayList<>();
            
            for (ServiceInstance instance : instances) {
                try {
                    String healthUrl = String.format("http://%s:%d%s", 
                        instance.getHost(), instance.getPort(), 
                        service.getHealthCheckEndpoint());
                    
                    // Perform health check with timeout
                    restTemplate.getForEntity(healthUrl, String.class);
                    healthyInstances++;
                    
                } catch (Exception e) {
                    errors.add(String.format("%s:%d - %s", 
                        instance.getHost(), instance.getPort(), e.getMessage()));
                }
            }
            
            boolean isHealthy = healthyInstances > 0;
            
            return HealthCheckResult.builder()
                .serviceName(serviceName)
                .healthy(isHealthy)
                .totalInstances(instances.size())
                .healthyInstances(healthyInstances)
                .message(isHealthy ? "Healthy" : "All instances unhealthy")
                .errors(errors)
                .checkedAt(Instant.now())
                .build();
            
        } catch (Exception e) {
            log.error("Health check failed for service: {}", serviceName, e);
            return HealthCheckResult.builder()
                .serviceName(serviceName)
                .healthy(false)
                .message("Health check failed: " + e.getMessage())
                .checkedAt(Instant.now())
                .build();
        }
    }
    
    /**
     * Get service registry status report
     */
    public RegistryStatusReport getStatusReport() {
        List<ServiceStatus> serviceStatuses = services.values().stream()
            .map(service -> {
                HealthStatus health = healthStatus.get(service.getServiceName());
                List<ServiceInstance> instances = discoveryClient.getInstances(service.getServiceName());
                
                return ServiceStatus.builder()
                    .serviceName(service.getServiceName())
                    .healthy(health != null && health.isHealthy())
                    .instanceCount(instances.size())
                    .lastHealthCheck(health != null ? health.getLastCheck() : null)
                    .registeredAt(service.getRegisteredAt())
                    .build();
            })
            .collect(Collectors.toList());
        
        long healthyServices = serviceStatuses.stream()
            .mapToLong(status -> status.isHealthy() ? 1 : 0)
            .sum();
        
        return RegistryStatusReport.builder()
            .reportTimestamp(Instant.now())
            .totalServices(services.size())
            .healthyServices((int) healthyServices)
            .serviceStatuses(serviceStatuses)
            .overallHealth((double) healthyServices / services.size())
            .build();
    }
    
    // Private helper methods
    
    private void discoverServices() {
        // Discover services from Spring Cloud Discovery Client
        List<String> serviceNames = discoveryClient.getServices();
        
        for (String serviceName : serviceNames) {
            if (!services.containsKey(serviceName)) {
                // Auto-register discovered services with default configuration
                registerDefaultService(serviceName);
            }
        }
    }
    
    private void registerDefaultService(String serviceName) {
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        
        ServiceRegistration registration = ServiceRegistration.builder()
            .serviceName(serviceName)
            .instances(instances)
            .healthCheckEndpoint("/actuator/health")
            .configurationEndpoint("/actuator/refresh")
            .dependsOn(List.of())
            .supportedRotationStrategies(List.of("IMMEDIATE", "BLUE_GREEN"))
            .gracefulShutdownSupported(true)
            .build();
        
        registerService(registration);
    }
    
    private void startHealthMonitoring() {
        healthChecker.scheduleAtFixedRate(() -> {
            for (String serviceName : services.keySet()) {
                try {
                    HealthCheckResult result = performHealthCheck(serviceName);
                    
                    HealthStatus status = HealthStatus.builder()
                        .serviceName(serviceName)
                        .healthy(result.isHealthy())
                        .lastCheck(Instant.now())
                        .consecutiveFailures(result.isHealthy() ? 0 : 
                            healthStatus.getOrDefault(serviceName, HealthStatus.builder().build())
                                .getConsecutiveFailures() + 1)
                        .message(result.getMessage())
                        .build();
                    
                    healthStatus.put(serviceName, status);
                    
                    // Alert on service failures
                    if (!result.isHealthy() && status.getConsecutiveFailures() >= 3) {
                        log.error("Service {} has failed health checks {} times consecutively", 
                            serviceName, status.getConsecutiveFailures());
                    }
                    
                } catch (Exception e) {
                    log.error("Health monitoring failed for service: {}", serviceName, e);
                }
            }
        }, 30, healthCheckInterval, TimeUnit.SECONDS);
    }
    
    private int calculateDependencyLevel(String serviceName, Set<String> processed, Set<String> visiting) {
        if (visiting.contains(serviceName)) {
            log.warn("Circular dependency detected involving service: {}", serviceName);
            return 0; // Break circular dependency
        }
        
        ServiceInfo service = services.get(serviceName);
        if (service == null || service.getDependsOn().isEmpty()) {
            return 0; // No dependencies
        }
        
        visiting.add(serviceName);
        
        int maxDependencyLevel = 0;
        for (String dependency : service.getDependsOn()) {
            if (!processed.contains(dependency)) {
                int dependencyLevel = calculateDependencyLevel(dependency, processed, visiting);
                maxDependencyLevel = Math.max(maxDependencyLevel, dependencyLevel);
            }
        }
        
        visiting.remove(serviceName);
        return maxDependencyLevel + 1;
    }
    
    private ServiceInstance getHealthyInstance(String serviceName) {
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        
        // For now, return first instance
        // In production, implement proper load balancing
        return instances.isEmpty() ? null : instances.get(0);
    }
    
    // Inner classes
    
    @Data
    @Builder
    public static class ServiceRegistration {
        private String serviceName;
        private List<ServiceInstance> instances;
        private String healthCheckEndpoint;
        private String configurationEndpoint;
        private List<String> dependsOn;
        private List<String> supportedRotationStrategies;
        private boolean gracefulShutdownSupported;
    }
    
    @Data
    @Builder
    public static class ServiceInfo {
        private String serviceName;
        private List<ServiceInstance> instances;
        private String healthCheckEndpoint;
        private String configurationEndpoint;
        private List<String> dependsOn;
        private List<String> supportedRotationStrategies;
        private boolean gracefulShutdownSupported;
        private Instant registeredAt;
    }
    
    @Data
    @Builder
    public static class HealthStatus {
        private String serviceName;
        private boolean healthy;
        private Instant lastCheck;
        private int consecutiveFailures;
        private String message;
    }
    
    @Data
    @Builder
    public static class HealthCheckResult {
        private String serviceName;
        private boolean healthy;
        private int totalInstances;
        private int healthyInstances;
        private String message;
        private List<String> errors;
        private Instant checkedAt;
    }
    
    @Data
    @Builder
    public static class ServiceStatus {
        private String serviceName;
        private boolean healthy;
        private int instanceCount;
        private Instant lastHealthCheck;
        private Instant registeredAt;
    }
    
    @Data
    @Builder
    public static class RegistryStatusReport {
        private Instant reportTimestamp;
        private int totalServices;
        private int healthyServices;
        private List<ServiceStatus> serviceStatuses;
        private double overallHealth;
    }
    
    public static class ServiceRegistryException extends RuntimeException {
        public ServiceRegistryException(String message) {
            super(message);
        }
        
        public ServiceRegistryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}