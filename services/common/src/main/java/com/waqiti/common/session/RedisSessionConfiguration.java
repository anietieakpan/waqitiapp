package com.waqiti.common.session;

import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.HeaderHttpSessionIdResolver;
import org.springframework.session.web.http.HttpSessionIdResolver;

import java.time.Duration;
import java.util.List;

/**
 * Redis cluster configuration for distributed session management
 */
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800) // 30 minutes
@Slf4j
public class RedisSessionConfiguration {
    
    @Value("${redis.cluster.nodes}")
    private List<String> clusterNodes;
    
    @Value("${redis.cluster.password:}")
    private String clusterPassword;
    
    @Value("${redis.cluster.max-redirects:3}")
    private int maxRedirects;
    
    @Value("${redis.cluster.timeout:60000}")
    private long timeoutMs;
    
    @Value("${redis.cluster.pool.max-active:20}")
    private int maxActive;
    
    @Value("${redis.cluster.pool.max-idle:10}")
    private int maxIdle;
    
    @Value("${redis.cluster.pool.min-idle:5}")
    private int minIdle;
    
    /**
     * Redis cluster configuration
     */
    @Bean
    public RedisClusterConfiguration redisClusterConfiguration() {
        RedisClusterConfiguration config = new RedisClusterConfiguration(clusterNodes);
        config.setMaxRedirects(maxRedirects);
        
        if (clusterPassword != null && !clusterPassword.isEmpty()) {
            config.setPassword(clusterPassword);
        }
        
        return config;
    }
    
    /**
     * Lettuce client configuration with optimizations
     */
    @Bean
    public LettuceClientConfiguration lettuceClientConfiguration() {
        ClusterTopologyRefreshOptions topologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
            .enablePeriodicRefresh(Duration.ofMinutes(1))
            .enableAllAdaptiveRefreshTriggers()
            .adaptiveRefreshTriggersTimeout(Duration.ofSeconds(30))
            .build();
        
        ClusterClientOptions clientOptions = ClusterClientOptions.builder()
            .topologyRefreshOptions(topologyRefreshOptions)
            .validateClusterNodeMembership(true)
            .maxRedirects(maxRedirects)
            .build();
        
        return LettuceClientConfiguration.builder()
            .clientOptions(clientOptions)
            .commandTimeout(Duration.ofMillis(timeoutMs))
            .shutdownTimeout(Duration.ofSeconds(10))
            .build();
    }
    
    /**
     * Redis connection factory with cluster support
     */
    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
            redisClusterConfiguration(), lettuceClientConfiguration());
        
        factory.setValidateConnection(true);
        factory.setShareNativeConnection(true);
        factory.afterPropertiesSet();
        
        return factory;
    }
    
    /**
     * Redis template with optimized serialization
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory());
        
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
     * Distributed session manager
     */
    @Bean
    public DistributedSessionManager distributedSessionManager() {
        return new DistributedSessionManager(redisTemplate());
    }
    
    /**
     * Session ID resolver using headers (for API-based sessions)
     */
    @Bean
    public HttpSessionIdResolver httpSessionIdResolver() {
        return HeaderHttpSessionIdResolver.xAuthToken();
    }
    
    /**
     * Session event listener for monitoring
     */
    @Bean
    public SessionEventListener sessionEventListener() {
        return new SessionEventListener();
    }
}