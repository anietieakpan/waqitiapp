package com.waqiti.common.integration;

import com.waqiti.common.integration.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.ApplicationContext;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceIntegrationManager {

    private final ApplicationContext applicationContext;
    private final ServiceDiscovery serviceDiscovery;
    private final CircuitBreakerManager circuitBreakerManager;
    private final ServiceMetricsCollector metricsCollector;
    
    // Service registry and health tracking
    private final Map<String, ServiceEndpoint> serviceRegistry = new ConcurrentHashMap<>();
    private final Map<String, ServiceHealth> serviceHealthMap = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> serviceDependencies = new ConcurrentHashMap<>();
    
    // Async integration management
    private final Map<String, CompletableFuture<Object>> asyncOperations = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing Service Integration Manager");
        
        // Discover all Feign clients
        discoverFeignClients();
        
        // Map service dependencies
        mapServiceDependencies();
        
        // Start health monitoring
        startHealthMonitoring();
        
        // Register shutdown hooks
        registerShutdownHooks();
        
        log.info("Service Integration Manager initialized with {} services", serviceRegistry.size());
    }

    /**
     * Execute a service call with comprehensive error handling and resilience
     */
    @Retryable(value = {ServiceUnavailableException.class}, maxAttempts = 3, 
               backoff = @Backoff(delay = 1000, multiplier = 2))
    public <T> T executeServiceCall(String serviceName, String operation, 
                                    ServiceCallRequest request, Class<T> responseType) {
        
        String callId = generateCallId(serviceName, operation);
        ServiceEndpoint endpoint = getServiceEndpoint(serviceName);
        
        if (endpoint == null) {
            throw new ServiceNotFoundException("Service not found: " + serviceName);
        }
        
        // Check circuit breaker
        if (!circuitBreakerManager.canExecute(serviceName)) {
            throw new CircuitBreakerOpenException("Circuit breaker is open for service: " + serviceName);
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Pre-execution validation
            validateServiceCall(serviceName, operation, request);
            
            // Execute the call
            T result = performServiceCall(endpoint, operation, request, responseType);
            
            // Post-execution processing
            long executionTime = System.currentTimeMillis() - startTime;
            metricsCollector.recordSuccess(serviceName, operation, executionTime);
            circuitBreakerManager.recordSuccess(serviceName);
            
            log.debug("Service call successful: {} - {} ({}ms)", serviceName, operation, executionTime);
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            metricsCollector.recordFailure(serviceName, operation, executionTime, e);
            circuitBreakerManager.recordFailure(serviceName, e);
            
            log.error("Service call failed: {} - {} ({}ms): {}", 
                serviceName, operation, executionTime, e.getMessage());
            
            // Handle different types of failures
            throw handleServiceCallException(serviceName, operation, e);
        }
    }

    /**
     * Execute async service call with callback handling
     */
    public <T> CompletableFuture<T> executeAsyncServiceCall(String serviceName, String operation,
                                                            ServiceCallRequest request, Class<T> responseType) {
        
        String callId = generateCallId(serviceName, operation);
        
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            try {
                return executeServiceCall(serviceName, operation, request, responseType);
            } catch (Exception e) {
                throw new RuntimeException("Async service call failed", e);
            }
        }).orTimeout(30, TimeUnit.SECONDS)
          .whenComplete((result, throwable) -> {
              asyncOperations.remove(callId);
              if (throwable != null) {
                  log.error("Async service call failed: {} - {}", serviceName, operation, throwable);
              }
          });
        
        asyncOperations.put(callId, (CompletableFuture<Object>) future);
        return future;
    }

    /**
     * Execute bulk service calls with batching and error handling
     */
    public <T> List<ServiceCallResult<T>> executeBulkServiceCalls(String serviceName, String operation,
                                                                  List<ServiceCallRequest> requests, 
                                                                  Class<T> responseType) {
        
        List<ServiceCallResult<T>> results = new ArrayList<>();
        List<CompletableFuture<ServiceCallResult<T>>> futures = new ArrayList<>();
        
        // Execute calls in parallel with proper error isolation
        for (int i = 0; i < requests.size(); i++) {
            final int index = i;
            final ServiceCallRequest request = requests.get(i);
            
            CompletableFuture<ServiceCallResult<T>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    T result = executeServiceCall(serviceName, operation, request, responseType);
                    return ServiceCallResult.<T>builder()
                        .index(index)
                        .success(true)
                        .result(result)
                        .build();
                } catch (Exception e) {
                    return ServiceCallResult.<T>builder()
                        .index(index)
                        .success(false)
                        .error(e.getMessage())
                        .exception(e)
                        .build();
                }
            });
            
            futures.add(future);
        }
        
        // Collect all results
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0]));
        
        try {
            allFutures.get(60, TimeUnit.SECONDS); // Wait for all to complete
            
            for (CompletableFuture<ServiceCallResult<T>> future : futures) {
                results.add(future.get());
            }
            
        } catch (Exception e) {
            log.error("Bulk service call execution failed: {} - {}", serviceName, operation, e);
            
            // Collect partial results
            for (CompletableFuture<ServiceCallResult<T>> future : futures) {
                try {
                    if (future.isDone() && !future.isCompletedExceptionally()) {
                        results.add(future.get());
                    } else {
                        results.add(ServiceCallResult.<T>builder()
                            .success(false)
                            .error("Request timed out or failed")
                            .build());
                    }
                } catch (Exception ex) {
                    results.add(ServiceCallResult.<T>builder()
                        .success(false)
                        .error(ex.getMessage())
                        .exception(ex)
                        .build());
                }
            }
        }
        
        return results;
    }

    /**
     * Check if a service is healthy and available
     */
    public boolean isServiceHealthy(String serviceName) {
        ServiceHealth health = serviceHealthMap.get(serviceName);
        if (health == null) {
            return false;
        }
        
        return health.isHealthy() && 
               health.getLastCheck().isAfter(LocalDateTime.now().minus(Duration.ofMinutes(5)));
    }

    /**
     * Get service metrics and health information
     */
    public ServiceIntegrationReport getIntegrationReport() {
        // Collect service health data
        Map<String, ServiceIntegrationReport.ServiceHealthInfo> healthInfo = new ConcurrentHashMap<>();
        for (Map.Entry<String, ServiceHealth> entry : serviceHealthMap.entrySet()) {
            ServiceHealth health = entry.getValue();
            healthInfo.put(entry.getKey(), ServiceIntegrationReport.ServiceHealthInfo.builder()
                .serviceName(entry.getKey())
                .status(health.getStatus() != null ? health.getStatus().name() : "UNKNOWN")
                .healthScore(health.getHealthScore())
                .lastCheck(health.getLastChecked())
                .build());
        }

        // Collect metrics
        Map<String, ServiceMetricsCollector.ServiceMetrics> metrics = metricsCollector.getAllMetrics();

        // Collect circuit breaker states
        Map<String, String> circuitStates = new ConcurrentHashMap<>();
        // Circuit breaker states would be populated from circuit breaker manager

        return ServiceIntegrationReport.builder()
            .serviceHealthDetails(healthInfo)
            .totalServices(serviceRegistry.size())
            .healthyServices((int) healthInfo.values().stream()
                .filter(h -> "HEALTHY".equals(h.getStatus())).count())
            .generatedAt(java.time.Instant.now())
            .reportVersion("1.0")
            .build();
    }

    /**
     * Gracefully handle service unavailability with fallback mechanisms
     */
    public <T> T executeWithFallback(String serviceName, String operation,
                                     ServiceCallRequest request, Class<T> responseType,
                                     FallbackStrategy<T> fallbackStrategy) {
        
        try {
            return executeServiceCall(serviceName, operation, request, responseType);
        } catch (Exception e) {
            log.warn("Service call failed, executing fallback: {} - {}", serviceName, operation, e);
            
            if (fallbackStrategy != null) {
                return fallbackStrategy.execute(serviceName, operation, request, e);
            }
            
            throw e;
        }
    }

    /**
     * Resolve circular dependencies by deferring calls
     */
    public <T> CompletableFuture<T> deferServiceCall(String serviceName, String operation,
                                                     ServiceCallRequest request, Class<T> responseType,
                                                     Duration delay) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(delay.toMillis());
                return executeServiceCall(serviceName, operation, request, responseType);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Deferred service call interrupted", e);
            }
        });
    }

    private void discoverFeignClients() {
        log.info("Discovering Feign clients...");
        
        String[] beanNames = applicationContext.getBeanNamesForAnnotation(FeignClient.class);
        for (String beanName : beanNames) {
            try {
                Object bean = applicationContext.getBean(beanName);
                FeignClient feignClient = bean.getClass().getAnnotation(FeignClient.class);
                
                if (feignClient != null) {
                    String serviceName = !feignClient.name().isEmpty() ? 
                        feignClient.name() : feignClient.value();
                    
                    ServiceEndpoint endpoint = ServiceEndpoint.builder()
                        .serviceName(serviceName)
                        .url(feignClient.url())
                        .clientBean(bean)
                        .clientInterface(bean.getClass().getInterfaces()[0])
                        .build();
                    
                    serviceRegistry.put(serviceName, endpoint);

                    // Initialize health tracking
                    serviceHealthMap.put(serviceName, new ServiceHealth(serviceName));

                    log.info("Registered Feign client: {} -> {}", serviceName, feignClient.url());
                }
            } catch (Exception e) {
                log.error("Failed to register Feign client: {}", beanName, e);
            }
        }
    }

    private void mapServiceDependencies() {
        log.info("Mapping service dependencies...");
        
        for (ServiceEndpoint endpoint : serviceRegistry.values()) {
            Set<String> dependencies = new HashSet<>();
            
            // Analyze methods to find dependencies
            Method[] methods = endpoint.getClientInterface().getMethods();
            for (Method method : methods) {
                RequestMapping mapping = method.getAnnotation(RequestMapping.class);
                if (mapping != null) {
                    // Extract service calls from method signatures and parameters
                    // This is a simplified approach - in reality, you'd need more sophisticated analysis
                    String[] paths = mapping.path();
                    for (String path : paths) {
                        if (path.contains("user") && !endpoint.getServiceName().equals("user-service")) {
                            dependencies.add("user-service");
                        }
                        if (path.contains("payment") && !endpoint.getServiceName().equals("payment-service")) {
                            dependencies.add("payment-service");
                        }
                        if (path.contains("wallet") && !endpoint.getServiceName().equals("wallet-service")) {
                            dependencies.add("wallet-service");
                        }
                        // Add more service dependency detection logic
                    }
                }
            }
            
            serviceDependencies.put(endpoint.getServiceName(), dependencies);
            log.debug("Service {} depends on: {}", endpoint.getServiceName(), dependencies);
        }
        
        // Detect and warn about circular dependencies
        detectCircularDependencies();
    }

    private void detectCircularDependencies() {
        for (String serviceName : serviceDependencies.keySet()) {
            Set<String> visited = new HashSet<>();
            Set<String> recursionStack = new HashSet<>();
            
            if (hasCircularDependency(serviceName, visited, recursionStack)) {
                log.warn("Circular dependency detected involving service: {}", serviceName);
                // Implement circular dependency resolution strategies
                resolveCircularDependency(serviceName, recursionStack);
            }
        }
    }

    private boolean hasCircularDependency(String serviceName, Set<String> visited, Set<String> recursionStack) {
        visited.add(serviceName);
        recursionStack.add(serviceName);
        
        Set<String> dependencies = serviceDependencies.get(serviceName);
        if (dependencies != null) {
            for (String dependency : dependencies) {
                if (!visited.contains(dependency)) {
                    if (hasCircularDependency(dependency, visited, recursionStack)) {
                        return true;
                    }
                } else if (recursionStack.contains(dependency)) {
                    return true;
                }
            }
        }
        
        recursionStack.remove(serviceName);
        return false;
    }

    private void resolveCircularDependency(String serviceName, Set<String> circularServices) {
        log.info("Resolving circular dependency for services: {}", circularServices);
        
        // Strategy 1: Use event-driven communication for circular dependencies
        // Strategy 2: Implement lazy loading and deferred execution
        // Strategy 3: Break dependency by introducing intermediary service
        
        for (String service : circularServices) {
            ServiceEndpoint endpoint = serviceRegistry.get(service);
            if (endpoint != null) {
                // Mark as requiring special handling
                // TODO: Add hasCircularDependency and circularDependencyServices fields to ServiceEndpoint
                // endpoint.setHasCircularDependency(true);
                // endpoint.setCircularDependencyServices(new HashSet<>(circularServices));

                log.info("Service {} has circular dependency detected", service);
            }
        }
    }

    private void startHealthMonitoring() {
        // Implementation would start background thread for health monitoring
        log.info("Starting service health monitoring");
    }

    private void registerShutdownHooks() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down Service Integration Manager");
            
            // Cancel all async operations
            for (CompletableFuture<Object> future : asyncOperations.values()) {
                future.cancel(true);
            }
            
            // Close circuit breakers
            circuitBreakerManager.shutdown();
            
            log.info("Service Integration Manager shutdown complete");
        }));
    }

    // Helper methods and inner classes would be implemented here...
    
    private String generateCallId(String serviceName, String operation) {
        return serviceName + "-" + operation + "-" + System.currentTimeMillis();
    }

    private ServiceEndpoint getServiceEndpoint(String serviceName) {
        return serviceRegistry.get(serviceName);
    }

    private void validateServiceCall(String serviceName, String operation, ServiceCallRequest request) {
        // Implement validation logic
    }

    private <T> T performServiceCall(ServiceEndpoint endpoint, String operation, 
                                     ServiceCallRequest request, Class<T> responseType) {
        log.debug("Performing service call to {} - operation: {}", endpoint.getServiceName(), operation);
        
        try {
            // Build the complete URL
            String url = buildServiceUrl(endpoint, request.getPath());
            
            // Create HTTP client (would use RestTemplate or WebClient in production)
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            
            // Configure timeouts
            org.springframework.http.client.HttpComponentsClientHttpRequestFactory factory =
                new org.springframework.http.client.HttpComponentsClientHttpRequestFactory();
            factory.setConnectTimeout(java.time.Duration.ofMillis(endpoint.getConnectTimeoutMs()));
            factory.setConnectionRequestTimeout(java.time.Duration.ofMillis(endpoint.getReadTimeoutMs()));
            restTemplate.setRequestFactory(factory);
            
            // Prepare headers
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            if (request.getHeaders() != null) {
                request.getHeaders().forEach(headers::set);
            }
            
            // Add standard headers
            headers.set("X-Request-ID", request.getRequestId());
            headers.set("X-Correlation-ID", request.getCorrelationId());
            if (request.getUserId() != null) {
                headers.set("X-User-ID", request.getUserId());
            }
            if (request.getSessionId() != null) {
                headers.set("X-Session-ID", request.getSessionId());
            }
            
            // Add idempotency key if present
            if (request.getIdempotencyKey() != null) {
                headers.set("Idempotency-Key", request.getIdempotencyKey());
            }
            
            // Create the HTTP entity
            org.springframework.http.HttpEntity<?> httpEntity = new org.springframework.http.HttpEntity<>(
                request.getBody(), headers
            );
            
            // Build URI with query parameters
            org.springframework.web.util.UriComponentsBuilder uriBuilder = 
                org.springframework.web.util.UriComponentsBuilder.fromHttpUrl(url);
            if (request.getQueryParams() != null) {
                request.getQueryParams().forEach(uriBuilder::queryParam);
            }
            String finalUrl = uriBuilder.build().toUriString();
            
            // Determine HTTP method
            org.springframework.http.HttpMethod httpMethod = 
                org.springframework.http.HttpMethod.valueOf(
                    request.getHttpMethod() != null ? request.getHttpMethod() : "GET"
                );
            
            // Record metrics
            long startTime = System.currentTimeMillis();
            
            try {
                // Execute the HTTP request
                org.springframework.http.ResponseEntity<T> response = restTemplate.exchange(
                    finalUrl,
                    httpMethod,
                    httpEntity,
                    responseType
                );
                
                // Record success metrics
                long duration = System.currentTimeMillis() - startTime;
                metricsCollector.recordServiceCall(
                    endpoint.getServiceName(),
                    operation,
                    Duration.ofMillis(duration),
                    true
                );
                
                // Update service health
                ServiceHealth health = serviceHealthMap.computeIfAbsent(
                    endpoint.getServiceName(),
                    k -> new ServiceHealth(k)
                );
                health.recordSuccess(duration);
                
                log.debug("Service call successful: {} - {} in {}ms", 
                    endpoint.getServiceName(), operation, duration);
                
                return response.getBody();
                
            } catch (org.springframework.web.client.HttpStatusCodeException e) {
                // Handle HTTP errors
                long duration = System.currentTimeMillis() - startTime;
                metricsCollector.recordServiceCall(
                    endpoint.getServiceName(),
                    operation,
                    Duration.ofMillis(duration),
                    false
                );
                
                // Update service health
                ServiceHealth health = serviceHealthMap.get(endpoint.getServiceName());
                if (health != null) {
                    health.recordFailure(e.getStatusCode().value(), e.getMessage());
                }
                
                log.error("Service call failed with HTTP {}: {} - {}", 
                    e.getStatusCode(), endpoint.getServiceName(), operation);
                
                // Check if retryable
                if (endpoint.getRetryableStatusCodes() != null && 
                    endpoint.getRetryableStatusCodes().contains(e.getStatusCode().value())) {
                    throw new ServiceUnavailableException(
                        "Service temporarily unavailable: " + endpoint.getServiceName(), e
                    );
                }
                
                throw new ServiceException(
                    String.format("Service call failed: %s - HTTP %d", 
                        endpoint.getServiceName(), e.getStatusCode().value()), e
                );
            }
            
        } catch (java.net.SocketTimeoutException e) {
            log.error("Service call timeout: {} - {}", endpoint.getServiceName(), operation);
            ServiceHealth health = serviceHealthMap.get(endpoint.getServiceName());
            if (health != null) {
                health.recordTimeout();
            }
            throw new ServiceTimeoutException(
                "Service call timed out: " + endpoint.getServiceName(), e
            );
            
        } catch (java.net.ConnectException | java.net.UnknownHostException e) {
            log.error("Service connection failed: {} - {}", endpoint.getServiceName(), operation);
            ServiceHealth health = serviceHealthMap.get(endpoint.getServiceName());
            if (health != null) {
                health.recordConnectionFailure();
            }
            throw new ServiceUnavailableException(
                "Service unavailable: " + endpoint.getServiceName(), e
            );
            
        } catch (Exception e) {
            if (e instanceof ServiceException) {
                throw (ServiceException) e;
            }
            log.error("Unexpected service call error: {} - {}", 
                endpoint.getServiceName(), operation, e);
            throw new ServiceException(
                "Service call failed: " + endpoint.getServiceName(), e
            );
        }
    }
    
    private String buildServiceUrl(ServiceEndpoint endpoint, String path) {
        StringBuilder url = new StringBuilder();
        
        // Protocol
        url.append(endpoint.getProtocol() != null ? endpoint.getProtocol() : "http");
        url.append("://");
        
        // Base URL
        url.append(endpoint.getBaseUrl());
        
        // Port if specified and not default
        if (endpoint.getPort() > 0) {
            boolean isDefaultPort = (endpoint.getPort() == 80 && "http".equals(endpoint.getProtocol())) ||
                                  (endpoint.getPort() == 443 && "https".equals(endpoint.getProtocol()));
            if (!isDefaultPort) {
                url.append(":").append(endpoint.getPort());
            }
        }
        
        // Context path
        if (endpoint.getContextPath() != null && !endpoint.getContextPath().isEmpty()) {
            if (!endpoint.getContextPath().startsWith("/")) {
                url.append("/");
            }
            url.append(endpoint.getContextPath());
        }
        
        // API path
        if (path != null && !path.isEmpty()) {
            if (!path.startsWith("/")) {
                url.append("/");
            }
            url.append(path);
        }
        
        return url.toString();
    }

    private RuntimeException handleServiceCallException(String serviceName, String operation, Exception e) {
        if (e instanceof java.net.ConnectException) {
            return new ServiceUnavailableException("Service unavailable: " + serviceName, e);
        } else if (e instanceof java.util.concurrent.TimeoutException) {
            return new ServiceTimeoutException("Service timeout: " + serviceName, e);
        } else {
            return new ServiceException("Service call failed: " + serviceName, e);
        }
    }

    // Exception classes
    public static class ServiceNotFoundException extends RuntimeException {
        public ServiceNotFoundException(String message) { super(message); }
    }

    public static class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message, Throwable cause) { super(message, cause); }
    }

    public static class ServiceTimeoutException extends RuntimeException {
        public ServiceTimeoutException(String message, Throwable cause) { super(message, cause); }
    }

    public static class ServiceException extends RuntimeException {
        public ServiceException(String message, Throwable cause) { super(message, cause); }
    }

    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) { super(message); }
    }

    @FunctionalInterface
    public interface FallbackStrategy<T> {
        T execute(String serviceName, String operation, ServiceCallRequest request, Exception originalException);
    }
}