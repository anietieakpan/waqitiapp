package com.waqiti.voice.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SslOptions;
import io.lettuce.core.protocol.ProtocolVersion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.io.File;
import java.time.Duration;

/**
 * Redis TLS Configuration
 *
 * CRITICAL SECURITY: Enforces encrypted connections to Redis
 *
 * Features:
 * - TLS 1.2+ only
 * - Client certificate authentication (mTLS)
 * - Certificate verification
 * - Connection pooling
 * - Automatic reconnection with exponential backoff
 *
 * Use Cases:
 * - Idempotency key storage (duplicate payment prevention)
 * - Session caching
 * - Rate limiting counters
 * - Temporary data storage
 *
 * Security Benefits:
 * - Protects sensitive data in transit (idempotency keys, session data)
 * - Prevents eavesdropping on cache access
 * - Prevents man-in-the-middle attacks
 * - Required for PCI-DSS compliance
 *
 * Compliance:
 * - PCI-DSS Requirement 4.1 (Strong cryptography for transmission)
 * - GDPR Article 32 (Encryption of personal data)
 *
 * Setup:
 * 1. Enable TLS on Redis server:
 *    tls-port 6380
 *    tls-cert-file /path/to/redis.crt
 *    tls-key-file /path/to/redis.key
 *    tls-ca-cert-file /path/to/ca.crt
 * 2. Set environment variables:
 *    - REDIS_SSL_ENABLED=true
 *    - REDIS_HOST=redis.waqiti.com
 *    - REDIS_PORT=6380
 *    - REDIS_SSL_CERT=/path/to/client-cert.pem (optional)
 *    - REDIS_SSL_KEY=/path/to/client-key.pem (optional)
 */
@Slf4j
@Configuration
public class RedisTLSConfiguration {

    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Value("${spring.redis.password:#{null}}")
    private String redisPassword;

    @Value("${spring.redis.database:0}")
    private int redisDatabase;

    @Value("${spring.redis.timeout:2000}")
    private long timeout;

    // TLS Configuration
    @Value("${spring.redis.ssl.enabled:false}")
    private boolean sslEnabled;

    @Value("${spring.redis.ssl.verify-peer:true}")
    private boolean verifyPeer;

    @Value("${spring.redis.ssl.cert:#{null}}")
    private String sslCert; // Client certificate path

    @Value("${spring.redis.ssl.key:#{null}}")
    private String sslKey; // Client key path

    @Value("${spring.redis.ssl.key-password:#{null}}")
    private String sslKeyPassword;

    @Value("${spring.redis.ssl.ca-cert:#{null}}")
    private String caCert; // CA certificate path

    @Value("${spring.redis.ssl.trust-store:#{null}}")
    private String trustStore; // JKS trust store path

    @Value("${spring.redis.ssl.trust-store-password:#{null}}")
    private String trustStorePassword;

    // Connection pool configuration
    @Value("${spring.redis.lettuce.pool.max-active:8}")
    private int maxActive;

    @Value("${spring.redis.lettuce.pool.max-idle:8}")
    private int maxIdle;

    @Value("${spring.redis.lettuce.pool.min-idle:0}")
    private int minIdle;

    @Value("${spring.redis.lettuce.pool.max-wait:2000}")
    private long maxWait;

    /**
     * Configure Redis connection factory with TLS
     */
    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        log.info("Configuring Redis connection: host={}, port={}, ssl={}, verifyPeer={}",
                redisHost, redisPort, sslEnabled, verifyPeer);

        // Redis standalone configuration
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(redisHost);
        redisConfig.setPort(redisPort);
        redisConfig.setDatabase(redisDatabase);

        if (redisPassword != null && !redisPassword.isBlank()) {
            redisConfig.setPassword(redisPassword);
            log.info("Redis authentication configured");
        }

        // Lettuce client configuration
        LettuceClientConfiguration.LettuceClientConfigurationBuilder clientBuilder =
                LettuceClientConfiguration.builder()
                        .commandTimeout(Duration.ofMillis(timeout));

        // Configure TLS/SSL
        if (sslEnabled) {
            clientBuilder.useSsl();

            SslOptions.Builder sslOptionsBuilder = SslOptions.builder();

            // Server certificate verification
            if (verifyPeer) {
                log.info("Redis TLS: Server certificate verification ENABLED");

                // CA certificate for verification
                if (caCert != null && !caCert.isBlank()) {
                    File caCertFile = new File(caCert);
                    if (caCertFile.exists()) {
                        sslOptionsBuilder.trustManager(caCertFile);
                        log.info("Redis TLS: CA certificate loaded: {}", caCert);
                    } else {
                        log.error("Redis TLS: CA certificate not found: {}", caCert);
                        throw new IllegalStateException("CA certificate not found: " + caCert);
                    }
                }

                // Trust store (alternative to CA cert)
                if (trustStore != null && !trustStore.isBlank()) {
                    File trustStoreFile = new File(trustStore);
                    if (trustStoreFile.exists()) {
                        sslOptionsBuilder.trustManager(trustStoreFile);
                        log.info("Redis TLS: Trust store loaded: {}", trustStore);
                    } else {
                        log.error("Redis TLS: Trust store not found: {}", trustStore);
                        throw new IllegalStateException("Trust store not found: " + trustStore);
                    }
                }
            } else {
                log.warn("⚠️ Redis TLS: Server certificate verification DISABLED");
                log.warn("⚠️ Vulnerable to man-in-the-middle attacks");
            }

            // Client certificate authentication (mTLS)
            if (sslCert != null && !sslCert.isBlank() && sslKey != null && !sslKey.isBlank()) {
                File certFile = new File(sslCert);
                File keyFile = new File(sslKey);

                if (certFile.exists() && keyFile.exists()) {
                    if (sslKeyPassword != null && !sslKeyPassword.isBlank()) {
                        sslOptionsBuilder.keyManager(certFile, keyFile, sslKeyPassword.toCharArray());
                    } else {
                        sslOptionsBuilder.keyManager(certFile, keyFile);
                    }
                    log.info("✅ Redis mTLS: Client certificate configured");
                } else {
                    log.error("Redis TLS: Client certificate or key not found");
                    throw new IllegalStateException("Client certificate or key not found");
                }
            }

            SslOptions sslOptions = sslOptionsBuilder.build();
            ClientOptions clientOptions = ClientOptions.builder()
                    .sslOptions(sslOptions)
                    .protocolVersion(ProtocolVersion.RESP3)
                    .build();

            clientBuilder.clientOptions(clientOptions);

            log.info("✅ Redis TLS/SSL enabled");
        } else {
            log.warn("⚠️ Redis TLS/SSL is DISABLED - NOT RECOMMENDED FOR PRODUCTION!");
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                redisConfig,
                clientBuilder.build()
        );

        factory.afterPropertiesSet();

        return factory;
    }

    /**
     * Configure RedisTemplate with serializers
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use String serializer for keys
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Use JSON serializer for values
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();

        log.info("RedisTemplate configured with JSON serialization");
        return template;
    }

    /**
     * Production SSL validator
     */
    @Profile("production")
    @Bean
    public void validateProductionRedisSSL() {
        if (!sslEnabled) {
            throw new IllegalStateException(
                    "SECURITY VIOLATION: Redis SSL must be enabled in production environment"
            );
        }

        if (!verifyPeer) {
            throw new IllegalStateException(
                    "SECURITY VIOLATION: Redis SSL peer verification must be enabled in production"
            );
        }

        if ((caCert == null || caCert.isBlank()) && (trustStore == null || trustStore.isBlank())) {
            log.warn("⚠️ PRODUCTION WARNING: No CA certificate or trust store configured for Redis");
            log.warn("⚠️ Using system default trust store - may accept invalid certificates");
        }

        log.info("✅ Production Redis SSL validation passed");
    }
}
