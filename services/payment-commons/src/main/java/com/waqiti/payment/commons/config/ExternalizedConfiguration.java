package com.waqiti.payment.commons.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;
import java.util.List;
import java.util.HashMap;

/**
 * Production-Ready Externalized Configuration for Waqiti Platform
 * Replaces ALL hardcoded configurations with environment-aware settings
 * 
 * @author Waqiti Platform Team
 * @version 4.0.0
 * @since 2025-01-17
 */
@Data
@Slf4j
@Validated
@RefreshScope
@Configuration
@ConfigurationProperties(prefix = "waqiti.payment.commons")
public class ExternalizedConfiguration {

    // Service Discovery & Registry
    private ServiceDiscovery serviceDiscovery = new ServiceDiscovery();

    // Database Configuration
    private DatabaseConfig database = new DatabaseConfig();

    // Messaging Configuration
    private MessagingConfig messaging = new MessagingConfig();

    // Cache Configuration
    private CacheConfig cache = new CacheConfig();

    // Security Configuration
    private SecurityConfig security = new SecurityConfig();

    // API Gateway Configuration
    private ApiGatewayConfig apiGateway = new ApiGatewayConfig();

    // ML Services Configuration
    private MLConfig mlServices = new MLConfig();

    // External Services Configuration
    private ExternalServices external = new ExternalServices();

    // Monitoring & Observability
    private ObservabilityConfig observability = new ObservabilityConfig();

    // Payment Processing Configuration
    private PaymentConfig payment = new PaymentConfig();

    @Data
    public static class ServiceDiscovery {
        @NotBlank
        private String consulHost = "${CONSUL_HOST:consul.service.consul}";
        
        @Min(1) @Max(65535)
        private int consulPort = 8500;
        
        @NotBlank
        private String eurekaUrl = "${EUREKA_URL:http://eureka.service.consul:8761/eureka}";
        
        private boolean enableServiceRegistry = true;
        
        private Map<String, String> serviceUrls = new HashMap<>() {{
            put("payment-service", "${PAYMENT_SERVICE_URL:http://payment-service.service.consul:8080}");
            put("fraud-detection-service", "${FRAUD_SERVICE_URL:http://fraud-detection-service.service.consul:8081}");
            put("wallet-service", "${WALLET_SERVICE_URL:http://wallet-service.service.consul:8082}");
            put("notification-service", "${NOTIFICATION_SERVICE_URL:http://notification-service.service.consul:8083}");
            put("user-service", "${USER_SERVICE_URL:http://user-service.service.consul:8084}");
            put("ledger-service", "${LEDGER_SERVICE_URL:http://ledger-service.service.consul:8085}");
            put("compliance-service", "${COMPLIANCE_SERVICE_URL:http://compliance-service.service.consul:8086}");
            put("reconciliation-service", "${RECONCILIATION_SERVICE_URL:http://reconciliation-service.service.consul:8087}");
            put("reporting-service", "${REPORTING_SERVICE_URL:http://reporting-service.service.consul:8088}");
            put("audit-service", "${AUDIT_SERVICE_URL:http://audit-service.service.consul:8089}");
        }};
    }

    @Data
    public static class DatabaseConfig {
        @NotBlank
        private String primaryHost = "${DB_PRIMARY_HOST:postgres-primary.service.consul}";
        
        @Min(1) @Max(65535)
        private int primaryPort = 5432;
        
        @NotBlank
        private String readReplicaHost = "${DB_REPLICA_HOST:postgres-replica.service.consul}";
        
        @Min(1) @Max(65535)
        private int readReplicaPort = 5432;
        
        @NotBlank
        private String database = "${DB_NAME:waqiti}";
        
        @NotBlank
        private String username = "${DB_USERNAME:waqiti_user}";
        
        @NotBlank
        private String password = "${DB_PASSWORD:}";
        
        @Min(10) @Max(200)
        private int maxPoolSize = 50;
        
        @Min(5) @Max(100)
        private int minIdleConnections = 10;
        
        @Min(10000) @Max(60000)
        private int connectionTimeout = 30000;
        
        private boolean enableReadWriteSplitting = true;
        
        private boolean useSSL = true;
        
        @NotBlank
        private String sslMode = "${DB_SSL_MODE:require}";
    }

    @Data
    public static class MessagingConfig {
        @NotBlank
        private String kafkaBootstrapServers = "${KAFKA_BOOTSTRAP_SERVERS:kafka-broker-1.service.consul:9092,kafka-broker-2.service.consul:9092,kafka-broker-3.service.consul:9092}";
        
        @NotBlank
        private String schemaRegistryUrl = "${SCHEMA_REGISTRY_URL:http://schema-registry.service.consul:8081}";
        
        @NotBlank
        private String rabbitHost = "${RABBITMQ_HOST:rabbitmq.service.consul}";
        
        @Min(1) @Max(65535)
        private int rabbitPort = 5672;
        
        @NotBlank
        private String rabbitUsername = "${RABBITMQ_USERNAME:waqiti}";
        
        @NotBlank
        private String rabbitPassword = "${RABBITMQ_PASSWORD:}";
        
        private boolean enableKafkaSSL = true;
        
        @NotBlank
        private String kafkaSecurityProtocol = "${KAFKA_SECURITY_PROTOCOL:SASL_SSL}";
        
        @NotBlank
        private String kafkaSaslMechanism = "${KAFKA_SASL_MECHANISM:PLAIN}";
    }

    @Data
    public static class CacheConfig {
        @NotBlank
        private String redisHost = "${REDIS_HOST:redis-master.service.consul}";
        
        @Min(1) @Max(65535)
        private int redisPort = 6379;
        
        @NotBlank
        private String redisPassword = "${REDIS_PASSWORD:}";
        
        private boolean useRedisSentinel = true;
        
        @NotBlank
        private String redisSentinelMaster = "${REDIS_SENTINEL_MASTER:mymaster}";
        
        @NotBlank
        private String redisSentinelNodes = "${REDIS_SENTINEL_NODES:redis-sentinel-1.service.consul:26379,redis-sentinel-2.service.consul:26379,redis-sentinel-3.service.consul:26379}";
        
        @Min(10) @Max(500)
        private int maxConnections = 100;
        
        @Min(10) @Max(200)
        private int minIdleConnections = 20;
        
        private boolean enableClusterMode = true;
    }

    @Data
    public static class SecurityConfig {
        @NotBlank
        private String keycloakUrl = "${KEYCLOAK_URL:http://keycloak.service.consul:8080}";
        
        @NotBlank
        private String keycloakRealm = "${KEYCLOAK_REALM:waqiti}";
        
        @NotBlank
        private String keycloakClientId = "${KEYCLOAK_CLIENT_ID:waqiti-backend}";
        
        @NotBlank
        private String keycloakClientSecret = "${KEYCLOAK_CLIENT_SECRET:}";
        
        @NotBlank
        private String oauth2IssuerUri = "${OAUTH2_ISSUER_URI:http://keycloak.service.consul:8080/realms/waqiti}";
        
        @NotBlank
        private String jwtSecret = "${JWT_SECRET:}";
        
        @Min(300) @Max(86400)
        private int jwtExpirationSeconds = 3600;
        
        @Min(3600) @Max(2592000)
        private int refreshTokenExpirationSeconds = 604800;
        
        @NotBlank
        private String vaultUrl = "${VAULT_URL:http://vault.service.consul:8200}";
        
        @NotBlank
        private String vaultToken = "${VAULT_TOKEN:}";
        
        @NotBlank
        private String vaultPath = "${VAULT_PATH:secret/waqiti}";
        
        private boolean enableMTLS = true;
        
        @NotBlank
        private String trustStorePath = "${TRUSTSTORE_PATH:/etc/ssl/certs/waqiti-truststore.jks}";
        
        @NotBlank
        private String trustStorePassword = "${TRUSTSTORE_PASSWORD:}";
        
        @NotBlank
        private String keyStorePath = "${KEYSTORE_PATH:/etc/ssl/certs/waqiti-keystore.jks}";
        
        @NotBlank
        private String keyStorePassword = "${KEYSTORE_PASSWORD:}";
    }

    @Data
    public static class ApiGatewayConfig {
        @NotBlank
        private String gatewayUrl = "${API_GATEWAY_URL:http://api-gateway.service.consul:8080}";
        
        @NotBlank
        private String kongAdminUrl = "${KONG_ADMIN_URL:http://kong-admin.service.consul:8001}";
        
        @Min(1) @Max(1000)
        private int globalRateLimitPerSecond = 100;
        
        @Min(1) @Max(10000)
        private int userRateLimitPerMinute = 60;
        
        @Min(1000) @Max(60000)
        private int requestTimeoutMs = 30000;
        
        private boolean enableCircuitBreaker = true;
        
        @Min(5) @Max(100)
        private int circuitBreakerFailureThreshold = 50;
        
        @Min(1000) @Max(60000)
        private int circuitBreakerResetTimeoutMs = 30000;
    }

    @Data
    public static class MLConfig {
        @NotBlank
        private String tensorflowServingUrl = "${TF_SERVING_URL:http://tensorflow-serving.service.consul:8501}";
        
        @NotBlank
        private String sagemakerEndpoint = "${SAGEMAKER_ENDPOINT:https://runtime.sagemaker.${AWS_REGION}.amazonaws.com}";
        
        @NotBlank
        private String mlflowTrackingUri = "${MLFLOW_TRACKING_URI:http://mlflow.service.consul:5000}";
        
        @NotBlank
        private String modelStoragePath = "${MODEL_STORAGE_PATH:s3://waqiti-ml-models/production}";
        
        @Min(10) @Max(1000)
        private int inferenceTimeoutMs = 100;
        
        @DecimalMin("0.0") @DecimalMax("1.0")
        private BigDecimal fraudThresholdHigh = new BigDecimal("0.8");
        
        @DecimalMin("0.0") @DecimalMax("1.0")
        private BigDecimal fraudThresholdMedium = new BigDecimal("0.5");
        
        private boolean enableModelVersioning = true;
        
        @NotBlank
        private String primaryModelVersion = "${ML_PRIMARY_MODEL_VERSION:v3.2.1}";
        
        @NotBlank
        private String fallbackModelVersion = "${ML_FALLBACK_MODEL_VERSION:v2.8.0}";
    }

    @Data
    public static class ExternalServices {
        @NotBlank
        private String plaidUrl = "${PLAID_URL:https://production.plaid.com}";
        
        @NotBlank
        private String plaidClientId = "${PLAID_CLIENT_ID:}";
        
        @NotBlank
        private String plaidSecret = "${PLAID_SECRET:}";
        
        @NotBlank
        private String stripeUrl = "${STRIPE_URL:https://api.stripe.com}";
        
        @NotBlank
        private String stripeSecretKey = "${STRIPE_SECRET_KEY:}";
        
        @NotBlank
        private String twilioAccountSid = "${TWILIO_ACCOUNT_SID:}";
        
        @NotBlank
        private String twilioAuthToken = "${TWILIO_AUTH_TOKEN:}";
        
        @NotBlank
        private String sendgridApiKey = "${SENDGRID_API_KEY:}";
        
        @NotBlank
        private String achProcessorUrl = "${ACH_PROCESSOR_URL:https://api.achprocessor.com}";
        
        @NotBlank
        private String achProcessorApiKey = "${ACH_PROCESSOR_API_KEY:}";
        
        @NotBlank
        private String fedNowUrl = "${FEDNOW_URL:https://api.fednow.org}";
        
        @NotBlank
        private String fedNowCertPath = "${FEDNOW_CERT_PATH:/etc/ssl/certs/fednow.pem}";
        
        @NotBlank
        private String rtpUrl = "${RTP_URL:https://api.theclearinghouse.org/rtp}";
        
        @NotBlank
        private String rtpApiKey = "${RTP_API_KEY:}";
        
        @NotBlank
        private String ofacApiUrl = "${OFAC_API_URL:https://api.ofac.treas.gov}";
        
        @NotBlank
        private String ofacApiKey = "${OFAC_API_KEY:}";
    }

    @Data
    public static class ObservabilityConfig {
        @NotBlank
        private String prometheusUrl = "${PROMETHEUS_URL:http://prometheus.service.consul:9090}";
        
        @NotBlank
        private String grafanaUrl = "${GRAFANA_URL:http://grafana.service.consul:3000}";
        
        @NotBlank
        private String jaegerUrl = "${JAEGER_URL:http://jaeger-collector.service.consul:14268}";
        
        @NotBlank
        private String elasticsearchUrl = "${ELASTICSEARCH_URL:http://elasticsearch.service.consul:9200}";
        
        @NotBlank
        private String kibanaUrl = "${KIBANA_URL:http://kibana.service.consul:5601}";
        
        @NotBlank
        private String openTelemetryEndpoint = "${OTEL_EXPORTER_OTLP_ENDPOINT:http://otel-collector.service.consul:4317}";
        
        private boolean enableMetrics = true;
        private boolean enableTracing = true;
        private boolean enableLogging = true;
        
        @DecimalMin("0.0") @DecimalMax("1.0")
        private BigDecimal tracingSamplingRate = new BigDecimal("0.1");
        
        @NotBlank
        private String logLevel = "${LOG_LEVEL:INFO}";
    }

    @Data
    public static class PaymentConfig {
        @Min(1) @Max(1000000)
        private BigDecimal dailyTransferLimit = new BigDecimal("50000");
        
        @Min(1) @Max(100000)
        private BigDecimal singleTransferLimit = new BigDecimal("10000");
        
        @Min(1) @Max(100)
        private int maxTransactionsPerDay = 50;
        
        @Min(1) @Max(10)
        private int maxRetryAttempts = 3;
        
        @Min(1000) @Max(60000)
        private int paymentTimeoutMs = 30000;
        
        @DecimalMin("0.0") @DecimalMax("10.0")
        private BigDecimal transactionFeePercentage = new BigDecimal("0.5");
        
        @DecimalMin("0.0") @DecimalMax("100.0")
        private BigDecimal minimumTransactionFee = new BigDecimal("0.25");
        
        @DecimalMin("0.0") @DecimalMax("1000.0")
        private BigDecimal maximumTransactionFee = new BigDecimal("25.00");
        
        private boolean enableInstantPayments = true;
        private boolean enableBatchProcessing = true;
        private boolean enableRecurringPayments = true;
        
        @NotBlank
        private String batchProcessingSchedule = "${BATCH_SCHEDULE:0 0 2,10,14,18 * * *}";
    }

    /**
     * Initialize and validate configuration on startup
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("Initializing Waqiti Platform Configuration");
        
        // Validate critical configurations
        validateCriticalConfigurations();
        
        // Log configuration summary (without sensitive data)
        logConfigurationSummary();
        
        log.info("Waqiti Platform Configuration initialized successfully");
    }

    private void validateCriticalConfigurations() {
        // Validate database connection
        if (database.getPassword() == null || database.getPassword().isEmpty()) {
            log.error("CRITICAL: Database password not configured!");
            throw new IllegalStateException("Database password must be configured");
        }
        
        // Validate security keys
        if (security.getJwtSecret() == null || security.getJwtSecret().isEmpty()) {
            log.error("CRITICAL: JWT secret not configured!");
            throw new IllegalStateException("JWT secret must be configured");
        }
        
        // Validate external service credentials
        if (external.getStripeSecretKey() == null || external.getStripeSecretKey().isEmpty()) {
            log.warn("WARNING: Stripe secret key not configured - payment processing may fail");
        }
    }

    private void logConfigurationSummary() {
        log.info("=== Waqiti Platform Configuration Summary ===");
        log.info("Environment: {}", System.getenv("ENVIRONMENT") != null ? System.getenv("ENVIRONMENT") : "development");
        log.info("Service Discovery: Consul={}, Eureka={}", 
            serviceDiscovery.getConsulHost(), 
            serviceDiscovery.isEnableServiceRegistry() ? "enabled" : "disabled");
        log.info("Database: Primary={}, Read Replica={}, SSL={}", 
            database.getPrimaryHost(), 
            database.getReadReplicaHost(),
            database.isUseSSL() ? "enabled" : "disabled");
        log.info("Messaging: Kafka Brokers={}, RabbitMQ={}", 
            messaging.getKafkaBootstrapServers().split(",").length,
            messaging.getRabbitHost());
        log.info("Cache: Redis={}, Cluster Mode={}", 
            cache.isUseRedisSentinel() ? "Sentinel" : cache.getRedisHost(),
            cache.isEnableClusterMode() ? "enabled" : "disabled");
        log.info("Security: OAuth2 Provider={}, mTLS={}", 
            security.getKeycloakUrl(),
            security.isEnableMTLS() ? "enabled" : "disabled");
        log.info("ML Services: TensorFlow={}, SageMaker={}", 
            mlServices.getTensorflowServingUrl(),
            mlServices.getSagemakerEndpoint());
        log.info("Observability: Metrics={}, Tracing={}, Logging={}", 
            observability.isEnableMetrics() ? "enabled" : "disabled",
            observability.isEnableTracing() ? "enabled" : "disabled",
            observability.getLogLevel());
        log.info("Payment Limits: Daily=${}, Single=${}, Max Transactions={}", 
            payment.getDailyTransferLimit(),
            payment.getSingleTransferLimit(),
            payment.getMaxTransactionsPerDay());
        log.info("==============================================");
    }

    /**
     * Get service URL by service name
     */
    public String getServiceUrl(String serviceName) {
        return serviceDiscovery.getServiceUrls().getOrDefault(serviceName, 
            String.format("http://%s.service.consul:8080", serviceName));
    }

    /**
     * Check if running in production environment
     */
    public boolean isProduction() {
        String env = System.getenv("ENVIRONMENT");
        return "production".equalsIgnoreCase(env) || "prod".equalsIgnoreCase(env);
    }

    /**
     * Get database URL with proper SSL configuration
     */
    public String getDatabaseUrl(boolean readReplica) {
        String host = readReplica ? database.getReadReplicaHost() : database.getPrimaryHost();
        int port = readReplica ? database.getReadReplicaPort() : database.getPrimaryPort();
        
        StringBuilder url = new StringBuilder("jdbc:postgresql://")
            .append(host).append(":").append(port)
            .append("/").append(database.getDatabase());
        
        if (database.isUseSSL()) {
            url.append("?ssl=true&sslmode=").append(database.getSslMode());
        }
        
        return url.toString();
    }
}