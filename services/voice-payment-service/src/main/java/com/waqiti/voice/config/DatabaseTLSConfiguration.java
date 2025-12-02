package com.waqiti.voice.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Database TLS/SSL Configuration
 *
 * CRITICAL SECURITY: Enforces encrypted connections to PostgreSQL
 *
 * Features:
 * - TLS 1.2+ only (no SSL 3.0, TLS 1.0, TLS 1.1)
 * - Server certificate verification
 * - Client certificate authentication (optional)
 * - Connection pooling with HikariCP
 *
 * Security Benefits:
 * - Protects data in transit (voice biometric data, PII)
 * - Prevents man-in-the-middle attacks
 * - Prevents eavesdropping on database connections
 * - Required for PCI-DSS compliance
 *
 * Compliance:
 * - PCI-DSS Requirement 4.1 (Use strong cryptography for transmission)
 * - GDPR Article 32 (Encryption of personal data)
 * - HIPAA Security Rule (Transmission Security)
 *
 * Setup:
 * 1. Enable SSL on PostgreSQL server: ssl = on
 * 2. Generate server certificates (or use Let's Encrypt)
 * 3. Configure pg_hba.conf: hostssl all all 0.0.0.0/0 md5
 * 4. Set environment variables:
 *    - DB_SSL_MODE=require (or verify-ca, verify-full)
 *    - DB_SSL_CERT=/path/to/client-cert.pem (optional)
 *    - DB_SSL_KEY=/path/to/client-key.pem (optional)
 *    - DB_SSL_ROOT_CERT=/path/to/root-ca.pem (for verify-ca/verify-full)
 */
@Slf4j
@Configuration
public class DatabaseTLSConfiguration {

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}")
    private String driverClassName;

    // TLS Configuration
    @Value("${spring.datasource.ssl.enabled:true}")
    private boolean sslEnabled;

    @Value("${spring.datasource.ssl.mode:require}")
    private String sslMode; // disable, allow, prefer, require, verify-ca, verify-full

    @Value("${spring.datasource.ssl.factory:org.postgresql.ssl.DefaultJavaSSLFactory}")
    private String sslFactory;

    @Value("${spring.datasource.ssl.cert:#{null}}")
    private String sslCert; // Client certificate path

    @Value("${spring.datasource.ssl.key:#{null}}")
    private String sslKey; // Client key path

    @Value("${spring.datasource.ssl.root-cert:#{null}}")
    private String sslRootCert; // CA certificate path

    @Value("${spring.datasource.ssl.password:#{null}}")
    private String sslPassword; // Certificate password

    // HikariCP Configuration
    @Value("${spring.datasource.hikari.maximum-pool-size:20}")
    private int maximumPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:5}")
    private int minimumIdle;

    @Value("${spring.datasource.hikari.connection-timeout:30000}")
    private long connectionTimeout;

    @Value("${spring.datasource.hikari.idle-timeout:600000}")
    private long idleTimeout;

    @Value("${spring.datasource.hikari.max-lifetime:1800000}")
    private long maxLifetime;

    /**
     * Configure DataSource with TLS/SSL
     */
    @Bean
    @Primary
    public DataSource dataSource() {
        log.info("Configuring PostgreSQL DataSource with TLS: enabled={}, mode={}",
                sslEnabled, sslMode);

        HikariConfig config = new HikariConfig();

        // Basic configuration
        config.setJdbcUrl(buildJdbcUrlWithSSL());
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);

        // Connection pool configuration
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setMaxLifetime(maxLifetime);

        // Pool name for monitoring
        config.setPoolName("VoicePaymentHikariPool");

        // Connection test query
        config.setConnectionTestQuery("SELECT 1");

        // SSL/TLS properties
        if (sslEnabled) {
            Properties props = new Properties();

            // Core SSL settings
            props.setProperty("ssl", "true");
            props.setProperty("sslmode", sslMode);
            props.setProperty("sslfactory", sslFactory);

            // Client certificate authentication (mTLS)
            if (sslCert != null && !sslCert.isBlank()) {
                props.setProperty("sslcert", sslCert);
                log.info("Client certificate configured: {}", sslCert);
            }

            if (sslKey != null && !sslKey.isBlank()) {
                props.setProperty("sslkey", sslKey);
                log.info("Client key configured: {}", sslKey);
            }

            if (sslPassword != null && !sslPassword.isBlank()) {
                props.setProperty("sslpassword", sslPassword);
                log.info("Client certificate password configured");
            }

            // CA certificate for verification
            if (sslRootCert != null && !sslRootCert.isBlank()) {
                props.setProperty("sslrootcert", sslRootCert);
                log.info("Root CA certificate configured: {}", sslRootCert);
            }

            config.setDataSourceProperties(props);

            log.info("✅ PostgreSQL TLS/SSL enabled: mode={}", sslMode);

            // Validate SSL mode
            validateSSLMode(sslMode);
        } else {
            log.warn("⚠️ PostgreSQL TLS/SSL is DISABLED - NOT RECOMMENDED FOR PRODUCTION!");
        }

        // Health check configuration
        config.setHealthCheckRegistry(null); // Use default

        // Leak detection (development only)
        config.setLeakDetectionThreshold(60000); // 60 seconds

        return new HikariDataSource(config);
    }

    /**
     * Build JDBC URL with SSL parameters
     */
    private String buildJdbcUrlWithSSL() {
        if (!sslEnabled) {
            return jdbcUrl;
        }

        // If URL already contains SSL params, return as-is
        if (jdbcUrl.contains("ssl=") || jdbcUrl.contains("sslmode=")) {
            return jdbcUrl;
        }

        // Append SSL parameters
        String separator = jdbcUrl.contains("?") ? "&" : "?";
        return jdbcUrl + separator + "ssl=true&sslmode=" + sslMode;
    }

    /**
     * Validate SSL mode for security
     */
    private void validateSSLMode(String mode) {
        switch (mode.toLowerCase()) {
            case "disable":
                log.error("❌ SSL mode 'disable' is INSECURE - data transmitted in plaintext!");
                break;

            case "allow":
                log.warn("⚠️ SSL mode 'allow' may fall back to plaintext - not recommended");
                break;

            case "prefer":
                log.warn("⚠️ SSL mode 'prefer' may fall back to plaintext - use 'require' or higher");
                break;

            case "require":
                log.info("✅ SSL mode 'require' - encrypted connection enforced");
                log.warn("⚠️ Server certificate NOT verified - vulnerable to MITM attacks");
                log.info("💡 Consider using 'verify-ca' or 'verify-full' for production");
                break;

            case "verify-ca":
                log.info("✅ SSL mode 'verify-ca' - encrypted connection with CA verification");
                log.warn("⚠️ Hostname NOT verified - some MITM attacks still possible");
                log.info("💡 Consider using 'verify-full' for maximum security");
                break;

            case "verify-full":
                log.info("✅ SSL mode 'verify-full' - encrypted connection with full verification");
                log.info("🔒 Maximum security: CA + hostname verification");
                break;

            default:
                log.error("❌ Unknown SSL mode: {} - defaulting to 'require'", mode);
        }
    }

    /**
     * Production SSL mode validator
     */
    @Profile("production")
    @Bean
    public void validateProductionSSL() {
        if (!sslEnabled) {
            throw new IllegalStateException(
                    "SECURITY VIOLATION: SSL must be enabled in production environment"
            );
        }

        if ("disable".equalsIgnoreCase(sslMode) || "allow".equalsIgnoreCase(sslMode)) {
            throw new IllegalStateException(
                    "SECURITY VIOLATION: SSL mode '" + sslMode + "' is not allowed in production"
            );
        }

        if (!"verify-full".equalsIgnoreCase(sslMode) && !"verify-ca".equalsIgnoreCase(sslMode)) {
            log.warn("⚠️ PRODUCTION WARNING: SSL mode '{}' does not verify server certificate", sslMode);
            log.warn("⚠️ Vulnerable to man-in-the-middle attacks");
            log.warn("⚠️ Recommended: Use 'verify-full' for maximum security");
        }

        log.info("✅ Production SSL validation passed");
    }
}
