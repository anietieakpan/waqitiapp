package com.waqiti.common.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.Map;

/**
 * Configuration properties for HashiCorp Vault integration
 */
@Data
@Validated
@ConfigurationProperties(prefix = "waqiti.security.vault")
public class VaultProperties {
    
    /**
     * Enable Vault integration
     */
    private boolean enabled = false;
    
    /**
     * Vault server URL
     */
    @NotBlank
    private String uri = "http://localhost:8200";
    
    /**
     * Authentication method
     */
    private Authentication authentication = new Authentication();
    
    /**
     * KV (Key-Value) engine settings
     */
    private Kv kv = new Kv();
    
    /**
     * Database secrets engine settings
     */
    private Database database = new Database();
    
    /**
     * Connection settings
     */
    private Connection connection = new Connection();
    
    /**
     * SSL settings
     */
    private Ssl ssl = new Ssl();
    
    @Data
    public static class Authentication {
        /**
         * Authentication method: token, approle, kubernetes, aws, azure
         */
        private String method = "token";
        
        /**
         * Static token (for development only)
         */
        private String token;
        
        /**
         * AppRole authentication settings
         */
        private AppRole appRole = new AppRole();
        
        /**
         * Kubernetes authentication settings
         */
        private Kubernetes kubernetes = new Kubernetes();
        
        @Data
        public static class AppRole {
            private String roleId;
            private String secretId;
            private String path = "auth/approle";
        }
        
        @Data
        public static class Kubernetes {
            private String role;
            private String serviceAccountTokenPath = "/var/run/secrets/kubernetes.io/serviceaccount/token";
            private String path = "auth/kubernetes";
        }
    }
    
    @Data
    public static class Kv {
        /**
         * KV engine version (1 or 2)
         */
        private int version = 2;
        
        /**
         * Backend path
         */
        private String backend = "secret";
        
        /**
         * Default context path for application secrets
         */
        private String defaultContext = "waqiti";
        
        /**
         * Application name for secret paths
         */
        private String applicationName = "waqiti-platform";
        
        /**
         * Profiles to load secrets for
         */
        private String[] profiles = {"default"};
    }
    
    @Data
    public static class Database {
        /**
         * Enable database secrets engine
         */
        private boolean enabled = false;
        
        /**
         * Database backend path
         */
        private String backend = "database";
        
        /**
         * Database role for secret generation
         */
        private String role = "waqiti-db-role";
        
        /**
         * TTL for database credentials
         */
        @Min(300)
        @Max(86400)
        private int ttlSeconds = 3600;
    }
    
    @Data
    public static class Connection {
        /**
         * Connection timeout in milliseconds
         */
        @Min(1000)
        @Max(30000)
        private int timeoutMs = 5000;
        
        /**
         * Read timeout in milliseconds
         */
        @Min(1000)
        @Max(60000)
        private int readTimeoutMs = 15000;
        
        /**
         * Maximum number of HTTP connections
         */
        @Min(1)
        @Max(100)
        private int maxConnections = 10;
        
        /**
         * Maximum number of HTTP connections per route
         */
        @Min(1)
        @Max(50)
        private int maxConnectionsPerRoute = 5;
    }
    
    @Data
    public static class Ssl {
        /**
         * Trust store location
         */
        private String trustStore;
        
        /**
         * Trust store password
         */
        private String trustStorePassword;
        
        /**
         * Trust store type
         */
        private String trustStoreType = "JKS";
        
        /**
         * Key store location (for client certificate authentication)
         */
        private String keyStore;
        
        /**
         * Key store password
         */
        private String keyStorePassword;
        
        /**
         * Key store type
         */
        private String keyStoreType = "JKS";
        
        /**
         * Verify SSL certificates
         */
        private boolean verifyHostname = true;
    }
}