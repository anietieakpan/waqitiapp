package com.waqiti.common.servicemesh;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Central Service Mesh Configuration
 * Provides integration with Istio/Envoy service mesh for:
 * - Service discovery and load balancing
 * - Traffic management and routing
 * - Security (mTLS, authorization)
 * - Observability (metrics, tracing, logging)
 * - Resilience (retries, circuit breaking, timeouts)
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableScheduling
@EnableConfigurationProperties(ServiceMeshProperties.class)
@ConditionalOnProperty(prefix = "waqiti.service-mesh", name = "enabled", havingValue = "true")
public class ServiceMeshConfiguration {

    private final ServiceMeshProperties properties;
    private final MeterRegistry meterRegistry;
    private final DiscoveryClient discoveryClient;

    /**
     * Service Mesh Manager - Core orchestrator
     */
    @Bean
    public ServiceMeshManager serviceMeshManager(
            ServiceRegistry serviceRegistry,
            TrafficManager trafficManager,
            SecurityManager securityManager,
            ObservabilityManager observabilityManager) {
        
        log.info("Initializing Service Mesh Manager with mode: {}", properties.getMode());
        
        return ServiceMeshManager.builder()
                .properties(properties)
                .serviceRegistry(serviceRegistry)
                .trafficManager(trafficManager)
                .securityManager(securityManager)
                .observabilityManager(observabilityManager)
                .meterRegistry(meterRegistry)
                .build();
    }

    /**
     * Service Registry for service discovery and registration
     */
    @Bean
    public ServiceRegistry serviceRegistry() {
        return ServiceRegistry.builder()
                .discoveryClient(discoveryClient)
                .properties(properties)
                .healthCheckInterval(Duration.ofSeconds(properties.getHealthCheck().getInterval()))
                .deregistrationDelay(Duration.ofSeconds(properties.getHealthCheck().getDeregistrationDelay()))
                .build();
    }

    /**
     * Circuit Breaker Registry for resilience
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        return CircuitBreakerRegistry.ofDefaults();
    }

    /**
     * Retry Registry for resilience
     */
    @Bean
    public RetryRegistry retryRegistry() {
        return RetryRegistry.ofDefaults();
    }

    /**
     * Traffic Manager for intelligent routing and load balancing
     */
    @Bean
    public TrafficManager trafficManager(CircuitBreakerRegistry circuitBreakerRegistry, 
                                          RetryRegistry retryRegistry) {
        return TrafficManager.builder()
                .loadBalancingStrategy(properties.getTraffic().getLoadBalancingStrategy())
                .retryPolicy(createRetryTemplate())
                .circuitBreakerThreshold(properties.getTraffic().getCircuitBreaker().getThreshold())
                .timeoutDuration(Duration.ofMillis(properties.getTraffic().getTimeoutMs()))
                .canaryEnabled(properties.getTraffic().getCanary().isEnabled())
                .canaryPercentage(properties.getTraffic().getCanary().getPercentage())
                .meterRegistry(meterRegistry)
                .circuitBreakerRegistry(circuitBreakerRegistry)
                .retryRegistry(retryRegistry)
                .build();
    }

    /**
     * Security Manager for mTLS and authorization
     */
    @Bean
    public SecurityManager securityManager() {
        return SecurityManager.builder()
                .mtlsEnabled(properties.getSecurity().isMtlsEnabled())
                .certificatePath(properties.getSecurity().getCertificatePath())
                .privateKeyPath(properties.getSecurity().getPrivateKeyPath())
                .caPath(properties.getSecurity().getCaPath())
                .authorizationEnabled(properties.getSecurity().isAuthorizationEnabled())
                .jwtValidation(properties.getSecurity().isJwtValidation())
                .build();
    }

    /**
     * Observability Manager for metrics, tracing, and logging
     */
    @Bean
    public ObservabilityManager observabilityManager() {
        return ObservabilityManager.builder()
                .meterRegistry(meterRegistry)
                .tracingEnabled(properties.getObservability().isTracingEnabled())
                .tracingEndpoint(properties.getObservability().getTracingEndpoint())
                .metricsEnabled(properties.getObservability().isMetricsEnabled())
                .metricsPort(properties.getObservability().getMetricsPort())
                .accessLogEnabled(properties.getObservability().isAccessLogEnabled())
                .build();
    }

    /**
     * Sidecar Proxy Manager for Envoy integration
     */
    @Bean
    @ConditionalOnProperty(prefix = "waqiti.service-mesh", name = "sidecar.enabled", havingValue = "true")
    public SidecarProxyManager sidecarProxyManager() {
        return new SidecarProxyManager(
                properties.getSidecar().getAdminPort(),
                properties.getSidecar().getXdsPort(),
                properties.getSidecar().getHealthCheckPath(),
                properties.getSidecar().getMetricsPath(),
                meterRegistry);
    }

    /**
     * Virtual Service Manager for traffic routing rules
     */
    @Bean
    public VirtualServiceManager virtualServiceManager() {
        return VirtualServiceManager.builder()
                .namespace(properties.getNamespace())
                .version(properties.getVersion())
                .properties(properties)
                .build();
    }

    /**
     * Destination Rule Manager for traffic policies
     */
    @Bean
    public DestinationRuleManager destinationRuleManager() {
        return DestinationRuleManager.builder()
                .connectionPoolSize(properties.getTraffic().getConnectionPool().getMaxConnections())
                .maxRequestsPerConnection(properties.getTraffic().getConnectionPool().getMaxRequestsPerConnection())
                .consecutiveErrors(properties.getTraffic().getCircuitBreaker().getConsecutiveErrors())
                .interval(Duration.ofSeconds(properties.getTraffic().getCircuitBreaker().getInterval()))
                .baseEjectionTime(Duration.ofSeconds(properties.getTraffic().getCircuitBreaker().getBaseEjectionTime()))
                .build();
    }

    /**
     * Service Entry Manager for external service registration
     */
    @Bean
    public ServiceEntryManager serviceEntryManager() {
        // Convert list to map
        Map<String, ServiceEntryManager.ExternalServiceConfig> servicesMap = new HashMap<>();
        if (properties.getExternalServices() != null) {
            properties.getExternalServices().forEach(service -> {
                // Create a list with single host and port for compatibility
                List<ServiceEntryManager.ServicePort> ports = new ArrayList<>();
                ports.add(ServiceEntryManager.ServicePort.builder()
                        .number(service.getPort())
                        .protocol(service.getProtocol())
                        .name(service.getProtocol().toLowerCase())
                        .build());
                
                List<String> hosts = new ArrayList<>();
                hosts.add(service.getHost());
                
                servicesMap.put(service.getName(), 
                    ServiceEntryManager.ExternalServiceConfig.builder()
                        .hosts(hosts)
                        .ports(ports)
                        .location(ServiceEntryManager.ServiceLocation.MESH_EXTERNAL)
                        .resolution(ServiceEntryManager.ResolutionMode.DNS)
                        .healthCheckEnabled(false)
                        .metadata(service.getHeaders())
                        .build());
            });
        }
        
        return ServiceEntryManager.builder()
                .properties(properties)
                .externalServices(servicesMap)
                .build();
    }

    /**
     * Gateway Manager for ingress/egress configuration
     */
    @Bean
    public GatewayManager gatewayManager() {
        return GatewayManager.builder()
                .ingressEnabled(properties.getGateway().isIngressEnabled())
                .egressEnabled(properties.getGateway().isEgressEnabled())
                .ingressPort(properties.getGateway().getIngressPort())
                .egressPort(properties.getGateway().getEgressPort())
                .hosts(properties.getGateway().getHosts())
                .build();
    }

    /**
     * Policy Manager for rate limiting and access control
     */
    @Bean
    public PolicyManager policyManager() {
        return PolicyManager.builder()
                .name(properties.getServiceName() + "-policy")
                .rateLimitEnabled(properties.getPolicy().isRateLimitEnabled())
                .rateLimitRequests(properties.getPolicy().getRateLimitRequests())
                .rateLimitPeriodSeconds(properties.getPolicy().getRateLimitPeriod())
                .corsEnabled(properties.getPolicy().isCorsEnabled())
                .corsOrigins(properties.getPolicy().getCorsOrigins())
                .build();
    }

    /**
     * Telemetry Manager for distributed tracing and metrics
     */
    @Bean
    public TelemetryManager telemetryManager() {
        return TelemetryManager.builder()
                .serviceName(properties.getServiceName())
                .meterRegistry(meterRegistry)
                .customTags(properties.getObservability().getCustomTags())
                .samplingRate(properties.getObservability().getSamplingRate())
                .build();
    }

    /**
     * Fault Injection Manager for chaos engineering
     */
    @Bean
    @ConditionalOnProperty(prefix = "waqiti.service-mesh.fault-injection", name = "enabled", havingValue = "true")
    public FaultInjectionManager faultInjectionManager() {
        return FaultInjectionManager.builder()
                .delayEnabled(properties.getFaultInjection().isDelayEnabled())
                .delayPercentage(properties.getFaultInjection().getDelayPercentage())
                .delayDuration(Duration.ofMillis(properties.getFaultInjection().getDelayMs()))
                .abortEnabled(properties.getFaultInjection().isAbortEnabled())
                .abortPercentage(properties.getFaultInjection().getAbortPercentage())
                .abortHttpStatus(properties.getFaultInjection().getAbortHttpStatus())
                .build();
    }

    /**
     * Service Mesh Health Monitor
     */
    @Bean
    public ServiceMeshHealthMonitor serviceMeshHealthMonitor() {
        return new ServiceMeshHealthMonitor(
                properties,
                meterRegistry,
                discoveryClient
        );
    }

    /**
     * Service Mesh Rest Template with built-in resilience
     */
    @Bean(name = "serviceMeshRestTemplate")
    public RestTemplate serviceMeshRestTemplate(
            TrafficManager trafficManager,
            SecurityManager securityManager,
            ObservabilityManager observabilityManager) {
        
        RestTemplate restTemplate = new RestTemplate();
        
        // Add interceptors for service mesh features
        restTemplate.getInterceptors().add(
                new ServiceMeshHttpInterceptor(
                        trafficManager,
                        securityManager,
                        observabilityManager,
                        properties
                )
        );
        
        return restTemplate;
    }

    /**
     * gRPC Channel for service mesh control plane communication
     */
    @Bean
    @ConditionalOnProperty(prefix = "waqiti.service-mesh", name = "control-plane.enabled", havingValue = "true")
    public ManagedChannel controlPlaneChannel() {
        return ManagedChannelBuilder
                .forAddress(
                        properties.getControlPlane().getHost(),
                        properties.getControlPlane().getPort()
                )
                .usePlaintext() // Use TLS in production
                .build();
    }

    /**
     * Scheduled executor for background tasks
     */
    @Bean
    public ScheduledExecutorService serviceMeshScheduler() {
        return Executors.newScheduledThreadPool(
                properties.getScheduler().getThreadPoolSize(),
                r -> {
                    Thread thread = new Thread(r);
                    thread.setName("service-mesh-scheduler-" + thread.getId());
                    thread.setDaemon(true);
                    return thread;
                }
        );
    }

    /**
     * Create retry template for resilience
     */
    private RetryTemplate createRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(properties.getTraffic().getRetry().getMaxAttempts());
        
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(properties.getTraffic().getRetry().getInitialIntervalMs());
        backOffPolicy.setMaxInterval(properties.getTraffic().getRetry().getMaxIntervalMs());
        backOffPolicy.setMultiplier(properties.getTraffic().getRetry().getMultiplier());
        
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        return retryTemplate;
    }

    /**
     * Service Mesh Readiness Indicator
     */
    @Bean
    public ServiceMeshReadinessIndicator serviceMeshReadinessIndicator(
            ServiceMeshManager serviceMeshManager) {
        return new ServiceMeshReadinessIndicator(serviceMeshManager);
    }
}