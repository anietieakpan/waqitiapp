package com.waqiti.security.rasp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.rasp.detector.AttackDetector;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.Comparator;
import java.util.List;

/**
 * RASP (Runtime Application Self-Protection) Configuration
 */
@Configuration
@EnableConfigurationProperties(RaspProperties.class)
@RequiredArgsConstructor
public class RaspConfiguration {

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
        
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public ObjectMapper raspObjectMapper() {
        return new ObjectMapper();
    }

    /**
     * Configure ordered list of attack detectors
     * Higher priority detectors run first
     */
    @Bean
    public List<AttackDetector> orderedAttackDetectors(List<AttackDetector> detectors) {
        return detectors.stream()
                .filter(AttackDetector::isEnabled)
                .sorted(Comparator.comparing(AttackDetector::getPriority).reversed())
                .toList();
    }
}