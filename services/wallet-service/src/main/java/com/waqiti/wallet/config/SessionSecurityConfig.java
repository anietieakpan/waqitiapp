/**
 * SECURITY ENHANCEMENT: Session Security Configuration
 * Enables session security validation and aspect-oriented programming
 */
package com.waqiti.wallet.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration for session security features including Redis and AOP
 */
@Configuration
@EnableAspectJAutoProxy
@Slf4j
public class SessionSecurityConfig {
    
    @Value("${wallet.security.session.enabled:true}")
    private boolean sessionSecurityEnabled;
    
    @Value("${wallet.security.session.redis-key-prefix:wallet:session:}")
    private String redisKeyPrefix;
    
    /**
     * Redis template for session security data
     */
    @Bean(name = "sessionSecurityRedisTemplate")
    public RedisTemplate<String, Object> sessionSecurityRedisTemplate(RedisConnectionFactory connectionFactory) {
        log.info("SECURITY: Configuring Redis template for session security with key prefix: {}", redisKeyPrefix);
        
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Configure serializers
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        
        // Enable transaction support
        template.setEnableTransactionSupport(true);
        
        template.afterPropertiesSet();
        
        log.info("SECURITY: Session security Redis template configured successfully");
        
        return template;
    }
    
    /**
     * Get session security configuration status
     */
    public boolean isSessionSecurityEnabled() {
        return sessionSecurityEnabled;
    }
    
    /**
     * Get Redis key prefix for session data
     */
    public String getRedisKeyPrefix() {
        return redisKeyPrefix;
    }
}