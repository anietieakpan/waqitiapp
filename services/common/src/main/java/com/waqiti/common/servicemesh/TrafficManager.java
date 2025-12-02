package com.waqiti.common.servicemesh;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.support.RetryTemplate;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Traffic Manager for Service Mesh
 * Handles load balancing, circuit breaking, retries, and traffic shaping
 */
@Slf4j
@Data
@Builder
@RequiredArgsConstructor
public class TrafficManager {

    private final ServiceMeshProperties.LoadBalancingStrategy loadBalancingStrategy;
    private final RetryTemplate retryPolicy;
    @Builder.Default
    private final int circuitBreakerThreshold = 50;
    private final Duration timeoutDuration;
    @Builder.Default
    private final boolean canaryEnabled = false;
    @Builder.Default
    private final int canaryPercentage = 10;
    private final MeterRegistry meterRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    @Builder.Default
    private final boolean metricsEnabled = true;

    private final Map<String, List<ServiceInstance>> serviceInstances = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final Map<String, Retry> retryPolicies = new ConcurrentHashMap<>();
    private final Map<String, TrafficPolicy> trafficPolicies = new ConcurrentHashMap<>();
    private final Map<String, CanaryDeployment> canaryDeployments = new ConcurrentHashMap<>();
    
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicLong totalRetries = new AtomicLong(0);
    private final Map<String, LatencyStats> latencyStats = new ConcurrentHashMap<>();
    @Builder.Default
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Initialize traffic manager with configuration
     */
    public void initialize(ServiceMeshProperties.TrafficConfig config) {
        if (initialized.compareAndSet(false, true)) {
            log.info("Initializing Traffic Manager with strategy: {}", loadBalancingStrategy);
            
            // Circuit breaker and retry registries are already initialized via constructor/builder
            // Additional configuration can be applied here if needed
            
            log.info("Traffic Manager initialized successfully");
        }
    }

    /**
     * Configure routing for a service
     */
    public void configureRouting(String serviceName, ServiceMeshManager.TrafficPolicy policy) {
        log.debug("Configuring routing for service: {}", serviceName);
        
        if (policy != null) {
            trafficPolicies.put(serviceName, TrafficPolicy.builder()
                    .serviceName(serviceName)
                    .loadBalancingStrategy(policy.getLoadBalancingStrategy())
                    .timeoutMs(policy.getTimeoutMs())
                    .maxRetries(policy.getMaxRetries())
                    .circuitBreakerEnabled(policy.isCircuitBreakerEnabled())
                    .build());
            
            // Create circuit breaker for the service
            if (policy.isCircuitBreakerEnabled()) {
                CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(serviceName);
                circuitBreakers.put(serviceName, circuitBreaker);
                
                // Add event listeners
                circuitBreaker.getEventPublisher()
                        .onStateTransition(event -> 
                                log.warn("Circuit breaker state transition for {}: {} -> {}", 
                                        serviceName, event.getStateTransition().getFromState(), 
                                        event.getStateTransition().getToState()));
            }
            
            // Create retry policy for the service
            Retry retry = retryRegistry.retry(serviceName);
            retryPolicies.put(serviceName, retry);
        }
    }

    /**
     * Route request to appropriate service instance
     */
    public ServiceInstance route(String serviceName, RequestContext context) {
        List<ServiceInstance> instances = serviceInstances.get(serviceName);
        
        if (instances == null || instances.isEmpty()) {
            throw new NoAvailableInstanceException("No available instances for service: " + serviceName);
        }
        
        // Filter healthy instances
        List<ServiceInstance> healthyInstances = instances.stream()
                .filter(ServiceInstance::isHealthy)
                .collect(Collectors.toList());
        
        if (healthyInstances.isEmpty()) {
            throw new NoAvailableInstanceException("No healthy instances for service: " + serviceName);
        }
        
        // Check for canary deployment
        if (canaryDeployments.containsKey(serviceName)) {
            ServiceInstance canaryInstance = routeCanary(serviceName, healthyInstances, context);
            if (canaryInstance != null) {
                return canaryInstance;
            }
        }
        
        // Apply load balancing strategy
        ServiceInstance selected = selectInstance(serviceName, healthyInstances, context);
        
        // Record metrics
        recordRoutingMetrics(serviceName, selected);
        
        return selected;
    }

    /**
     * Execute request with circuit breaker and retry
     */
    public <T> T executeWithResilience(String serviceName, Supplier<T> supplier) {
        activeConnections.incrementAndGet();
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            CircuitBreaker circuitBreaker = circuitBreakers.get(serviceName);
            Retry retry = retryPolicies.get(serviceName);
            
            Supplier<T> decoratedSupplier = supplier;
            
            // Apply circuit breaker if configured
            if (circuitBreaker != null) {
                decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, decoratedSupplier);
            }
            
            // Apply retry if configured
            if (retry != null) {
                decoratedSupplier = Retry.decorateSupplier(retry, decoratedSupplier);
                
                retry.getEventPublisher()
                        .onRetry(event -> {
                            totalRetries.incrementAndGet();
                            log.debug("Retry attempt {} for service {}", 
                                    event.getNumberOfRetryAttempts(), serviceName);
                        });
            }
            
            return decoratedSupplier.get();
            
        } finally {
            activeConnections.decrementAndGet();
            sample.stop(Timer.builder("service.mesh.request.duration")
                    .tag("service", serviceName)
                    .register(meterRegistry));
        }
    }

    /**
     * Enable canary deployment for a service
     */
    public void enableCanary(String serviceName, ServiceMeshManager.CanaryConfig config) {
        log.info("Enabling canary deployment for {} with {}% traffic", serviceName, config.getPercentage());
        
        CanaryDeployment canary = CanaryDeployment.builder()
                .serviceName(serviceName)
                .canaryVersion(config.getVersion())
                .trafficPercentage(config.getPercentage())
                .headers(config.getHeaders())
                .enabled(true)
                .build();
        
        canaryDeployments.put(serviceName, canary);
    }

    /**
     * Update traffic policy for a service
     */
    public void updatePolicy(String serviceName, ServiceMeshManager.TrafficPolicy policy) {
        log.info("Updating traffic policy for service: {}", serviceName);
        configureRouting(serviceName, policy);
    }

    /**
     * Remove routing configuration for a service
     */
    public void removeRouting(String serviceName) {
        log.info("Removing routing configuration for service: {}", serviceName);
        
        trafficPolicies.remove(serviceName);
        circuitBreakers.remove(serviceName);
        retryPolicies.remove(serviceName);
        canaryDeployments.remove(serviceName);
        serviceInstances.remove(serviceName);
        roundRobinCounters.remove(serviceName);
        latencyStats.remove(serviceName);
    }

    /**
     * Inject fault for chaos engineering
     */
    public void injectFault(ServiceMeshManager.FaultInjectionRequest request) {
        log.warn("Injecting fault for service: {} - Type: {}", 
                request.getServiceName(), request.getType());
        
        switch (request.getType()) {
            case DELAY:
                injectDelay(request.getServiceName(), request.getDuration());
                break;
            case ABORT:
                injectAbort(request.getServiceName(), request.getHttpStatus());
                break;
            case TIMEOUT:
                injectTimeout(request.getServiceName());
                break;
            case NETWORK_PARTITION:
                injectNetworkPartition(request.getServiceName());
                break;
        }
    }

    /**
     * Apply traffic configuration
     */
    public void applyConfiguration(ServiceMeshManager.TrafficConfiguration config) {
        log.info("Applying traffic configuration");
        // Implementation would update traffic policies based on configuration
    }

    /**
     * Check if traffic manager is healthy
     */
    public boolean isHealthy() {
        // Check if circuit breakers are not all open
        long openCircuits = circuitBreakers.values().stream()
                .filter(cb -> cb.getState() == CircuitBreaker.State.OPEN)
                .count();
        
        return openCircuits < circuitBreakers.size();
    }

    /**
     * Shutdown traffic manager
     */
    public void shutdown() {
        log.info("Shutting down Traffic Manager");
        circuitBreakers.clear();
        retryPolicies.clear();
        trafficPolicies.clear();
    }

    // Metrics methods

    public int getActiveConnections() {
        return activeConnections.get();
    }

    public double getAverageLatency() {
        return latencyStats.values().stream()
                .mapToDouble(LatencyStats::getAverage)
                .average()
                .orElse(0.0);
    }

    public double getP95Latency() {
        return latencyStats.values().stream()
                .mapToDouble(LatencyStats::getP95)
                .max()
                .orElse(0.0);
    }

    public double getP99Latency() {
        return latencyStats.values().stream()
                .mapToDouble(LatencyStats::getP99)
                .max()
                .orElse(0.0);
    }

    public int getCircuitBreakerOpenCount() {
        return (int) circuitBreakers.values().stream()
                .filter(cb -> cb.getState() == CircuitBreaker.State.OPEN)
                .count();
    }

    public long getRetryCount() {
        return totalRetries.get();
    }

    // Private helper methods

    private ServiceInstance selectInstance(String serviceName, List<ServiceInstance> instances, RequestContext context) {
        ServiceMeshProperties.LoadBalancingStrategy strategy = 
                Optional.ofNullable(trafficPolicies.get(serviceName))
                        .map(p -> ServiceMeshProperties.LoadBalancingStrategy.valueOf(p.getLoadBalancingStrategy()))
                        .orElse(loadBalancingStrategy);
        
        switch (strategy) {
            case ROUND_ROBIN:
                return roundRobinSelect(serviceName, instances);
            case LEAST_REQUEST:
                return leastRequestSelect(instances);
            case RANDOM:
                return randomSelect(instances);
            case CONSISTENT_HASH:
                return consistentHashSelect(instances, context);
            case WEIGHTED:
                return weightedSelect(instances);
            default:
                return instances.get(0);
        }
    }

    private ServiceInstance roundRobinSelect(String serviceName, List<ServiceInstance> instances) {
        AtomicInteger counter = roundRobinCounters.computeIfAbsent(serviceName, k -> new AtomicInteger(0));
        int index = counter.getAndIncrement() % instances.size();
        return instances.get(index);
    }

    private ServiceInstance leastRequestSelect(List<ServiceInstance> instances) {
        return instances.stream()
                .min(Comparator.comparingInt(ServiceInstance::getActiveRequests))
                .orElse(instances.get(0));
    }

    private ServiceInstance randomSelect(List<ServiceInstance> instances) {
        int index = ThreadLocalRandom.current().nextInt(instances.size());
        return instances.get(index);
    }

    private ServiceInstance consistentHashSelect(List<ServiceInstance> instances, RequestContext context) {
        int hash = context.getSessionId() != null ? context.getSessionId().hashCode() : context.hashCode();
        int index = Math.abs(hash) % instances.size();
        return instances.get(index);
    }

    private ServiceInstance weightedSelect(List<ServiceInstance> instances) {
        int totalWeight = instances.stream().mapToInt(ServiceInstance::getWeight).sum();
        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        
        int currentWeight = 0;
        for (ServiceInstance instance : instances) {
            currentWeight += instance.getWeight();
            if (random < currentWeight) {
                return instance;
            }
        }
        return instances.get(instances.size() - 1);
    }

    private ServiceInstance routeCanary(String serviceName, List<ServiceInstance> instances, RequestContext context) {
        CanaryDeployment canary = canaryDeployments.get(serviceName);
        
        // Check header-based routing first
        if (canary.getHeaders() != null && context.getHeaders() != null) {
            for (Map.Entry<String, String> entry : canary.getHeaders().entrySet()) {
                if (entry.getValue().equals(context.getHeaders().get(entry.getKey()))) {
                    return findCanaryInstance(instances, canary.getCanaryVersion());
                }
            }
        }
        
        // Apply percentage-based routing
        if (ThreadLocalRandom.current().nextInt(100) < canary.getTrafficPercentage()) {
            ServiceInstance canaryInstance = findCanaryInstance(instances, canary.getCanaryVersion());
            if (canaryInstance != null) {
                return canaryInstance;
            }
        }
        
        // Fallback to stable version if canary routing fails
        return findStableInstance(instances, canary.getStableVersion());
    }

    private ServiceInstance findCanaryInstance(List<ServiceInstance> instances, String version) {
        return instances.stream()
                .filter(i -> version.equals(i.getVersion()))
                .findFirst()
                .orElse(null);
    }

    private ServiceInstance findStableInstance(List<ServiceInstance> instances, String stableVersion) {
        return instances.stream()
                .filter(i -> stableVersion.equals(i.getVersion()))
                .findFirst()
                .orElse(instances.isEmpty() ? null : instances.get(0)); // Fallback to first available instance
    }

    private void recordRoutingMetrics(String serviceName, ServiceInstance instance) {
        meterRegistry.counter("service.mesh.routing",
                Tags.of("service", serviceName,
                        "instance", instance.getId(),
                        "version", instance.getVersion())).increment();
    }

    private void injectDelay(String serviceName, Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void injectAbort(String serviceName, int httpStatus) {
        throw new FaultInjectionException("Fault injection: Abort with status " + httpStatus);
    }

    private void injectTimeout(String serviceName) {
        throw new FaultInjectionException("Fault injection: Timeout");
    }

    private void injectNetworkPartition(String serviceName) {
        serviceInstances.remove(serviceName);
    }

    // Inner classes

    @Data
    @Builder
    public static class ServiceInstance {
        private String id;
        private String host;
        private int port;
        private String version;
        private boolean healthy;
        private int weight;
        private int activeRequests;
        private Map<String, String> metadata;
    }

    @Data
    @Builder
    public static class TrafficPolicy {
        private String serviceName;
        private String loadBalancingStrategy;
        private int timeoutMs;
        private int maxRetries;
        private boolean circuitBreakerEnabled;
    }

    @Data
    @Builder
    public static class CanaryDeployment {
        private String serviceName;
        private String canaryVersion;
        private String stableVersion;
        private int trafficPercentage;
        private Map<String, String> headers;
        private boolean enabled;
    }

    @Data
    @Builder
    public static class RequestContext {
        private String sessionId;
        private Map<String, String> headers;
        private String clientId;
    }

    @Data
    @Builder
    public static class LatencyStats {
        private double average;
        private double p50;
        private double p95;
        private double p99;
        private long count;
    }

    public static class NoAvailableInstanceException extends RuntimeException {
        public NoAvailableInstanceException(String message) {
            super(message);
        }
    }

    public static class FaultInjectionException extends RuntimeException {
        public FaultInjectionException(String message) {
            super(message);
        }
    }
}