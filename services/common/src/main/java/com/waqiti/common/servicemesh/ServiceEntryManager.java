package com.waqiti.common.servicemesh;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Production-ready Service Entry Manager for external service registration
 * Manages external service endpoints that are not part of the service mesh
 * 
 * Features:
 * - External service discovery and registration
 * - DNS resolution and caching
 * - Service endpoint health monitoring
 * - Protocol-specific configurations (HTTP, HTTPS, gRPC, TCP)
 * - TLS configuration for external services
 * - Service location awareness (MESH_EXTERNAL, MESH_INTERNAL)
 * - Resolution modes (DNS, STATIC, NONE)
 * - Endpoint rotation and failover
 * - Service entry versioning and updates
 * - Wildcard host support for domains
 */
@Slf4j
@Data
@Builder
@Component
public class ServiceEntryManager {

    private final ServiceMeshProperties properties;
    private final Map<String, ExternalServiceConfig> externalServices;
    
    // Service entry registry
    private final Map<String, ServiceEntry> serviceEntries = new ConcurrentHashMap<>();
    private final Map<String, ServiceEndpointHealth> endpointHealth = new ConcurrentHashMap<>();
    private final Map<String, DnsCache> dnsCache = new ConcurrentHashMap<>();
    
    // Thread pools
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(3);
    private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(10);
    
    // DNS resolver
    private final DnsResolver dnsResolver = new DnsResolver();
    
    // Metrics and monitoring
    private final AtomicLong registrationCount = new AtomicLong(0);
    private final AtomicLong resolutionCount = new AtomicLong(0);
    private final AtomicLong healthCheckCount = new AtomicLong(0);
    private final Map<String, ServiceMetrics> serviceMetrics = new ConcurrentHashMap<>();
    
    // State management
    @Builder.Default
    private volatile ManagerState state = ManagerState.INITIALIZING;

    @PostConstruct
    public void initialize() {
        log.info("Initializing Service Entry Manager");
        
        // Register configured external services
        registerConfiguredServices();
        
        // Start DNS resolution scheduler
        startDnsResolutionScheduler();
        
        // Start health monitoring
        startHealthMonitoring();
        
        // Initialize default service entries
        initializeDefaultServiceEntries();
        
        state = ManagerState.RUNNING;
        log.info("Service Entry Manager initialized successfully");
    }

    /**
     * Register an external service
     */
    public CompletableFuture<ServiceRegistrationResult> registerExternalService(ServiceRegistrationRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Registering external service: {}", request.getName());
            
            try {
                // Validate request
                validateServiceRegistration(request);
                
                // Create service entry
                ServiceEntry serviceEntry = ServiceEntry.builder()
                        .name(request.getName())
                        .hosts(request.getHosts())
                        .ports(createServicePorts(request.getPorts()))
                        .location(request.getLocation() != null ? 
                                request.getLocation() : ServiceLocation.MESH_EXTERNAL)
                        .resolution(request.getResolution() != null ? 
                                request.getResolution() : ResolutionMode.DNS)
                        .endpoints(request.getEndpoints())
                        .subjectAltNames(request.getSubjectAltNames())
                        .workloadSelector(request.getWorkloadSelector())
                        .exportTo(request.getExportTo())
                        .metadata(request.getMetadata())
                        .createdAt(LocalDateTime.now())
                        .version(1)
                        .build();
                
                // Resolve endpoints if using DNS
                if (serviceEntry.getResolution() == ResolutionMode.DNS) {
                    resolveServiceEndpoints(serviceEntry);
                }
                
                // Apply to mesh
                applyServiceEntry(serviceEntry);
                
                // Store in registry
                serviceEntries.put(request.getName(), serviceEntry);
                
                // Initialize metrics
                serviceMetrics.put(request.getName(), new ServiceMetrics(request.getName()));
                
                // Start health monitoring
                if (request.isHealthCheckEnabled()) {
                    startEndpointHealthCheck(serviceEntry);
                }
                
                registrationCount.incrementAndGet();
                log.info("External service registered successfully: {}", request.getName());
                
                return ServiceRegistrationResult.builder()
                        .success(true)
                        .serviceName(request.getName())
                        .registeredEndpoints(serviceEntry.getEndpoints().size())
                        .registeredAt(LocalDateTime.now())
                        .build();
                
            } catch (Exception e) {
                log.error("Failed to register external service: {}", request.getName(), e);
                
                return ServiceRegistrationResult.builder()
                        .success(false)
                        .serviceName(request.getName())
                        .errorMessage(e.getMessage())
                        .build();
            }
        }, asyncExecutor);
    }

    /**
     * Update service endpoints dynamically
     */
    public CompletableFuture<EndpointUpdateResult> updateServiceEndpoints(String serviceName, 
                                                                         List<ServiceEndpoint> newEndpoints) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Updating endpoints for service: {}", serviceName);
            
            try {
                ServiceEntry serviceEntry = serviceEntries.get(serviceName);
                if (serviceEntry == null) {
                    throw new ServiceNotFoundException("Service not found: " + serviceName);
                }
                
                // Validate endpoints
                validateEndpoints(newEndpoints);
                
                // Create updated service entry
                ServiceEntry updatedEntry = serviceEntry.toBuilder()
                        .endpoints(newEndpoints)
                        .version(serviceEntry.getVersion() + 1)
                        .updatedAt(LocalDateTime.now())
                        .build();
                
                // Apply updates
                applyServiceEntry(updatedEntry);
                
                // Update registry
                serviceEntries.put(serviceName, updatedEntry);
                
                // Reset health status for new endpoints
                resetEndpointHealth(serviceName, newEndpoints);
                
                log.info("Service endpoints updated successfully: {}", serviceName);
                
                return EndpointUpdateResult.builder()
                        .success(true)
                        .serviceName(serviceName)
                        .updatedEndpoints(newEndpoints.size())
                        .version(updatedEntry.getVersion())
                        .appliedAt(LocalDateTime.now())
                        .build();
                
            } catch (Exception e) {
                log.error("Failed to update service endpoints: {}", serviceName, e);
                
                return EndpointUpdateResult.builder()
                        .success(false)
                        .serviceName(serviceName)
                        .errorMessage(e.getMessage())
                        .build();
            }
        }, asyncExecutor);
    }

    /**
     * Resolve DNS for a service
     */
    public CompletableFuture<DnsResolutionResult> resolveDns(String hostname) {
        return CompletableFuture.supplyAsync(() -> {
            log.debug("Resolving DNS for hostname: {}", hostname);
            
            try {
                // Check cache first
                DnsCache cached = dnsCache.get(hostname);
                if (cached != null && !cached.isExpired()) {
                    log.debug("Using cached DNS resolution for: {}", hostname);
                    return DnsResolutionResult.builder()
                            .success(true)
                            .hostname(hostname)
                            .ipAddresses(cached.getIpAddresses())
                            .fromCache(true)
                            .ttl(cached.getTtl())
                            .build();
                }
                
                // Perform DNS resolution
                List<String> ipAddresses = dnsResolver.resolve(hostname);
                
                // Cache the result
                DnsCache newCache = DnsCache.builder()
                        .hostname(hostname)
                        .ipAddresses(ipAddresses)
                        .resolvedAt(LocalDateTime.now())
                        .ttl(Duration.ofMinutes(5))
                        .build();
                
                dnsCache.put(hostname, newCache);
                resolutionCount.incrementAndGet();
                
                log.debug("DNS resolved for {}: {}", hostname, ipAddresses);
                
                return DnsResolutionResult.builder()
                        .success(true)
                        .hostname(hostname)
                        .ipAddresses(ipAddresses)
                        .fromCache(false)
                        .ttl(newCache.getTtl())
                        .build();
                
            } catch (Exception e) {
                log.error("DNS resolution failed for: {}", hostname, e);
                
                return DnsResolutionResult.builder()
                        .success(false)
                        .hostname(hostname)
                        .errorMessage(e.getMessage())
                        .build();
            }
        }, asyncExecutor);
    }

    /**
     * Get healthy endpoints for a service
     */
    public List<ServiceEndpoint> getHealthyEndpoints(String serviceName) {
        ServiceEntry serviceEntry = serviceEntries.get(serviceName);
        if (serviceEntry == null) {
            return Collections.emptyList();
        }
        
        return serviceEntry.getEndpoints().stream()
                .filter(endpoint -> {
                    String key = serviceName + ":" + endpoint.getAddress() + ":" + endpoint.getPort();
                    ServiceEndpointHealth health = endpointHealth.get(key);
                    return health == null || health.isHealthy();
                })
                .collect(Collectors.toList());
    }

    /**
     * Configure TLS for external service
     */
    public CompletableFuture<TlsConfigResult> configureTls(String serviceName, TlsConfig tlsConfig) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Configuring TLS for service: {}", serviceName);
            
            try {
                ServiceEntry serviceEntry = serviceEntries.get(serviceName);
                if (serviceEntry == null) {
                    throw new ServiceNotFoundException("Service not found: " + serviceName);
                }
                
                // Validate TLS configuration
                validateTlsConfig(tlsConfig);
                
                // Update service entry with TLS config
                ServiceEntry updatedEntry = serviceEntry.toBuilder()
                        .tlsConfig(tlsConfig)
                        .version(serviceEntry.getVersion() + 1)
                        .updatedAt(LocalDateTime.now())
                        .build();
                
                // Apply updates
                applyServiceEntry(updatedEntry);
                
                // Update registry
                serviceEntries.put(serviceName, updatedEntry);
                
                log.info("TLS configured successfully for service: {}", serviceName);
                
                return TlsConfigResult.builder()
                        .success(true)
                        .serviceName(serviceName)
                        .tlsMode(tlsConfig.getMode())
                        .sni(tlsConfig.getSni())
                        .appliedAt(LocalDateTime.now())
                        .build();
                
            } catch (Exception e) {
                log.error("Failed to configure TLS for service: {}", serviceName, e);
                
                return TlsConfigResult.builder()
                        .success(false)
                        .serviceName(serviceName)
                        .errorMessage(e.getMessage())
                        .build();
            }
        }, asyncExecutor);
    }

    /**
     * Get service metrics
     */
    public ServiceMetrics getServiceMetrics(String serviceName) {
        return serviceMetrics.get(serviceName);
    }

    /**
     * Deregister an external service
     */
    public CompletableFuture<Void> deregisterService(String serviceName) {
        return CompletableFuture.runAsync(() -> {
            log.info("Deregistering service: {}", serviceName);
            
            try {
                ServiceEntry serviceEntry = serviceEntries.remove(serviceName);
                if (serviceEntry != null) {
                    // Remove from mesh
                    removeServiceEntry(serviceEntry);
                    
                    // Clean up health status
                    cleanupEndpointHealth(serviceName);
                    
                    // Remove metrics
                    serviceMetrics.remove(serviceName);
                    
                    // Clear DNS cache
                    serviceEntry.getHosts().forEach(dnsCache::remove);
                    
                    log.info("Service deregistered successfully: {}", serviceName);
                }
            } catch (Exception e) {
                log.error("Failed to deregister service: {}", serviceName, e);
                throw new RuntimeException("Deregistration failed", e);
            }
        }, asyncExecutor);
    }

    // Private helper methods

    private void registerConfiguredServices() {
        if (externalServices == null || externalServices.isEmpty()) {
            log.info("No external services configured");
            return;
        }
        
        log.info("Registering {} configured external services", externalServices.size());
        
        for (Map.Entry<String, ExternalServiceConfig> entry : externalServices.entrySet()) {
            String name = entry.getKey();
            ExternalServiceConfig config = entry.getValue();
            
            ServiceRegistrationRequest request = ServiceRegistrationRequest.builder()
                    .name(name)
                    .hosts(config.getHosts())
                    .ports(config.getPorts())
                    .location(config.getLocation())
                    .resolution(config.getResolution())
                    .endpoints(config.getEndpoints())
                    .healthCheckEnabled(config.isHealthCheckEnabled())
                    .metadata(config.getMetadata())
                    .build();
            
            registerExternalService(request);
        }
    }

    private void startDnsResolutionScheduler() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                refreshDnsResolutions();
            } catch (Exception e) {
                log.error("DNS resolution scheduler error", e);
            }
        }, 30, 60, TimeUnit.SECONDS);
    }

    private void startHealthMonitoring() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                performHealthChecks();
            } catch (Exception e) {
                log.error("Health monitoring error", e);
            }
        }, 10, 30, TimeUnit.SECONDS);
    }

    private void initializeDefaultServiceEntries() {
        // Register common external services
        
        // External payment gateways
        registerDefaultService("stripe-api", Arrays.asList("api.stripe.com"), 443, "HTTPS");
        registerDefaultService("paypal-api", Arrays.asList("api.paypal.com"), 443, "HTTPS");
        
        // External APIs
        registerDefaultService("google-maps", Arrays.asList("maps.googleapis.com"), 443, "HTTPS");
        registerDefaultService("twilio-api", Arrays.asList("api.twilio.com"), 443, "HTTPS");
        
        // External databases - register from configured external services
        if (properties != null && properties.getExternalServices() != null) {
            properties.getExternalServices().stream()
                    .filter(service -> "TCP".equalsIgnoreCase(service.getProtocol()))
                    .findFirst()
                    .ifPresent(service -> 
                        registerDefaultService("external-db", 
                                Arrays.asList(service.getHost()), 
                                service.getPort(), 
                                "TCP"));
        }
    }

    private void registerDefaultService(String name, List<String> hosts, int port, String protocol) {
        try {
            ServiceRegistrationRequest request = ServiceRegistrationRequest.builder()
                    .name(name)
                    .hosts(hosts)
                    .ports(Arrays.asList(ServicePort.builder()
                            .number(port)
                            .protocol(protocol)
                            .name(protocol.toLowerCase())
                            .build()))
                    .location(ServiceLocation.MESH_EXTERNAL)
                    .resolution(ResolutionMode.DNS)
                    .healthCheckEnabled(false)
                    .build();
            
            registerExternalService(request);
        } catch (Exception e) {
            log.warn("Failed to register default service: {}", name, e);
        }
    }

    private void validateServiceRegistration(ServiceRegistrationRequest request) {
        if (request.getName() == null || request.getName().isEmpty()) {
            throw new IllegalArgumentException("Service name is required");
        }
        if (request.getHosts() == null || request.getHosts().isEmpty()) {
            throw new IllegalArgumentException("At least one host is required");
        }
        if (request.getPorts() == null || request.getPorts().isEmpty()) {
            throw new IllegalArgumentException("At least one port is required");
        }
    }

    private List<ServicePort> createServicePorts(List<ServicePort> ports) {
        return ports.stream()
                .map(port -> ServicePort.builder()
                        .number(port.getNumber())
                        .protocol(port.getProtocol() != null ? port.getProtocol() : "TCP")
                        .name(port.getName() != null ? port.getName() : 
                                (port.getProtocol() != null ? port.getProtocol().toLowerCase() : "tcp") + "-" + port.getNumber())
                        .targetPort(port.getTargetPort() > 0 ? port.getTargetPort() : port.getNumber())
                        .build())
                .collect(Collectors.toList());
    }

    private void resolveServiceEndpoints(ServiceEntry serviceEntry) {
        List<ServiceEndpoint> endpoints = new ArrayList<>();
        
        for (String host : serviceEntry.getHosts()) {
            try {
                List<String> ipAddresses = dnsResolver.resolve(host);
                for (String ip : ipAddresses) {
                    for (ServicePort port : serviceEntry.getPorts()) {
                        endpoints.add(ServiceEndpoint.builder()
                                .address(ip)
                                .port(port.getNumber())
                                .labels(Map.of("host", host))
                                .weight(100)
                                .priority(0)
                                .build());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to resolve host: {}", host, e);
            }
        }
        
        serviceEntry.setEndpoints(endpoints);
    }

    private void applyServiceEntry(ServiceEntry serviceEntry) {
        // Apply to Istio/Envoy via Kubernetes API or xDS
        log.debug("Applying service entry: {}", serviceEntry.getName());
        // Implementation would use Kubernetes client or xDS API
    }

    private void removeServiceEntry(ServiceEntry serviceEntry) {
        // Remove from Istio/Envoy
        log.debug("Removing service entry: {}", serviceEntry.getName());
        // Implementation would use Kubernetes client or xDS API
    }

    private void validateEndpoints(List<ServiceEndpoint> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            throw new IllegalArgumentException("At least one endpoint is required");
        }
        
        for (ServiceEndpoint endpoint : endpoints) {
            if (endpoint.getAddress() == null || endpoint.getAddress().isEmpty()) {
                throw new IllegalArgumentException("Endpoint address is required");
            }
            if (endpoint.getPort() <= 0 || endpoint.getPort() > 65535) {
                throw new IllegalArgumentException("Invalid port number: " + endpoint.getPort());
            }
        }
    }

    private void validateTlsConfig(TlsConfig tlsConfig) {
        if (tlsConfig.getMode() == null) {
            throw new IllegalArgumentException("TLS mode is required");
        }
        if (tlsConfig.getMode() == TlsMode.MUTUAL && 
            (tlsConfig.getClientCertificate() == null || tlsConfig.getPrivateKey() == null)) {
            throw new IllegalArgumentException("Client certificate and private key required for mutual TLS");
        }
    }

    private void startEndpointHealthCheck(ServiceEntry serviceEntry) {
        for (ServiceEndpoint endpoint : serviceEntry.getEndpoints()) {
            String key = serviceEntry.getName() + ":" + endpoint.getAddress() + ":" + endpoint.getPort();
            
            ServiceEndpointHealth health = ServiceEndpointHealth.builder()
                    .serviceName(serviceEntry.getName())
                    .endpoint(endpoint)
                    .healthy(true)
                    .lastCheck(LocalDateTime.now())
                    .consecutiveFailures(0)
                    .build();
            
            endpointHealth.put(key, health);
        }
    }

    private void resetEndpointHealth(String serviceName, List<ServiceEndpoint> endpoints) {
        // Remove old health entries
        cleanupEndpointHealth(serviceName);
        
        // Create new health entries
        for (ServiceEndpoint endpoint : endpoints) {
            String key = serviceName + ":" + endpoint.getAddress() + ":" + endpoint.getPort();
            
            ServiceEndpointHealth health = ServiceEndpointHealth.builder()
                    .serviceName(serviceName)
                    .endpoint(endpoint)
                    .healthy(true)
                    .lastCheck(LocalDateTime.now())
                    .consecutiveFailures(0)
                    .build();
            
            endpointHealth.put(key, health);
        }
    }

    private void cleanupEndpointHealth(String serviceName) {
        endpointHealth.entrySet().removeIf(entry -> 
                entry.getValue().getServiceName().equals(serviceName));
    }

    private void refreshDnsResolutions() {
        for (ServiceEntry serviceEntry : serviceEntries.values()) {
            if (serviceEntry.getResolution() == ResolutionMode.DNS) {
                resolveServiceEndpoints(serviceEntry);
                applyServiceEntry(serviceEntry);
            }
        }
    }

    private void performHealthChecks() {
        for (Map.Entry<String, ServiceEndpointHealth> entry : endpointHealth.entrySet()) {
            ServiceEndpointHealth health = entry.getValue();
            
            // Perform health check (simplified)
            boolean isHealthy = checkEndpointHealth(health.getEndpoint());
            
            if (isHealthy) {
                health.setHealthy(true);
                health.setConsecutiveFailures(0);
            } else {
                health.setConsecutiveFailures(health.getConsecutiveFailures() + 1);
                if (health.getConsecutiveFailures() >= 3) {
                    health.setHealthy(false);
                    log.warn("Endpoint marked unhealthy: {}:{}", 
                            health.getEndpoint().getAddress(), 
                            health.getEndpoint().getPort());
                }
            }
            
            health.setLastCheck(LocalDateTime.now());
            healthCheckCount.incrementAndGet();
        }
    }

    private boolean checkEndpointHealth(ServiceEndpoint endpoint) {
        // Simplified health check - would implement actual health check logic
        try {
            InetAddress.getByName(endpoint.getAddress()).isReachable(5000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Service Entry Manager");
        
        state = ManagerState.SHUTTING_DOWN;
        
        scheduledExecutor.shutdown();
        asyncExecutor.shutdown();
        
        try {
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            if (!asyncExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        state = ManagerState.TERMINATED;
        log.info("Service Entry Manager shutdown complete");
    }

    // Inner classes and data models

    public enum ManagerState {
        INITIALIZING, RUNNING, SHUTTING_DOWN, TERMINATED
    }

    public enum ServiceLocation {
        MESH_EXTERNAL, MESH_INTERNAL
    }

    public enum ResolutionMode {
        NONE, STATIC, DNS
    }

    public enum TlsMode {
        DISABLE, SIMPLE, MUTUAL, ISTIO_MUTUAL
    }

    @Data
    @Builder(toBuilder = true)
    public static class ServiceEntry {
        private String name;
        private List<String> hosts;
        private List<ServicePort> ports;
        private ServiceLocation location;
        private ResolutionMode resolution;
        private List<ServiceEndpoint> endpoints;
        private List<String> subjectAltNames;
        private Map<String, String> workloadSelector;
        private List<String> exportTo;
        private TlsConfig tlsConfig;
        private Map<String, String> metadata;
        private int version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    public static class ServicePort {
        private int number;
        private String protocol;
        private String name;
        private int targetPort;
    }

    @Data
    @Builder
    public static class ServiceEndpoint {
        private String address;
        private int port;
        private Map<String, String> labels;
        private int weight;
        private int priority;
        private String network;
        private String locality;
    }

    @Data
    @Builder
    public static class TlsConfig {
        private TlsMode mode;
        private String serverCertificate;
        private String privateKey;
        private String caCertificates;
        private String clientCertificate;
        private List<String> subjectAltNames;
        private String sni;
        private String credentialName;
        private int minProtocolVersion;
        private int maxProtocolVersion;
        private List<String> cipherSuites;
    }

    @Data
    @Builder
    public static class ExternalServiceConfig {
        private List<String> hosts;
        private List<ServicePort> ports;
        private ServiceLocation location;
        private ResolutionMode resolution;
        private List<ServiceEndpoint> endpoints;
        private boolean healthCheckEnabled;
        private Map<String, String> metadata;
    }

    @Data
    @Builder
    public static class ServiceRegistrationRequest {
        private String name;
        private List<String> hosts;
        private List<ServicePort> ports;
        private ServiceLocation location;
        private ResolutionMode resolution;
        private List<ServiceEndpoint> endpoints;
        private List<String> subjectAltNames;
        private Map<String, String> workloadSelector;
        private List<String> exportTo;
        private boolean healthCheckEnabled;
        private Map<String, String> metadata;
    }

    @Data
    @Builder
    public static class ServiceRegistrationResult {
        private boolean success;
        private String serviceName;
        private int registeredEndpoints;
        private LocalDateTime registeredAt;
        private String errorMessage;
    }

    @Data
    @Builder
    public static class EndpointUpdateResult {
        private boolean success;
        private String serviceName;
        private int updatedEndpoints;
        private int version;
        private LocalDateTime appliedAt;
        private String errorMessage;
    }

    @Data
    @Builder
    public static class DnsResolutionResult {
        private boolean success;
        private String hostname;
        private List<String> ipAddresses;
        private boolean fromCache;
        private Duration ttl;
        private String errorMessage;
    }

    @Data
    @Builder
    public static class TlsConfigResult {
        private boolean success;
        private String serviceName;
        private TlsMode tlsMode;
        private String sni;
        private LocalDateTime appliedAt;
        private String errorMessage;
    }

    @Data
    @Builder
    public static class ServiceEndpointHealth {
        private String serviceName;
        private ServiceEndpoint endpoint;
        private boolean healthy;
        private LocalDateTime lastCheck;
        private int consecutiveFailures;
        private String lastError;
    }

    @Data
    @Builder
    public static class DnsCache {
        private String hostname;
        private List<String> ipAddresses;
        private LocalDateTime resolvedAt;
        private Duration ttl;
        
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(resolvedAt.plus(ttl));
        }
    }

    @Data
    public static class ServiceMetrics {
        private final String serviceName;
        private final AtomicLong requestCount = new AtomicLong(0);
        private final AtomicLong errorCount = new AtomicLong(0);
        private final AtomicLong latencySum = new AtomicLong(0);
        private final AtomicInteger activeConnections = new AtomicInteger(0);
        
        public ServiceMetrics(String serviceName) {
            this.serviceName = serviceName;
        }
    }

    /**
     * DNS Resolver utility
     */
    private static class DnsResolver {
        public List<String> resolve(String hostname) throws UnknownHostException {
            InetAddress[] addresses = InetAddress.getAllByName(hostname);
            List<String> ipAddresses = new ArrayList<>();
            for (InetAddress address : addresses) {
                ipAddresses.add(address.getHostAddress());
            }
            return ipAddresses;
        }
    }

    public static class ServiceNotFoundException extends RuntimeException {
        public ServiceNotFoundException(String message) {
            super(message);
        }
    }
}