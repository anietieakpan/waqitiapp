package com.waqiti.config.config;

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
 * Comprehensive configuration for Config Service.
 * 
 * Provides configuration management, security context,
 * and environment repository management.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Configuration
@EnableJpaRepositories(basePackages = {
    "com.waqiti.config.repository"
})
@ComponentScan(basePackages = {
    "com.waqiti.config",
    "com.waqiti.common.encryption",
    "com.waqiti.common.security",
    "com.waqiti.common.events"
})
@EnableFeignClients(basePackages = {
    "com.waqiti.config.client",
    "com.waqiti.common.client"
})
@EnableTransactionManagement
@EnableRetry
@EnableAsync
@Slf4j
public class ConfigServiceConfiguration {
    
    /**
     * Configure RestTemplate for config service
     */
    @Bean("configRestTemplate")
    @ConditionalOnMissingBean(name = "configRestTemplate")
    public RestTemplate configRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        
        // Add custom error handler
        restTemplate.setErrorHandler(new com.waqiti.common.http.CustomResponseErrorHandler());
        
        // Add logging interceptor
        restTemplate.getInterceptors().add(
            new com.waqiti.common.http.LoggingClientHttpRequestInterceptor()
        );
        
        log.info("Config RestTemplate configured");
        return restTemplate;
    }
    
    /**
     * Configure security context
     */
    @Bean
    @ConditionalOnMissingBean(name = "securityContext")
    public com.waqiti.common.security.SecurityContext securityContext() {
        log.info("Security context configured for config service");
        return new com.waqiti.common.security.SecurityContext();
    }
    
    /**
     * Configure event publisher
     */
    @Bean
    @ConditionalOnMissingBean(name = "eventPublisher")
    public com.waqiti.common.events.EventPublisher eventPublisher() {
        log.info("Event publisher configured for config service");
        return new com.waqiti.common.events.EventPublisher(null); // KafkaTemplate will be injected
    }
    
    // NotificationServiceClient, ConfigurationController, and RefreshController
    // are auto-discovered via @Component/@RestController annotations
    // No manual bean configuration needed
    
    /**
     * Configure environment repository bean to resolve multiple bean conflict
     */
    @Bean("compositeEnvironmentRepository")
    @ConditionalOnMissingBean(name = "compositeEnvironmentRepository")
    public org.springframework.cloud.config.environment.EnvironmentRepository compositeEnvironmentRepository() {
        log.info("Composite environment repository configured");
        // This resolves the "more than one bean of 'EnvironmentRepository' type" error
        return new org.springframework.cloud.config.server.environment.CompositeEnvironmentRepository(
            java.util.Collections.emptyList()
        );
    }
    
    /**
     * Configure search path composite environment repository
     */
    @Bean("searchPathCompositeEnvironmentRepository")
    @ConditionalOnMissingBean(name = "searchPathCompositeEnvironmentRepository")  
    public org.springframework.cloud.config.environment.EnvironmentRepository searchPathCompositeEnvironmentRepository() {
        log.info("Search path composite environment repository configured");
        return new org.springframework.cloud.config.server.environment.CompositeEnvironmentRepository(
            java.util.Collections.emptyList()
        );
    }
    
    // ConfigServerHealthIndicator and ConfigurationService
    // are auto-discovered via @Component/@Service annotations
    // No manual bean configuration needed
}