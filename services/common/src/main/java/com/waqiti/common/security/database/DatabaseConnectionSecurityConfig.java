package com.waqiti.common.security.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive Database Connection Security Configuration
 * Implements industrial-grade security for database connections
 */
@Configuration
@Slf4j
@ConditionalOnProperty(name = "database.security.enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseConnectionSecurityConfig {

    @Value("${database.security.ssl.enabled:true}")
    private boolean sslEnabled;

    @Value("${database.security.ssl.mode:require}")
    private String sslMode; // require, verify-ca, verify-full

    @Value("${database.security.ssl.ca-cert:}")
    private String caCertPath;

    @Value("${database.security.ssl.client-cert:}")
    private String clientCertPath;

    @Value("${database.security.ssl.client-key:}")
    private String clientKeyPath;

    @Value("${database.security.ssl.keystore.path:}")
    private String keystorePath;

    @Value("${database.security.ssl.keystore.password:}")
    private String keystorePassword;

    @Value("${database.security.ssl.truststore.path:}")
    private String truststorePath;

    @Value("${database.security.ssl.truststore.password:}")
    private String truststorePassword;

    @Value("${database.security.connection.encrypted-password:}")
    private String encryptedDbPassword;

    @Value("${database.security.connection.encryption-key:}")
    private String passwordEncryptionKey;

    @Value("${database.security.pool.max-lifetime:1800000}") // 30 minutes
    private long maxConnectionLifetime;

    @Value("${database.security.pool.idle-timeout:600000}") // 10 minutes
    private long idleTimeout;

    @Value("${database.security.pool.connection-timeout:30000}") // 30 seconds
    private long connectionTimeout;

    @Value("${database.security.pool.max-size:20}")
    private int maxPoolSize;

    @Value("${database.security.pool.min-idle:5}")
    private int minIdle;

    @Value("${database.security.pool.leak-detection-threshold:60000}") // 1 minute
    private long leakDetectionThreshold;

    @Value("${database.security.validation.query:SELECT 1}")
    private String connectionTestQuery;

    @Value("${database.security.validation.timeout:5000}") // 5 seconds
    private int validationTimeout;

    @Value("${database.security.monitoring.enabled:true}")
    private boolean monitoringEnabled;

    @Value("${database.security.monitoring.interval:60}") // seconds
    private int monitoringInterval;

    @Value("${database.security.readonly.user:}")
    private String readOnlyUser;

    @Value("${database.security.readonly.password:}")
    private String readOnlyPassword;

    @Value("${database.security.audit.enabled:true}")
    private boolean auditEnabled;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    private final ScheduledExecutorService monitoringExecutor = Executors.newScheduledThreadPool(1);

    /**
     * Create secure primary DataSource with SSL/TLS and security hardening
     */
    @Bean
    @Primary
    public DataSource secureDataSource(RedisTemplate<String, Object> redisTemplate) {
        log.info("Configuring secure database connection with SSL/TLS and security hardening");

        HikariConfig config = new HikariConfig();

        // Basic connection settings
        config.setJdbcUrl(enhanceUrlWithSecurity(datasourceUrl));
        config.setUsername(datasourceUsername);
        config.setPassword(getDecryptedPassword());

        // SSL/TLS Configuration
        if (sslEnabled) {
            configureSslProperties(config);
        }

        // Connection pool security settings
        configurePoolSecurity(config);

        // Connection validation and testing
        configureConnectionValidation(config);

        // Security hardening
        configureSecurityHardening(config);

        // Create the datasource
        HikariDataSource dataSource = new HikariDataSource(config);

        // Start monitoring if enabled
        if (monitoringEnabled) {
            startConnectionMonitoring(dataSource, redisTemplate);
        }

        // Verify SSL connection
        if (sslEnabled) {
            verifySslConnection(dataSource);
        }

        log.info("Secure database connection configured successfully");
        return dataSource;
    }

    /**
     * Create read-only DataSource for enhanced security
     */
    @Bean
    public DataSource readOnlyDataSource() {
        if (readOnlyUser == null || readOnlyUser.isEmpty()) {
            log.debug("Read-only user not configured, skipping read-only datasource");
            return null;
        }

        log.info("Configuring read-only database connection");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(enhanceUrlWithSecurity(datasourceUrl));
        config.setUsername(readOnlyUser);
        config.setPassword(readOnlyPassword);
        config.setReadOnly(true);

        // Apply same security settings
        if (sslEnabled) {
            configureSslProperties(config);
        }
        configurePoolSecurity(config);
        configureConnectionValidation(config);

        // Smaller pool for read-only connections
        config.setMaximumPoolSize(maxPoolSize / 2);
        config.setMinimumIdle(minIdle / 2);

        return new HikariDataSource(config);
    }

    /**
     * Enhance JDBC URL with security parameters
     */
    private String enhanceUrlWithSecurity(String url) {
        StringBuilder enhancedUrl = new StringBuilder(url);

        // Add security parameters based on database type
        if (url.contains("postgresql")) {
            enhancedUrl.append(url.contains("?") ? "&" : "?");
            
            if (sslEnabled) {
                enhancedUrl.append("ssl=true");
                enhancedUrl.append("&sslmode=").append(sslMode);
                
                if (!caCertPath.isEmpty()) {
                    enhancedUrl.append("&sslrootcert=").append(caCertPath);
                }
                if (!clientCertPath.isEmpty()) {
                    enhancedUrl.append("&sslcert=").append(clientCertPath);
                }
                if (!clientKeyPath.isEmpty()) {
                    enhancedUrl.append("&sslkey=").append(clientKeyPath);
                }
            }
            
            // Additional PostgreSQL security settings
            enhancedUrl.append("&prepareThreshold=0"); // Prevent SQL injection via prepared statements
            enhancedUrl.append("&preparedStatementCacheQueries=0");
            enhancedUrl.append("&preparedStatementCacheSizeMiB=0");
            
        } else if (url.contains("mysql")) {
            enhancedUrl.append(url.contains("?") ? "&" : "?");
            
            if (sslEnabled) {
                enhancedUrl.append("useSSL=true");
                enhancedUrl.append("&requireSSL=true");
                enhancedUrl.append("&verifyServerCertificate=").append("verify-full".equals(sslMode));
                
                if (!truststorePath.isEmpty()) {
                    enhancedUrl.append("&trustCertificateKeyStoreUrl=file:").append(truststorePath);
                    enhancedUrl.append("&trustCertificateKeyStorePassword=").append(truststorePassword);
                }
                if (!keystorePath.isEmpty()) {
                    enhancedUrl.append("&clientCertificateKeyStoreUrl=file:").append(keystorePath);
                    enhancedUrl.append("&clientCertificateKeyStorePassword=").append(keystorePassword);
                }
            }
            
            // Additional MySQL security settings
            enhancedUrl.append("&autoReconnect=false"); // Prevent automatic reconnection
            enhancedUrl.append("&allowMultiQueries=false"); // Prevent multi-statement attacks
            enhancedUrl.append("&useServerPrepStmts=true"); // Use server-side prepared statements
            enhancedUrl.append("&cachePrepStmts=true");
            enhancedUrl.append("&prepStmtCacheSize=250");
            enhancedUrl.append("&prepStmtCacheSqlLimit=2048");
        }

        return enhancedUrl.toString();
    }

    /**
     * Configure SSL/TLS properties for HikariCP
     */
    private void configureSslProperties(HikariConfig config) {
        Properties props = new Properties();

        // PostgreSQL SSL properties
        props.setProperty("ssl", "true");
        props.setProperty("sslmode", sslMode);
        
        if (!caCertPath.isEmpty()) {
            props.setProperty("sslrootcert", caCertPath);
        }
        if (!clientCertPath.isEmpty()) {
            props.setProperty("sslcert", clientCertPath);
        }
        if (!clientKeyPath.isEmpty()) {
            props.setProperty("sslkey", clientKeyPath);
        }

        // MySQL SSL properties
        if (!truststorePath.isEmpty()) {
            System.setProperty("javax.net.ssl.trustStore", truststorePath);
            System.setProperty("javax.net.ssl.trustStorePassword", truststorePassword);
        }
        if (!keystorePath.isEmpty()) {
            System.setProperty("javax.net.ssl.keyStore", keystorePath);
            System.setProperty("javax.net.ssl.keyStorePassword", keystorePassword);
        }

        config.setDataSourceProperties(props);
    }

    /**
     * Configure connection pool security
     */
    private void configurePoolSecurity(HikariConfig config) {
        // Pool sizing
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);

        // Connection lifecycle
        config.setMaxLifetime(maxConnectionLifetime);
        config.setIdleTimeout(idleTimeout);
        config.setConnectionTimeout(connectionTimeout);

        // Leak detection
        config.setLeakDetectionThreshold(leakDetectionThreshold);

        // Pool name for monitoring
        config.setPoolName("SecureHikariPool");

        // Register MBeans for monitoring
        config.setRegisterMbeans(true);

        // Thread factory with security context
        config.setThreadFactory(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName("secure-db-pool-" + thread.getId());
            return thread;
        });
    }

    /**
     * Configure connection validation
     */
    private void configureConnectionValidation(HikariConfig config) {
        config.setConnectionTestQuery(connectionTestQuery);
        config.setValidationTimeout(validationTimeout);
        
        // Test connection on checkout
        config.setConnectionInitSql("SELECT 1");
        
        // Custom connection customizer for additional security
        config.setConnectionInitSql(getConnectionInitSql());
    }

    /**
     * Configure additional security hardening
     */
    private void configureSecurityHardening(HikariConfig config) {
        // Set transaction isolation to prevent dirty reads
        config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");

        // Auto-commit disabled for transaction integrity
        config.setAutoCommit(false);

        // Catalog (database) restrictions
        if (datasourceUrl.contains("postgresql")) {
            config.setCatalog("public");
        }

        // Read-only flag for read replicas (if applicable)
        // config.setReadOnly(false);

        // Additional JDBC properties for security
        Properties props = config.getDataSourceProperties();
        if (props == null) {
            props = new Properties();
        }

        // Statement timeout to prevent long-running queries
        props.setProperty("statement_timeout", "300000"); // 5 minutes
        props.setProperty("lock_timeout", "10000"); // 10 seconds
        props.setProperty("idle_in_transaction_session_timeout", "60000"); // 1 minute

        config.setDataSourceProperties(props);
    }

    /**
     * Get connection initialization SQL based on database type
     */
    private String getConnectionInitSql() {
        if (datasourceUrl.contains("postgresql")) {
            return "SET statement_timeout = 300000; SET lock_timeout = 10000;";
        } else if (datasourceUrl.contains("mysql")) {
            return "SET SESSION sql_mode = 'TRADITIONAL,NO_ENGINE_SUBSTITUTION';";
        }
        return "SELECT 1";
    }

    /**
     * Decrypt database password if encrypted
     */
    private String getDecryptedPassword() {
        if (encryptedDbPassword != null && !encryptedDbPassword.isEmpty()) {
            try {
                return decryptPassword(encryptedDbPassword, passwordEncryptionKey);
            } catch (Exception e) {
                log.error("Failed to decrypt database password", e);
                throw new RuntimeException("Database password decryption failed", e);
            }
        }
        
        // Fall back to environment variable or property
        String password = System.getenv("DB_PASSWORD");
        if (password == null || password.isEmpty()) {
            password = System.getProperty("spring.datasource.password");
        }
        
        if (password == null || password.isEmpty()) {
            throw new IllegalStateException("Database password not configured securely");
        }
        
        return password;
    }

    /**
     * Decrypt password using AES
     */
    private String decryptPassword(String encryptedPassword, String key) throws Exception {
        SecretKey secretKey = new SecretKeySpec(Base64.getDecoder().decode(key), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedPassword));
        return new String(decryptedBytes);
    }

    /**
     * Verify SSL connection is established
     */
    private void verifySslConnection(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            boolean isSslEnabled = false;
            
            if (datasourceUrl.contains("postgresql")) {
                // Check PostgreSQL SSL status
                var stmt = conn.createStatement();
                var rs = stmt.executeQuery("SELECT ssl_is_used()");
                if (rs.next()) {
                    isSslEnabled = rs.getBoolean(1);
                }
                rs.close();
                stmt.close();
                
                if (isSslEnabled) {
                    // Get SSL details
                    stmt = conn.createStatement();
                    rs = stmt.executeQuery("SELECT ssl_version(), ssl_cipher()");
                    if (rs.next()) {
                        log.info("PostgreSQL SSL enabled - Version: {}, Cipher: {}", 
                            rs.getString(1), rs.getString(2));
                    }
                    rs.close();
                    stmt.close();
                }
                
            } else if (datasourceUrl.contains("mysql")) {
                // Check MySQL SSL status
                var stmt = conn.createStatement();
                var rs = stmt.executeQuery("SHOW STATUS LIKE 'Ssl_cipher'");
                if (rs.next()) {
                    String cipher = rs.getString("Value");
                    isSslEnabled = cipher != null && !cipher.isEmpty();
                    if (isSslEnabled) {
                        log.info("MySQL SSL enabled - Cipher: {}", cipher);
                    }
                }
                rs.close();
                stmt.close();
            }
            
            if (!isSslEnabled && sslMode.equals("require")) {
                throw new SQLException("SSL connection required but not established");
            }
            
            log.info("Database SSL verification completed - SSL {}", 
                isSslEnabled ? "ENABLED" : "DISABLED");
                
        } catch (Exception e) {
            log.error("Failed to verify SSL connection", e);
            if (sslMode.equals("require")) {
                throw new RuntimeException("SSL verification failed", e);
            }
        }
    }

    /**
     * Start connection pool monitoring
     */
    private void startConnectionMonitoring(HikariDataSource dataSource, RedisTemplate<String, Object> redisTemplate) {
        monitoringExecutor.scheduleWithFixedDelay(() -> {
            try {
                var poolStats = dataSource.getHikariPoolMXBean();
                
                ConnectionPoolMetrics metrics = ConnectionPoolMetrics.builder()
                    .timestamp(Instant.now())
                    .activeConnections(poolStats.getActiveConnections())
                    .idleConnections(poolStats.getIdleConnections())
                    .totalConnections(poolStats.getTotalConnections())
                    .threadsAwaitingConnection(poolStats.getThreadsAwaitingConnection())
                    .connectionTimeoutRate(calculateTimeoutRate(dataSource))
                    .build();
                
                // Store metrics in Redis
                String metricKey = "db:pool:metrics:" + Instant.now().toEpochMilli();
                redisTemplate.opsForValue().set(metricKey, metrics, Duration.ofHours(24));
                
                // Check for anomalies
                checkPoolAnomalies(metrics);
                
                log.debug("Connection pool metrics - Active: {}, Idle: {}, Total: {}, Waiting: {}",
                    metrics.activeConnections, metrics.idleConnections, 
                    metrics.totalConnections, metrics.threadsAwaitingConnection);
                    
            } catch (Exception e) {
                log.warn("Error monitoring connection pool", e);
            }
        }, monitoringInterval, monitoringInterval, TimeUnit.SECONDS);
    }

    /**
     * Check for connection pool anomalies
     */
    private void checkPoolAnomalies(ConnectionPoolMetrics metrics) {
        // Check for connection leaks
        if (metrics.activeConnections > maxPoolSize * 0.9) {
            log.warn("Connection pool near capacity: {} active connections", metrics.activeConnections);
        }
        
        // Check for waiting threads
        if (metrics.threadsAwaitingConnection > 0) {
            log.warn("Threads waiting for connections: {}", metrics.threadsAwaitingConnection);
        }
        
        // Check timeout rate
        if (metrics.connectionTimeoutRate > 0.1) { // More than 10% timeouts
            log.error("High connection timeout rate: {}", metrics.connectionTimeoutRate);
        }
    }

    /**
     * Calculate connection timeout rate
     */
    private double calculateTimeoutRate(HikariDataSource dataSource) {
        // This would track timeout metrics over time
        // For now, return 0 as placeholder
        return 0.0;
    }

    /**
     * Validate server certificate for SSL connections
     */
    private void validateServerCertificate(String certPath) {
        if (certPath == null || certPath.isEmpty()) {
            return;
        }

        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            try (FileInputStream fis = new FileInputStream(certPath)) {
                Certificate cert = cf.generateCertificate(fis);
                
                if (cert instanceof X509Certificate) {
                    X509Certificate x509Cert = (X509Certificate) cert;
                    
                    // Check certificate validity
                    x509Cert.checkValidity();
                    
                    // Check certificate expiration
                    if (x509Cert.getNotAfter().toInstant().isBefore(Instant.now().plus(Duration.ofDays(30)))) {
                        log.warn("Database server certificate expires soon: {}", x509Cert.getNotAfter());
                    }
                    
                    log.info("Database server certificate validated - Subject: {}, Expires: {}",
                        x509Cert.getSubjectDN(), x509Cert.getNotAfter());
                }
            }
        } catch (Exception e) {
            log.error("Failed to validate server certificate", e);
        }
    }

    /**
     * Clean up resources on shutdown
     */
    @jakarta.annotation.PreDestroy
    public void cleanup() {
        log.info("Shutting down database connection monitoring");
        monitoringExecutor.shutdown();
        try {
            if (!monitoringExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                monitoringExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            monitoringExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Connection pool metrics
     */
    @lombok.Data
    @lombok.Builder
    public static class ConnectionPoolMetrics {
        private Instant timestamp;
        private int activeConnections;
        private int idleConnections;
        private int totalConnections;
        private int threadsAwaitingConnection;
        private double connectionTimeoutRate;
    }
}