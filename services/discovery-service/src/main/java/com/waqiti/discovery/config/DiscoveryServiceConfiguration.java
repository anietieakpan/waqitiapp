package com.waqiti.discovery.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.client.RestTemplate;

/**
 * Comprehensive configuration for Discovery Service.
 * 
 * Provides service registry management, health monitoring,
 * and distributed service coordination.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Configuration
@EnableJpaRepositories(basePackages = {
    "com.waqiti.discovery.repository"
})
@ComponentScan(basePackages = {
    "com.waqiti.discovery",
    "com.waqiti.common.security",
    "com.waqiti.common.events",
    "com.waqiti.common.audit"
})
@EnableFeignClients(basePackages = {
    "com.waqiti.discovery.client",
    "com.waqiti.common.client"
})
@EnableTransactionManagement
@EnableRetry
@EnableAsync
@Slf4j
public class DiscoveryServiceConfiguration {
    
    /**
     * Configure RestTemplate for discovery service
     */
    @Bean("discoveryRestTemplate")
    @ConditionalOnMissingBean(name = "discoveryRestTemplate")
    public RestTemplate discoveryRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        log.info("Discovery RestTemplate configured");
        return restTemplate;
    }

    // Repositories are auto-configured by @EnableJpaRepositories
    // Services are auto-configured by @ComponentScan
    // All beans are created via Spring's component scanning
}