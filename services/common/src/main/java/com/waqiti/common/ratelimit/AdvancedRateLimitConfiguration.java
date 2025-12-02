package com.waqiti.common.ratelimit;

import com.waqiti.common.ratelimit.properties.AdvancedRateLimitProperties;
import com.waqiti.common.ratelimit.properties.AdvancedRateLimitProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.ConnectionPoolConfig;

import java.time.Duration;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

/**
 * Advanced configuration for the distributed rate limiting system with Redis.
 * Supports standalone Redis, Redis Sentinel for HA, and Redis Cluster for scalability.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Configuration
@EnableConfigurationProperties(AdvancedRateLimitProperties.class)
public class AdvancedRateLimitConfiguration {
    
    /**
     * Creates optimized Jedis pool configuration for production use.
     */
    @Bean
    public JedisPoolConfig jedisPoolConfig(AdvancedRateLimitProperties properties) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        
        // Connection pool settings
        poolConfig.setMaxTotal(properties.getRedis().getMaxConnections());
        poolConfig.setMaxIdle(properties.getRedis().getMaxIdle());
        poolConfig.setMinIdle(properties.getRedis().getMinIdle());
        
        // Connection validation
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        
        // Eviction settings for idle connections
        poolConfig.setTimeBetweenEvictionRunsMillis(30000);
        poolConfig.setMinEvictableIdleTimeMillis(60000);
        poolConfig.setSoftMinEvictableIdleTimeMillis(30000);
        poolConfig.setNumTestsPerEvictionRun(3);
        
        // Connection timeouts
        poolConfig.setMaxWaitMillis(Duration.ofSeconds(5).toMillis());
        poolConfig.setBlockWhenExhausted(true);
        
        // Performance optimizations
        poolConfig.setLifo(true); // LIFO ordering for better connection locality
        poolConfig.setFairness(false); // Better performance under load
        
        return poolConfig;
    }
    
    /**
     * Creates primary Jedis pool for distributed rate limiting with Redis.
     * Supports standalone, sentinel, and cluster configurations.
     */
    @Bean
    @Primary
    public Object jedisPool(AdvancedRateLimitProperties properties, JedisPoolConfig poolConfig) {
        AdvancedRateLimitProperties.Redis redisConfig = properties.getRedis();
        
        if (redisConfig.getSentinels() != null && !redisConfig.getSentinels().isEmpty()) {
            // Redis Sentinel configuration for HA - returns JedisSentinelPool
            return createSentinelPool(redisConfig, poolConfig);
        } else {
            // Standalone Redis configuration - returns JedisPool
            return createStandalonePool(redisConfig, poolConfig);
        }
    }
    
    @Bean
    @ConditionalOnProperty(name = "rate-limit.redis.sentinels", matchIfMissing = false)
    public JedisSentinelPool jedisSentinelPool(AdvancedRateLimitProperties properties, JedisPoolConfig poolConfig) {
        return createSentinelPool(properties.getRedis(), poolConfig);
    }
    
    @Bean
    @ConditionalOnMissingBean(JedisSentinelPool.class)
    public JedisPool standardJedisPool(AdvancedRateLimitProperties properties, JedisPoolConfig poolConfig) {
        return createStandalonePool(properties.getRedis(), poolConfig);
    }
    
    /**
     * Creates Jedis cluster for Redis Cluster support.
     */
    @Bean
    public JedisCluster jedisCluster(AdvancedRateLimitProperties properties) {
        AdvancedRateLimitProperties.Redis redisConfig = properties.getRedis();
        
        if (redisConfig.getClusterNodes() != null && !redisConfig.getClusterNodes().isEmpty()) {
            Set<HostAndPort> clusterNodes = new HashSet<>();
            
            for (String node : redisConfig.getClusterNodes()) {
                String[] parts = node.split(":");
                clusterNodes.add(new HostAndPort(parts[0], Integer.parseInt(parts[1])));
            }
            
            ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
            poolConfig.setMaxTotal(redisConfig.getMaxConnections());
            poolConfig.setMaxIdle(redisConfig.getMaxIdle());
            poolConfig.setMinIdle(redisConfig.getMinIdle());
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestOnReturn(true);
            
            return new JedisCluster(
                clusterNodes,
                redisConfig.getTimeoutMs(),
                redisConfig.getTimeoutMs(),
                redisConfig.getMaxRedirections(),
                redisConfig.getPassword(),
                poolConfig
            );
        }
        
        return null; // Cluster not configured
    }
    
    /**
     * Creates Redis connection factory for Spring Data Redis integration.
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory(AdvancedRateLimitProperties properties) {
        AdvancedRateLimitProperties.Redis redisConfig = properties.getRedis();
        
        JedisConnectionFactory factory = new JedisConnectionFactory();
        factory.setHostName(redisConfig.getHost());
        factory.setPort(redisConfig.getPort());
        factory.setDatabase(redisConfig.getDatabase());
        factory.setTimeout(redisConfig.getTimeoutMs());
        
        if (redisConfig.getPassword() != null && !redisConfig.getPassword().isEmpty()) {
            factory.setPassword(redisConfig.getPassword());
        }
        
        // Pool configuration
        factory.setPoolConfig(jedisPoolConfig(properties));
        
        // SSL configuration
        factory.setUseSsl(redisConfig.isSsl());
        
        factory.afterPropertiesSet();
        return factory;
    }
    
    /**
     * Creates Redis template with proper serialization for rate limiting.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JSON serializer for values
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        
        template.setDefaultSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        
        return template;
    }
    
    /**
     * Creates standalone Jedis pool.
     */
    private JedisPool createStandalonePool(AdvancedRateLimitProperties.Redis redisConfig, 
                                         JedisPoolConfig poolConfig) {
        return new JedisPool(
            poolConfig,
            redisConfig.getHost(),
            redisConfig.getPort(),
            (int) redisConfig.getConnectionTimeoutMs(),
            redisConfig.getPassword(),
            redisConfig.getDatabase(),
            redisConfig.isSsl()
        );
    }
    
    /**
     * Creates Jedis sentinel pool for HA configuration.
     */
    private JedisSentinelPool createSentinelPool(AdvancedRateLimitProperties.Redis redisConfig, 
                                               JedisPoolConfig poolConfig) {
        Set<String> sentinels = new HashSet<>(redisConfig.getSentinels());
        
        return new JedisSentinelPool(
            redisConfig.getMasterName(),
            sentinels,
            poolConfig,
            (int) redisConfig.getConnectionTimeoutMs(),
            redisConfig.getPassword(),
            redisConfig.getDatabase()
        );
    }
    
}