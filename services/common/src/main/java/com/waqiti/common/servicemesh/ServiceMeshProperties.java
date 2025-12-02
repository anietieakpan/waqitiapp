package com.waqiti.common.servicemesh;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service Mesh Configuration Properties
 * Centralizes all service mesh settings including traffic management,
 * security, observability, and control plane configuration
 */
@Data
@Component
@ConfigurationProperties(prefix = "waqiti.service-mesh")
public class ServiceMeshProperties {

    /**
     * Enable/disable service mesh features
     */
    private boolean enabled = true;

    /**
     * Service mesh mode: ISTIO, LINKERD, CONSUL, CUSTOM
     */
    private ServiceMeshMode mode = ServiceMeshMode.ISTIO;

    /**
     * Service name for identification in the mesh
     */
    private String serviceName;

    /**
     * Service version for canary deployments
     */
    private String version = "v1";

    /**
     * Kubernetes namespace
     */
    private String namespace = "waqiti";

    /**
     * External services configuration
     */
    private List<ExternalService> externalServices = new ArrayList<>();

    /**
     * Traffic management configuration
     */
    private TrafficConfig traffic = new TrafficConfig();

    /**
     * Security configuration
     */
    private SecurityConfig security = new SecurityConfig();

    /**
     * Observability configuration
     */
    private ObservabilityConfig observability = new ObservabilityConfig();

    /**
     * Sidecar proxy configuration
     */
    private SidecarConfig sidecar = new SidecarConfig();

    /**
     * Gateway configuration
     */
    private GatewayConfig gateway = new GatewayConfig();

    /**
     * Policy configuration
     */
    private PolicyConfig policy = new PolicyConfig();

    /**
     * Health check configuration
     */
    private HealthCheckConfig healthCheck = new HealthCheckConfig();

    /**
     * Control plane configuration
     */
    private ControlPlaneConfig controlPlane = new ControlPlaneConfig();

    /**
     * Fault injection configuration for chaos engineering
     */
    private FaultInjectionConfig faultInjection = new FaultInjectionConfig();

    /**
     * Scheduler configuration
     */
    private SchedulerConfig scheduler = new SchedulerConfig();

    /**
     * Service mesh mode enumeration
     */
    public enum ServiceMeshMode {
        ISTIO,
        LINKERD,
        CONSUL,
        CUSTOM
    }

    /**
     * Load balancing strategy enumeration
     */
    public enum LoadBalancingStrategy {
        ROUND_ROBIN,
        LEAST_REQUEST,
        RANDOM,
        CONSISTENT_HASH,
        WEIGHTED
    }

    /**
     * Traffic management configuration
     */
    @Data
    public static class TrafficConfig {
        private LoadBalancingStrategy loadBalancingStrategy = LoadBalancingStrategy.ROUND_ROBIN;
        private int timeoutMs = 30000;
        private RetryConfig retry = new RetryConfig();
        private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();
        private CanaryConfig canary = new CanaryConfig();
        private ConnectionPoolConfig connectionPool = new ConnectionPoolConfig();
        private boolean mirroringEnabled = false;
        private double mirroringPercentage = 0.0;
        private String mirroringDestination;
    }

    /**
     * Retry configuration
     */
    @Data
    public static class RetryConfig {
        private int maxAttempts = 3;
        private long initialIntervalMs = 1000;
        private long maxIntervalMs = 10000;
        private double multiplier = 2.0;
        private List<String> retryOn = List.of("5xx", "gateway-error", "connect-failure", "refused-stream");
    }

    /**
     * Circuit breaker configuration
     */
    @Data
    public static class CircuitBreakerConfig {
        private int threshold = 5;
        private int consecutiveErrors = 5;
        private int interval = 30;
        private int baseEjectionTime = 30;
        private int maxEjectionPercent = 50;
        private int minHealthPercent = 50;
        private boolean enabled = true;
    }

    /**
     * Canary deployment configuration
     */
    @Data
    public static class CanaryConfig {
        private boolean enabled = false;
        private int percentage = 10;
        private String headerMatch;
        private Map<String, String> labels = new HashMap<>();
        private boolean sessionAffinity = false;
    }

    /**
     * Connection pool configuration
     */
    @Data
    public static class ConnectionPoolConfig {
        private int maxConnections = 100;
        private int maxRequestsPerConnection = 2;
        private int maxPendingRequests = 64;
        private int connectTimeoutSeconds = 10;
        private int h2MaxRequests = 100;
        private boolean http2Enabled = true;
    }

    /**
     * Security configuration
     */
    @Data
    public static class SecurityConfig {
        private boolean mtlsEnabled = true;
        private String certificatePath = "/etc/certs/cert-chain.pem";
        private String privateKeyPath = "/etc/certs/key.pem";
        private String caPath = "/etc/certs/root-cert.pem";
        private boolean authorizationEnabled = true;
        private boolean jwtValidation = true;
        private String jwtIssuer;
        private String jwksUri;
        private List<String> allowedDomains = new ArrayList<>();
        private boolean strictMode = false;
    }

    /**
     * Observability configuration
     */
    @Data
    public static class ObservabilityConfig {
        private boolean tracingEnabled = true;
        private String tracingEndpoint = "http://jaeger-collector:14268/api/traces";
        private double samplingRate = 0.1;
        private boolean metricsEnabled = true;
        private int metricsPort = 15090;
        private boolean accessLogEnabled = true;
        private String accessLogFormat = "JSON";
        private Map<String, String> customTags = new HashMap<>();
        private boolean distributedTracingEnabled = true;
        private List<String> propagationHeaders = List.of("x-correlation-id", "x-request-id", "x-b3-traceid");
    }

    /**
     * Sidecar proxy configuration
     */
    @Data
    public static class SidecarConfig {
        private boolean enabled = true;
        private int adminPort = 15000;
        private int xdsPort = 15010;
        private String healthCheckPath = "/ready";
        private String metricsPath = "/stats/prometheus";
        private int proxyPort = 15001;
        private boolean interceptAllTraffic = true;
        private List<String> excludedPorts = List.of("15090", "15021");
        private List<String> excludedIPs = new ArrayList<>();
    }

    /**
     * Gateway configuration
     */
    @Data
    public static class GatewayConfig {
        private boolean ingressEnabled = true;
        private boolean egressEnabled = false;
        private int ingressPort = 443;
        private int egressPort = 443;
        private List<String> hosts = List.of("api.waqiti.com");
        private boolean httpsRedirect = true;
        private String tlsMode = "SIMPLE";
        private String serverCertificate;
        private String privateKey;
    }

    /**
     * Policy configuration
     */
    @Data
    public static class PolicyConfig {
        private boolean rateLimitEnabled = true;
        private int rateLimitRequests = 100;
        private int rateLimitPeriod = 60;
        private boolean corsEnabled = true;
        private List<String> corsOrigins = List.of("https://api.example.com", "https://api.example.com");
        private List<String> corsMethods = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS");
        private List<String> corsHeaders = List.of("Content-Type", "Authorization", "X-Correlation-Id");
        private boolean authenticationRequired = true;
        private boolean authorizationRequired = true;
    }

    /**
     * Health check configuration
     */
    @Data
    public static class HealthCheckConfig {
        private int interval = 30;
        private int timeout = 10;
        private int unhealthyThreshold = 3;
        private int healthyThreshold = 2;
        private String path = "/actuator/health";
        private String protocol = "HTTP";
        private int deregistrationDelay = 30;
    }

    /**
     * Control plane configuration
     */
    @Data
    public static class ControlPlaneConfig {
        private boolean enabled = true;
        private String host = "istiod.istio-system.svc.cluster.local";
        private int port = 15010;
        private boolean secureConnection = true;
        private String apiVersion = "v1";
        private int syncInterval = 30;
    }

    /**
     * Fault injection configuration
     */
    @Data
    public static class FaultInjectionConfig {
        private boolean enabled = false;
        private boolean delayEnabled = false;
        private int delayPercentage = 10;
        private long delayMs = 5000;
        private boolean abortEnabled = false;
        private int abortPercentage = 5;
        private int abortHttpStatus = 503;
        private Map<String, FaultRule> customRules = new HashMap<>();
    }

    /**
     * Custom fault rule
     */
    @Data
    public static class FaultRule {
        private String path;
        private String method;
        private int delayPercentage;
        private long delayMs;
        private int abortPercentage;
        private int abortHttpStatus;
    }

    /**
     * Scheduler configuration
     */
    @Data
    public static class SchedulerConfig {
        private int threadPoolSize = 10;
        private boolean autoScaling = true;
        private int minThreads = 5;
        private int maxThreads = 20;
    }

    /**
     * External service configuration
     */
    @Data
    public static class ExternalService {
        private String name;
        private String host;
        private int port;
        private String protocol = "HTTPS";
        private boolean mtlsRequired = false;
        private Map<String, String> headers = new HashMap<>();
        private int timeoutMs = 30000;
        private RetryConfig retry = new RetryConfig();
    }
}