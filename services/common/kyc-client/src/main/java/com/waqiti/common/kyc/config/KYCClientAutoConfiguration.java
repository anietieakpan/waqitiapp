package com.waqiti.common.kyc.config;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableFeignClients(basePackages = "com.waqiti.common.kyc.client")
@EnableConfigurationProperties(KYCClientProperties.class)
@ComponentScan(basePackages = "com.waqiti.common.kyc")
@EnableCaching
@ConditionalOnProperty(prefix = "kyc.client", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KYCClientAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "kyc.client.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CacheManager kycCacheManager(KYCClientProperties properties) {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
                "kyc-verifications",
                "kyc-active-verifications", 
                "kyc-user-verifications",
                "kyc-user-status",
                "kyc-user-verified",
                "kyc-user-actions"
        );
        
        // Configure cache settings if needed
        return cacheManager;
    }
}

@Data
@ConfigurationProperties(prefix = "kyc.client")
class KYCClientProperties {
    
    private boolean enabled = true;
    private Cache cache = new Cache();
    private CircuitBreaker circuitBreaker = new CircuitBreaker();
    private Retry retry = new Retry();
    
    @Data
    public static class Cache {
        private boolean enabled = true;
        private Duration ttl = Duration.ofMinutes(5);
        private int maxSize = 1000;
    }
    
    @Data
    public static class CircuitBreaker {
        private int failureRateThreshold = 50;
        private Duration waitDurationInOpenState = Duration.ofSeconds(30);
        private int slidingWindowSize = 10;
        private int minimumNumberOfCalls = 5;
    }
    
    @Data
    public static class Retry {
        private int maxAttempts = 3;
        private Duration waitDuration = Duration.ofSeconds(1);
    }
}