package com.waqiti.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized database configuration service that replaces hardcoded URLs
 * with environment-based configuration for production readiness.
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "waqiti.database")
public class DatabaseConfigurationService {

    private final Environment environment;
    
    public DatabaseConfigurationService(Environment environment) {
        this.environment = environment;
    }

    // Database configuration properties
    @Value("${WAQITI_DB_HOST:localhost}")
    private String dbHost;
    
    @Value("${WAQITI_DB_PORT:5432}")
    private String dbPort;
    
    @Value("${WAQITI_DB_USERNAME:app_user}")
    private String dbUsername;
    
    @Value("${WAQITI_DB_PASSWORD:}")
    private String dbPassword;
    
    @Value("${WAQITI_DB_SSL_MODE:require}")
    private String sslMode;
    
    @Value("${WAQITI_DB_MAX_POOL_SIZE:20}")
    private int maxPoolSize;
    
    @Value("${WAQITI_DB_MIN_POOL_SIZE:5}")
    private int minPoolSize;
    
    @Value("${WAQITI_DB_CONNECTION_TIMEOUT:30000}")
    private int connectionTimeout;
    
    @Value("${WAQITI_DB_IDLE_TIMEOUT:600000}")
    private int idleTimeout;
    
    @Value("${WAQITI_DB_MAX_LIFETIME:1800000}")
    private int maxLifetime;

    /**
     * Get database URL for a specific service.
     * Replaces hardcoded localhost URLs with environment-based configuration.
     */
    public String getDatabaseUrl(String serviceName) {
        String databaseName = getDatabaseName(serviceName);
        String url = buildDatabaseUrl(databaseName);
        
        log.info("Generated database URL for service '{}': {}", serviceName, maskPassword(url));
        return url;
    }
    
    /**
     * Get database configuration properties for a service.
     */
    public DatabaseProperties getDatabaseProperties(String serviceName) {
        String url = getDatabaseUrl(serviceName);
        
        return DatabaseProperties.builder()
            .url(url)
            .username(dbUsername)
            .password(dbPassword)
            .driverClassName("org.postgresql.Driver")
            .maxPoolSize(maxPoolSize)
            .minPoolSize(minPoolSize)
            .connectionTimeout(connectionTimeout)
            .idleTimeout(idleTimeout)
            .maxLifetime(maxLifetime)
            .build();
    }
    
    /**
     * Build complete database URL with all parameters.
     */
    private String buildDatabaseUrl(String databaseName) {
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append("jdbc:postgresql://")
                  .append(dbHost)
                  .append(":")
                  .append(dbPort)
                  .append("/")
                  .append(databaseName);
        
        // Add connection parameters
        urlBuilder.append("?sslmode=").append(sslMode);
        urlBuilder.append("&ApplicationName=").append("waqiti-").append(databaseName);
        urlBuilder.append("&connectTimeout=").append(connectionTimeout / 1000); // Convert to seconds
        urlBuilder.append("&socketTimeout=").append(connectionTimeout / 1000);
        urlBuilder.append("&tcpKeepAlive=true");
        urlBuilder.append("&reWriteBatchedInserts=true");
        urlBuilder.append("&prepareThreshold=1");
        
        // Add performance optimizations
        if (isProductionEnvironment()) {
            urlBuilder.append("&stringtype=unspecified"); // Better performance
            urlBuilder.append("&binaryTransfer=true");
        }
        
        return urlBuilder.toString();
    }
    
    /**
     * Get database name for a service with proper naming convention.
     */
    private String getDatabaseName(String serviceName) {
        // Service name to database name mapping
        Map<String, String> serviceDbMapping = new HashMap<>();
        serviceDbMapping.put("payment-service", "waqiti_payments");
        serviceDbMapping.put("user-service", "waqiti_users");
        serviceDbMapping.put("wallet-service", "waqiti_wallets");
        serviceDbMapping.put("transaction-service", "waqiti_transactions");
        serviceDbMapping.put("compliance-service", "waqiti_compliance");
        serviceDbMapping.put("notification-service", "waqiti_notifications");
        serviceDbMapping.put("analytics-service", "waqiti_analytics");
        serviceDbMapping.put("support-service", "waqiti_support");
        serviceDbMapping.put("reconciliation-service", "waqiti_reconciliation");
        serviceDbMapping.put("fraud-detection-service", "waqiti_fraud_detection");
        serviceDbMapping.put("audit-service", "waqiti_audit");
        
        String dbName = serviceDbMapping.get(serviceName);
        if (dbName != null) {
            return dbName;
        }
        
        // Default naming pattern for unlisted services
        return "waqiti_" + serviceName.replace("-service", "").replace("-", "_");
    }
    
    /**
     * Check if running in production environment.
     */
    private boolean isProductionEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if ("prod".equals(profile) || "production".equals(profile)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Mask password in URL for logging.
     */
    private String maskPassword(String url) {
        return url.replaceAll("password=[^&]*", "password=***");
    }
    
    /**
     * Validate database configuration.
     */
    public void validateConfiguration() {
        if (dbPassword == null || dbPassword.trim().isEmpty()) {
            if (isProductionEnvironment()) {
                throw new IllegalStateException("Database password must be provided in production environment");
            }
            log.warn("Database password not set - using default for development");
        }
        
        if ("localhost".equals(dbHost) && isProductionEnvironment()) {
            log.warn("Using localhost database host in production environment");
        }
        
        log.info("Database configuration validated successfully");
    }
    
    /**
     * Get read-only replica configuration if available.
     */
    public DatabaseProperties getReadOnlyProperties(String serviceName) {
        String readOnlyHost = environment.getProperty("WAQITI_DB_READONLY_HOST", dbHost);
        String readOnlyPort = environment.getProperty("WAQITI_DB_READONLY_PORT", dbPort);
        
        if (!readOnlyHost.equals(dbHost) || !readOnlyPort.equals(dbPort)) {
            log.info("Using read-only replica for service: {}", serviceName);
            
            String databaseName = getDatabaseName(serviceName);
            String readOnlyUrl = String.format("jdbc:postgresql://%s:%s/%s?sslmode=%s&readOnly=true", 
                readOnlyHost, readOnlyPort, databaseName, sslMode);
            
            return DatabaseProperties.builder()
                .url(readOnlyUrl)
                .username(environment.getProperty("WAQITI_DB_READONLY_USERNAME", dbUsername))
                .password(environment.getProperty("WAQITI_DB_READONLY_PASSWORD", dbPassword))
                .driverClassName("org.postgresql.Driver")
                .maxPoolSize(maxPoolSize / 2) // Smaller pool for read-only
                .minPoolSize(Math.max(1, minPoolSize / 2))
                .connectionTimeout(connectionTimeout)
                .idleTimeout(idleTimeout)
                .maxLifetime(maxLifetime)
                .readOnly(true)
                .build();
        }
        
        return getDatabaseProperties(serviceName);
    }
    
    /**
     * Database properties data class.
     */
    public static class DatabaseProperties {
        private String url;
        private String username;
        private String password;
        private String driverClassName;
        private int maxPoolSize;
        private int minPoolSize;
        private int connectionTimeout;
        private int idleTimeout;
        private int maxLifetime;
        private boolean readOnly = false;
        
        // Builder pattern implementation
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private DatabaseProperties properties = new DatabaseProperties();
            
            public Builder url(String url) {
                properties.url = url;
                return this;
            }
            
            public Builder username(String username) {
                properties.username = username;
                return this;
            }
            
            public Builder password(String password) {
                properties.password = password;
                return this;
            }
            
            public Builder driverClassName(String driverClassName) {
                properties.driverClassName = driverClassName;
                return this;
            }
            
            public Builder maxPoolSize(int maxPoolSize) {
                properties.maxPoolSize = maxPoolSize;
                return this;
            }
            
            public Builder minPoolSize(int minPoolSize) {
                properties.minPoolSize = minPoolSize;
                return this;
            }
            
            public Builder connectionTimeout(int connectionTimeout) {
                properties.connectionTimeout = connectionTimeout;
                return this;
            }
            
            public Builder idleTimeout(int idleTimeout) {
                properties.idleTimeout = idleTimeout;
                return this;
            }
            
            public Builder maxLifetime(int maxLifetime) {
                properties.maxLifetime = maxLifetime;
                return this;
            }
            
            public Builder readOnly(boolean readOnly) {
                properties.readOnly = readOnly;
                return this;
            }
            
            public DatabaseProperties build() {
                return properties;
            }
        }
        
        // Getters
        public String getUrl() { return url; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getDriverClassName() { return driverClassName; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public int getMinPoolSize() { return minPoolSize; }
        public int getConnectionTimeout() { return connectionTimeout; }
        public int getIdleTimeout() { return idleTimeout; }
        public int getMaxLifetime() { return maxLifetime; }
        public boolean isReadOnly() { return readOnly; }
    }
}