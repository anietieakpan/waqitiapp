package com.waqiti.common.vault;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enhanced Vault configuration properties for centralized management
 * 
 * Provides comprehensive configuration for Vault integration across all services
 * with support for multiple authentication methods, SSL, retry policies, and more.
 */
@Data
@Component
@ConfigurationProperties(prefix = "vault")
public class VaultConfigurationProperties {

    // Vault paths configuration
    private Paths paths = new Paths();
    
    // Environment configuration
    private Environment environment = new Environment();
    
    // Health check configuration
    private Health health = new Health();
    
    // Metrics configuration
    private Metrics metrics = new Metrics();
    
    // Audit configuration
    private Audit audit = new Audit();
    
    // Retry configuration
    private Retry retry = new Retry();
    
    // Cache configuration
    private Cache cache = new Cache();

    // Authentication configuration
    private String token;
    private AppRoleAuth appRole;
    private KubernetesAuth kubernetes;
    private AwsAuth aws;

    @Data
    public static class AppRoleAuth {
        private String roleId;
        private String secretId;
        private String path = "approle";
    }

    @Data
    public static class KubernetesAuth {
        private String role;
        private String serviceAccountTokenPath = "/var/run/secrets/kubernetes.io/serviceaccount/token";
        private String path = "kubernetes";
    }

    @Data
    public static class AwsAuth {
        private String role;
        private String path = "aws";
        private String region;
        private String serverIdHeader;
    }

    @Data
    public static class Paths {
        // Database credentials
        private String database = "database/${spring.application.name}";
        private String databaseRead = "database/${spring.application.name}-read";
        private String databaseStatic = "database/${spring.application.name}-static";
        
        // JWT secrets
        private String jwt = "jwt/${spring.application.name}";
        private String jwtRefresh = "jwt/${spring.application.name}/refresh";
        
        // API keys and external service credentials
        private String apiKeys = "api-keys/${spring.application.name}";
        private String externalApis = "external-apis";
        private String paymentProviders = "payment-providers";
        
        // Encryption keys
        private String encryption = "encryption/${spring.application.name}";
        private String transitKey = "transit/${spring.application.name}";
        
        // Infrastructure secrets
        private String redis = "infrastructure/redis";
        private String kafka = "infrastructure/kafka";
        private String elasticsearch = "infrastructure/elasticsearch";
        
        // Monitoring and observability
        private String monitoring = "monitoring";
        private String alerts = "alerts";
        
        // Certificates and PKI
        private String pki = "pki/${spring.application.name}";
        private String tlsCerts = "certificates/${spring.application.name}";
        
        // Application-specific secrets
        private String application = "application/${spring.application.name}";
        private String features = "features/${spring.application.name}";
        
        // Custom paths for specific services
        private Map<String, String> custom = new HashMap<>();
    }
    
    @Data
    public static class Environment {
        private String prefix = "${spring.profiles.active:development}";
        private boolean usePrefix = true;
        private Map<String, String> mappings = new HashMap<>();
    }
    
    @Data
    public static class Health {
        private boolean enabled = true;
        private String endpoint = "sys/health";
        private Duration timeout = Duration.ofSeconds(5);
        private Duration interval = Duration.ofMinutes(1);
    }
    
    @Data
    public static class Metrics {
        private boolean enabled = true;
        private String prefix = "vault.client";
        private boolean includePathTag = false;
        private List<String> tags = List.of();
    }
    
    @Data
    public static class Audit {
        private boolean enabled = true;
        private boolean logRequests = false;
        private boolean logResponses = false;
        private boolean logSecrets = false;
        private List<String> excludePaths = List.of("sys/health", "sys/seal-status");
    }
    
    @Data
    public static class Retry {
        private boolean enabled = true;
        private int maxAttempts = 5;
        private Duration initialInterval = Duration.ofSeconds(1);
        private Duration maxInterval = Duration.ofSeconds(10);
        private double multiplier = 2.0;
        private List<String> retryableExceptions = List.of(
            "org.springframework.vault.VaultException",
            "java.net.ConnectException",
            "java.net.SocketTimeoutException"
        );
    }
    
    @Data
    public static class Cache {
        private boolean enabled = true;
        private Duration ttl = Duration.ofMinutes(5);
        private int maxSize = 1000;
        private boolean refreshAsync = true;
        private Duration refreshThreshold = Duration.ofMinutes(1);
    }
    
    /**
     * Get the full path for a given secret type with environment prefix
     */
    public String getFullPath(String secretType) {
        String basePath = getBasePath(secretType);
        if (environment.isUsePrefix() && environment.getPrefix() != null) {
            return environment.getPrefix() + "/" + basePath;
        }
        return basePath;
    }
    
    /**
     * Get the base path for a given secret type
     */
    private String getBasePath(String secretType) {
        switch (secretType.toLowerCase()) {
            case "database":
                return paths.getDatabase();
            case "database-read":
                return paths.getDatabaseRead();
            case "database-static":
                return paths.getDatabaseStatic();
            case "jwt":
                return paths.getJwt();
            case "jwt-refresh":
                return paths.getJwtRefresh();
            case "api-keys":
                return paths.getApiKeys();
            case "external-apis":
                return paths.getExternalApis();
            case "payment-providers":
                return paths.getPaymentProviders();
            case "encryption":
                return paths.getEncryption();
            case "transit-key":
                return paths.getTransitKey();
            case "redis":
                return paths.getRedis();
            case "kafka":
                return paths.getKafka();
            case "elasticsearch":
                return paths.getElasticsearch();
            case "monitoring":
                return paths.getMonitoring();
            case "alerts":
                return paths.getAlerts();
            case "pki":
                return paths.getPki();
            case "tls-certs":
                return paths.getTlsCerts();
            case "application":
                return paths.getApplication();
            case "features":
                return paths.getFeatures();
            default:
                return paths.getCustom().getOrDefault(secretType, secretType);
        }
    }
    
    /**
     * Check if a secret type has environment-specific configuration
     */
    public boolean hasEnvironmentMapping(String secretType) {
        return environment.getMappings().containsKey(secretType);
    }
    
    /**
     * Get environment-specific configuration for a secret type
     */
    public String getEnvironmentMapping(String secretType) {
        return environment.getMappings().get(secretType);
    }
}