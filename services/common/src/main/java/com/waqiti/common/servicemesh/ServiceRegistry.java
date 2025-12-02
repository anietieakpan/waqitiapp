package com.waqiti.common.servicemesh;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service Registry for Service Mesh
 * Manages service registration, discovery, and health monitoring
 */
@Slf4j
@Data
@Builder
@RequiredArgsConstructor
public class ServiceRegistry {

    private final DiscoveryClient discoveryClient;
    private final ServiceMeshProperties properties;

    @Builder.Default
    private final Duration healthCheckInterval = Duration.ofSeconds(30);
    @Builder.Default
    private final Duration deregistrationDelay = Duration.ofSeconds(10);
    @Builder.Default
    private final boolean autoRegister = true;

    @Builder.Default
    private final Map<String, ServiceInfo> registeredServices = new ConcurrentHashMap<>();
    @Builder.Default
    private final Map<String, HealthStatus> healthStatuses = new ConcurrentHashMap<>();
    @Builder.Default
    private final Map<String, Set<String>> serviceDependencies = new ConcurrentHashMap<>();
    @Builder.Default
    private final ScheduledExecutorService healthCheckExecutor = new ScheduledThreadPoolExecutor(5);

    @Builder.Default
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Initialize service registry
     */
    public void initialize() {
        if (initialized.compareAndSet(false, true)) {
            log.info("Initializing Service Registry");

            // Start health monitoring
            startHealthMonitoring();

            // Discover existing services
            discoverServices();

            log.info("Service Registry initialized with {} services", registeredServices.size());
        }
    }

    /**
     * Register a service
     */
    public void register(ServiceMeshManager.ServiceRegistration registration) {
        log.info("Registering service: {} version: {}", registration.getServiceName(), registration.getVersion());

        ServiceInfo serviceInfo = ServiceInfo.builder()
                .serviceName(registration.getServiceName())
                .version(registration.getVersion())
                .namespace(registration.getNamespace())
                .healthCheckPath(registration.getHealthCheckPath())
                .metadata(registration.getMetadata())
                .registrationTime(Instant.now())
                .lastHealthCheck(Instant.now())
                .healthy(true)
                .build();

        registeredServices.put(registration.getServiceName(), serviceInfo);

        // Initialize health status
        healthStatuses.put(registration.getServiceName(), HealthStatus.builder()
                .serviceName(registration.getServiceName())
                .healthy(true)
                .lastCheck(Instant.now())
                .consecutiveFailures(0)
                .build());

        log.info("Service {} registered successfully", registration.getServiceName());
    }

    /**
     * Deregister a service
     */
    public void deregister(String serviceName) {
        log.info("Deregistering service: {}", serviceName);

        // Wait for deregistration delay to allow graceful shutdown
        try {
            Thread.sleep(deregistrationDelay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Deregistration delay interrupted for service: {}", serviceName);
        }

        registeredServices.remove(serviceName);
        healthStatuses.remove(serviceName);
        serviceDependencies.remove(serviceName);

        log.info("Service {} deregistered successfully", serviceName);
    }

    /**
     * Check if service registry is healthy
     */
    public boolean isHealthy() {
        return initialized.get() && !registeredServices.isEmpty();
    }

    /**
     * Check health of a specific service
     */
    public boolean checkHealth(String serviceName) {
        HealthStatus status = healthStatuses.get(serviceName);

        if (status == null) {
            log.warn("Health check requested for unknown service: {}", serviceName);
            return false;
        }

        // Check if health check is stale
        Duration timeSinceLastCheck = Duration.between(status.getLastCheck(), Instant.now());
        if (timeSinceLastCheck.compareTo(healthCheckInterval.multipliedBy(3)) > 0) {
            log.warn("Health check is stale for service: {} (last check: {} ago)",
                    serviceName, timeSinceLastCheck);
            return false;
        }

        return status.isHealthy();
    }

    /**
     * Get all registered services
     */
    public Map<String, ServiceInfo> getRegisteredServices() {
        return new HashMap<>(registeredServices);
    }

    /**
     * Get service dependencies
     */
    public Set<String> getDependencies(String serviceName) {
        return serviceDependencies.getOrDefault(serviceName, new HashSet<>());
    }

    /**
     * Update service dependencies
     */
    public void updateDependencies(String serviceName, Set<String> dependencies) {
        log.debug("Updating dependencies for service: {} -> {}", serviceName, dependencies);
        serviceDependencies.put(serviceName, new HashSet<>(dependencies));
    }

    /**
     * Get service instances from discovery client
     */
    public List<ServiceInstance> getServiceInstances(String serviceName) {
        try {
            return discoveryClient.getInstances(serviceName);
        } catch (Exception e) {
            log.error("Failed to get instances for service: {}", serviceName, e);
            return Collections.emptyList();
        }
    }

    /**
     * Deregister a service (called by ServiceMeshManager)
     */
    public void deregisterService(String serviceName) {
        deregister(serviceName);
    }

    // Private helper methods

    private void startHealthMonitoring() {
        healthCheckExecutor.scheduleWithFixedDelay(() -> {
            registeredServices.keySet().forEach(serviceName -> {
                try {
                    performHealthCheck(serviceName);
                } catch (Exception e) {
                    log.error("Health check failed for service: {}", serviceName, e);
                    updateHealthStatus(serviceName, false, e.getMessage());
                }
            });
        }, healthCheckInterval.toMillis(), healthCheckInterval.toMillis(), TimeUnit.MILLISECONDS);

        log.info("Started health monitoring with interval: {}", healthCheckInterval);
    }

    private void performHealthCheck(String serviceName) {
        List<ServiceInstance> instances = getServiceInstances(serviceName);

        if (instances.isEmpty()) {
            updateHealthStatus(serviceName, false, "No instances available");
            return;
        }

        // Check if at least one instance is healthy
        boolean anyHealthy = instances.stream()
                .anyMatch(instance -> checkInstanceHealth(instance, serviceName));

        updateHealthStatus(serviceName, anyHealthy,
                anyHealthy ? "Service healthy" : "All instances unhealthy");
    }

    private boolean checkInstanceHealth(ServiceInstance instance, String serviceName) {
        // In a real implementation, this would make an HTTP call to the health endpoint
        // For now, we'll assume the instance is healthy if it's registered
        return instance != null;
    }

    private void updateHealthStatus(String serviceName, boolean healthy, String message) {
        HealthStatus currentStatus = healthStatuses.get(serviceName);

        if (currentStatus == null) {
            currentStatus = HealthStatus.builder()
                    .serviceName(serviceName)
                    .build();
        }

        int consecutiveFailures = healthy ? 0 : currentStatus.getConsecutiveFailures() + 1;

        HealthStatus newStatus = HealthStatus.builder()
                .serviceName(serviceName)
                .healthy(healthy)
                .lastCheck(Instant.now())
                .consecutiveFailures(consecutiveFailures)
                .message(message)
                .build();

        healthStatuses.put(serviceName, newStatus);

        // Log if service state changed
        if (currentStatus.isHealthy() != healthy) {
            if (healthy) {
                log.info("Service {} recovered: {}", serviceName, message);
            } else {
                log.warn("Service {} unhealthy: {} (consecutive failures: {})",
                        serviceName, message, consecutiveFailures);
            }
        }

        // Update service info
        ServiceInfo serviceInfo = registeredServices.get(serviceName);
        if (serviceInfo != null) {
            serviceInfo.setHealthy(healthy);
            serviceInfo.setLastHealthCheck(Instant.now());
        }
    }

    private void discoverServices() {
        try {
            List<String> services = discoveryClient.getServices();

            for (String serviceName : services) {
                if (!registeredServices.containsKey(serviceName)) {
                    // Auto-register discovered service
                    ServiceMeshManager.ServiceRegistration registration =
                            ServiceMeshManager.ServiceRegistration.builder()
                                    .serviceName(serviceName)
                                    .version("unknown")
                                    .namespace(properties.getNamespace())
                                    .healthCheckPath("/actuator/health")
                                    .build();

                    register(registration);
                }
            }

            log.info("Discovered {} services from discovery client", services.size());
        } catch (Exception e) {
            log.error("Failed to discover services", e);
        }
    }

    // Inner classes

    @Data
    @Builder
    public static class ServiceInfo {
        private String serviceName;
        private String version;
        private String namespace;
        private String healthCheckPath;
        private Map<String, String> metadata;
        private Instant registrationTime;
        private Instant lastHealthCheck;
        private boolean healthy;
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
}