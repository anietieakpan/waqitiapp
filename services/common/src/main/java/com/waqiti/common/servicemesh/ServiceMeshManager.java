package com.waqiti.common.servicemesh;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central Service Mesh Manager
 * Orchestrates all service mesh components and provides unified management
 */
@Slf4j
@Builder
@RequiredArgsConstructor
public class ServiceMeshManager {

    private final ServiceMeshProperties properties;
    private final ServiceRegistry serviceRegistry;
    private final TrafficManager trafficManager;
    private final SecurityManager securityManager;
    private final ObservabilityManager observabilityManager;
    private final MeterRegistry meterRegistry;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Map<String, ServiceMeshStatus> serviceStatuses = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastHealthCheck = new ConcurrentHashMap<>();
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);

    @PostConstruct
    public void initialize() {
        if (initialized.compareAndSet(false, true)) {
            log.info("Initializing Service Mesh Manager for service: {}", properties.getServiceName());
            
            // Initialize components
            initializeServiceRegistry();
            initializeTrafficManagement();
            initializeSecurity();
            initializeObservability();
            
            // Register metrics
            registerMetrics();
            
            log.info("Service Mesh Manager initialized successfully");
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Service Mesh Manager");
        
        // Graceful shutdown of components
        try {
            serviceRegistry.deregisterService(properties.getServiceName());
            trafficManager.shutdown();
            observabilityManager.flush();
        } catch (Exception e) {
            log.error("Error during Service Mesh Manager shutdown", e);
        }
    }

    /**
     * Get current service mesh status
     */
    public ServiceMeshStatus getStatus() {
        return ServiceMeshStatus.builder()
                .serviceName(properties.getServiceName())
                .version(properties.getVersion())
                .namespace(properties.getNamespace())
                .meshMode(properties.getMode().toString())
                .healthy(isHealthy())
                .mtlsEnabled(properties.getSecurity().isMtlsEnabled())
                .tracingEnabled(properties.getObservability().isTracingEnabled())
                .totalRequests(totalRequests.get())
                .failedRequests(failedRequests.get())
                .successRate(calculateSuccessRate())
                .registeredServices(serviceRegistry.getRegisteredServices().size())
                .activeConnections(trafficManager.getActiveConnections())
                .lastUpdated(Instant.now())
                .build();
    }

    /**
     * Check if service mesh is healthy
     */
    public boolean isHealthy() {
        boolean registryHealthy = serviceRegistry.isHealthy();
        boolean trafficHealthy = trafficManager.isHealthy();
        boolean securityHealthy = securityManager.isHealthy();
        
        return registryHealthy && trafficHealthy && securityHealthy;
    }

    /**
     * Register a service in the mesh
     */
    public void registerService(ServiceRegistration registration) {
        log.info("Registering service in mesh: {}", registration.getServiceName());
        
        serviceRegistry.register(registration);
        
        // Configure traffic policies
        trafficManager.configureRouting(registration.getServiceName(), registration.getTrafficPolicy());
        
        // Configure security policies
        if (properties.getSecurity().isMtlsEnabled()) {
            securityManager.configureMTLS(registration.getServiceName());
        }
        
        // Setup observability
        observabilityManager.configureTracing(registration.getServiceName());
        
        // Update status
        serviceStatuses.put(registration.getServiceName(), ServiceMeshStatus.builder()
                .serviceName(registration.getServiceName())
                .healthy(true)
                .registrationTime(Instant.now())
                .build());
    }

    /**
     * Deregister a service from the mesh
     */
    public void deregisterService(String serviceName) {
        log.info("Deregistering service from mesh: {}", serviceName);
        
        serviceRegistry.deregister(serviceName);
        trafficManager.removeRouting(serviceName);
        securityManager.removePolicies(serviceName);
        observabilityManager.removeTracing(serviceName);
        serviceStatuses.remove(serviceName);
    }

    /**
     * Update traffic policy for a service
     */
    public void updateTrafficPolicy(String serviceName, TrafficPolicy policy) {
        log.info("Updating traffic policy for service: {}", serviceName);
        trafficManager.updatePolicy(serviceName, policy);
    }

    /**
     * Enable canary deployment
     */
    public void enableCanaryDeployment(String serviceName, CanaryConfig config) {
        log.info("Enabling canary deployment for service: {} with {}% traffic", 
                serviceName, config.getPercentage());
        
        trafficManager.enableCanary(serviceName, config);
        
        // Track canary metrics
        meterRegistry.gauge("service.mesh.canary.percentage", 
                Tags.of("service", serviceName), config.getPercentage());
    }

    /**
     * Perform health check on all services
     */
    @Scheduled(fixedDelayString = "${waqiti.service-mesh.health-check.interval:30000}")
    public void performHealthChecks() {
        serviceStatuses.keySet().forEach(serviceName -> {
            try {
                boolean healthy = serviceRegistry.checkHealth(serviceName);
                
                ServiceMeshStatus status = serviceStatuses.get(serviceName);
                if (status != null) {
                    status.setHealthy(healthy);
                    status.setLastHealthCheck(Instant.now());
                }
                
                lastHealthCheck.put(serviceName, Instant.now());
                
                // Record metrics
                meterRegistry.gauge("service.mesh.health", 
                        Tags.of("service", serviceName), healthy ? 1 : 0);
                
            } catch (Exception e) {
                log.error("Health check failed for service: {}", serviceName, e);
            }
        });
    }

    /**
     * Get service mesh metrics
     */
    public ServiceMeshMetrics getMetrics() {
        return ServiceMeshMetrics.builder()
                .totalServices(serviceStatuses.size())
                .healthyServices(countHealthyServices())
                .totalRequests(totalRequests.get())
                .failedRequests(failedRequests.get())
                .successRate(calculateSuccessRate())
                .averageLatency(trafficManager.getAverageLatency())
                .p95Latency(trafficManager.getP95Latency())
                .p99Latency(trafficManager.getP99Latency())
                .activeConnections(trafficManager.getActiveConnections())
                .circuitBreakerOpenCount(trafficManager.getCircuitBreakerOpenCount())
                .retryCount(trafficManager.getRetryCount())
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Apply service mesh configuration
     */
    public void applyConfiguration(ServiceMeshConfiguration config) {
        log.info("Applying service mesh configuration");
        
        // Update traffic configuration
        if (config.getTrafficConfig() != null) {
            trafficManager.applyConfiguration(config.getTrafficConfig());
        }
        
        // Update security configuration
        if (config.getSecurityConfig() != null) {
            securityManager.applyConfiguration(config.getSecurityConfig());
        }
        
        // Update observability configuration
        if (config.getObservabilityConfig() != null) {
            observabilityManager.applyConfiguration(config.getObservabilityConfig());
        }
    }

    /**
     * Trigger fault injection for chaos engineering
     */
    public void triggerFaultInjection(FaultInjectionRequest request) {
        if (!properties.getFaultInjection().isEnabled()) {
            throw new IllegalStateException("Fault injection is not enabled");
        }
        
        log.warn("Triggering fault injection for service: {} - Type: {}", 
                request.getServiceName(), request.getType());
        
        trafficManager.injectFault(request);
        
        // Record fault injection event
        meterRegistry.counter("service.mesh.fault.injection", 
                Tags.of("service", request.getServiceName(), 
                        "type", request.getType().toString())).increment();
    }

    /**
     * Get service dependencies
     */
    public ServiceDependencyGraph getDependencyGraph() {
        Map<String, Set<String>> dependencies = new HashMap<>();
        
        serviceStatuses.keySet().forEach(service -> {
            Set<String> deps = serviceRegistry.getDependencies(service);
            dependencies.put(service, deps);
        });
        
        return ServiceDependencyGraph.builder()
                .services(new HashSet<>(serviceStatuses.keySet()))
                .dependencies(dependencies)
                .generatedAt(Instant.now())
                .build();
    }

    // Private helper methods

    private void initializeServiceRegistry() {
        ServiceRegistration selfRegistration = ServiceRegistration.builder()
                .serviceName(properties.getServiceName())
                .version(properties.getVersion())
                .namespace(properties.getNamespace())
                .healthCheckPath(properties.getHealthCheck().getPath())
                .build();
        
        serviceRegistry.register(selfRegistration);
    }

    private void initializeTrafficManagement() {
        trafficManager.initialize(properties.getTraffic());
    }

    private void initializeSecurity() {
        if (properties.getSecurity().isMtlsEnabled()) {
            securityManager.enableMTLS();
        }
        if (properties.getSecurity().isAuthorizationEnabled()) {
            securityManager.enableAuthorization();
        }
    }

    private void initializeObservability() {
        observabilityManager.initialize(properties.getObservability());
    }

    private void registerMetrics() {
        // Register service mesh metrics
        meterRegistry.gauge("service.mesh.status", this, manager -> manager.isHealthy() ? 1 : 0);
        meterRegistry.gauge("service.mesh.services.total", serviceStatuses, Map::size);
        meterRegistry.gauge("service.mesh.requests.total", totalRequests, AtomicLong::get);
        meterRegistry.gauge("service.mesh.requests.failed", failedRequests, AtomicLong::get);
    }

    private long countHealthyServices() {
        return serviceStatuses.values().stream()
                .filter(ServiceMeshStatus::isHealthy)
                .count();
    }

    private double calculateSuccessRate() {
        long total = totalRequests.get();
        if (total == 0) return 100.0;
        
        long successful = total - failedRequests.get();
        return (successful * 100.0) / total;
    }

    // Inner classes

    @lombok.Data
    @lombok.Builder
    public static class ServiceMeshStatus {
        private String serviceName;
        private String version;
        private String namespace;
        private String meshMode;
        private boolean healthy;
        private boolean mtlsEnabled;
        private boolean tracingEnabled;
        private long totalRequests;
        private long failedRequests;
        private double successRate;
        private int registeredServices;
        private int activeConnections;
        private Instant registrationTime;
        private Instant lastHealthCheck;
        private Instant lastUpdated;
    }

    @lombok.Data
    @lombok.Builder
    public static class ServiceRegistration {
        private String serviceName;
        private String version;
        private String namespace;
        private String healthCheckPath;
        private TrafficPolicy trafficPolicy;
        private SecurityPolicy securityPolicy;
        private Map<String, String> metadata;
    }

    @lombok.Data
    @lombok.Builder
    public static class ServiceMeshConfiguration {
        private TrafficConfiguration trafficConfig;
        private SecurityConfiguration securityConfig;
        private ObservabilityConfiguration observabilityConfig;
    }

    @lombok.Data
    @lombok.Builder
    public static class ServiceMeshMetrics {
        private int totalServices;
        private long healthyServices;
        private long totalRequests;
        private long failedRequests;
        private double successRate;
        private double averageLatency;
        private double p95Latency;
        private double p99Latency;
        private int activeConnections;
        private int circuitBreakerOpenCount;
        private long retryCount;
        private Instant timestamp;
    }

    @lombok.Data
    @lombok.Builder
    public static class FaultInjectionRequest {
        private String serviceName;
        private FaultType type;
        private int percentage;
        private Duration duration;
        private int httpStatus;
        
        public enum FaultType {
            DELAY,
            ABORT,
            TIMEOUT,
            NETWORK_PARTITION
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class ServiceDependencyGraph {
        private Set<String> services;
        private Map<String, Set<String>> dependencies;
        private Instant generatedAt;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class TrafficPolicy {
        private String loadBalancingStrategy;
        private int timeoutMs;
        private int maxRetries;
        private boolean circuitBreakerEnabled;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class SecurityPolicy {
        private boolean mtlsRequired;
        private boolean authorizationRequired;
        private List<String> allowedClients;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class CanaryConfig {
        private int percentage;
        private String version;
        private Map<String, String> headers;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class TrafficConfiguration {
        private Map<String, Object> settings;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class SecurityConfiguration {
        private Map<String, Object> settings;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class ObservabilityConfiguration {
        private Map<String, Object> settings;
    }
}