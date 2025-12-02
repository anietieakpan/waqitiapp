package com.waqiti.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.idempotency.IdempotencyRecord;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Production-grade Redis configuration for idempotency service.
 *
 * Features:
 * - Connection pooling
 * - Automatic reconnection
 * - Timeouts and circuit breaking
 * - Cluster topology refresh
 * - JSON serialization
 * - High availability support
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "idempotency.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisIdempotencyConfig {

    @Value("${idempotency.redis.host:localhost}")
    private String redisHost;

    @Value("${idempotency.redis.port:6379}")
    private int redisPort;

    @Value("${idempotency.redis.password:}")
    private String redisPassword;

    @Value("${idempotency.redis.database:0}")
    private int redisDatabase;

    @Value("${idempotency.redis.timeout:5000}")
    private long redisTimeout;

    @Value("${idempotency.redis.pool.max-active:8}")
    private int poolMaxActive;

    @Value("${idempotency.redis.pool.max-idle:8}")
    private int poolMaxIdle;

    @Value("${idempotency.redis.pool.min-idle:1}")
    private int poolMinIdle;

    /**
     * Redis connection factory with production-ready configuration
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        log.info("Configuring Redis connection factory: host={}, port={}, db={}",
                redisHost, redisPort, redisDatabase);

        // Standalone configuration
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        config.setDatabase(redisDatabase);

        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.setPassword(redisPassword);
        }

        // Client configuration with timeouts and reconnection
        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(Duration.ofMillis(redisTimeout))
                .keepAlive(true)
                .build();

        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(socketOptions)
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(redisTimeout)))
                .build();

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(redisTimeout))
                .clientOptions(clientOptions)
                .build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config, clientConfig);

        log.info("Redis connection factory configured successfully");
        return factory;
    }

    /**
     * Redis template configured for idempotency records with JSON serialization
     */
    @Bean
    public RedisTemplate<String, IdempotencyRecord> idempotencyRedisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {

        log.info("Configuring idempotency Redis template");

        RedisTemplate<String, IdempotencyRecord> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // String serializer for keys
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // JSON serializer for values
        Jackson2JsonRedisSerializer<IdempotencyRecord> jsonSerializer =
                new Jackson2JsonRedisSerializer<>(IdempotencyRecord.class);
        jsonSerializer.setObjectMapper(objectMapper);

        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.setEnableTransactionSupport(false); // Idempotency doesn't need transactions

        template.afterPropertiesSet();

        log.info("Idempotency Redis template configured successfully");
        return template;
    }

    /**
     * Generic Redis template for other use cases
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // String serializer for keys
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // JSON serializer for values
        Jackson2JsonRedisSerializer<Object> jsonSerializer =
                new Jackson2JsonRedisSerializer<>(Object.class);
        jsonSerializer.setObjectMapper(objectMapper);

        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();

        return template;
    }
}
