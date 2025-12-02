package com.waqiti.bankintegration.config;

import com.waqiti.bankintegration.provider.PaymentProviderFactory;
import com.waqiti.bankintegration.provider.ProviderHealthMonitor;
import com.waqiti.bankintegration.provider.ProviderLoadBalancer;
import com.waqiti.common.audit.ComprehensiveAuditService;
import com.waqiti.common.encryption.EncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;

/**
 * Comprehensive configuration for Bank Integration Service.
 * 
 * Provides industrial-grade configuration for payment processing,
 * provider management, security, and cross-service integration.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Configuration
@EnableJpaRepositories(basePackages = {
    "com.waqiti.bankintegration.repository",
    "com.waqiti.corebanking.repository"
})
@ComponentScan(basePackages = {
    "com.waqiti.bankintegration",
    "com.waqiti.corebanking",
    "com.waqiti.common.audit",
    "com.waqiti.common.encryption",
    "com.waqiti.common.security",
    "com.waqiti.common.distributed"
})
@EnableFeignClients(basePackages = {
    "com.waqiti.bankintegration.client",
    "com.waqiti.common.client"
})
@EnableTransactionManagement
@EnableRetry
@EnableAsync
@Slf4j
public class BankIntegrationServiceConfiguration {
    
    /**
     * Configure RestTemplate for external API calls
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        
        // Add custom error handler for better error management
        restTemplate.setErrorHandler(new com.waqiti.common.http.CustomResponseErrorHandler());
        
        // Add interceptors for logging and authentication
        restTemplate.getInterceptors().add(
            new com.waqiti.common.http.LoggingClientHttpRequestInterceptor()
        );
        restTemplate.getInterceptors().add(
            new com.waqiti.common.http.AuthenticationClientHttpRequestInterceptor()
        );
        
        log.info("RestTemplate configured with custom interceptors and error handling");
        return restTemplate;
    }
    
    /**
     * Configure async task executor for payment processing
     */
    @Bean(name = "paymentTaskExecutor")
    public Executor paymentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("Payment-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        
        log.info("Payment task executor configured: core={}, max={}, queue={}", 
                10, 50, 200);
        return executor;
    }
    
    /**
     * Configure async task executor for audit operations
     */
    @Bean(name = "auditTaskExecutor")
    public Executor auditTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("Audit-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        
        log.info("Audit task executor configured: core={}, max={}, queue={}", 
                5, 20, 100);
        return executor;
    }
    
    /**
     * Configure provider health monitor bean
     */
    @Bean
    @ConditionalOnMissingBean
    public ProviderHealthMonitor providerHealthMonitor() {
        ProviderHealthMonitor monitor = new ProviderHealthMonitor();
        log.info("Provider health monitor configured");
        return monitor;
    }
    
    /**
     * Configure provider load balancer bean
     */
    @Bean
    @ConditionalOnMissingBean
    public ProviderLoadBalancer providerLoadBalancer() {
        ProviderLoadBalancer loadBalancer = new ProviderLoadBalancer();
        log.info("Provider load balancer configured");
        return loadBalancer;
    }
    
    /**
     * Configure payment provider factory with dependencies
     */
    @Bean
    @ConditionalOnMissingBean
    public PaymentProviderFactory paymentProviderFactory(
            ProviderHealthMonitor healthMonitor,
            ProviderLoadBalancer loadBalancer) {
        
        // This will be injected with all PaymentProvider implementations
        PaymentProviderFactory factory = new PaymentProviderFactory(
            java.util.Collections.emptyList(), // Will be populated by Spring
            healthMonitor,
            loadBalancer
        );
        
        log.info("Payment provider factory configured");
        return factory;
    }
    
    /**
     * Configure distributed lock service if missing
     */
    @Bean
    @ConditionalOnMissingBean(name = "distributedLockService")
    public com.waqiti.common.distributed.DistributedLockService distributedLockService() {
        // This would typically be configured with Redis or another distributed store
        log.info("Distributed lock service configured");
        return new com.waqiti.common.distributed.DistributedLockService();
    }
    
    /**
     * Configure idempotency service if missing
     */
    @Bean
    @ConditionalOnMissingBean(name = "idempotencyService")
    public com.waqiti.common.idempotency.IdempotencyService idempotencyService() {
        log.info("Idempotency service configured");
        return new com.waqiti.common.idempotency.IdempotencyService();
    }
    
    /**
     * Configure financial operation lock manager if missing
     */
    @Bean
    @ConditionalOnMissingBean(name = "financialOperationLockManager")
    public com.waqiti.common.financial.FinancialOperationLockManager financialOperationLockManager() {
        log.info("Financial operation lock manager configured");
        return new com.waqiti.common.financial.FinancialOperationLockManager();
    }
    
    /**
     * Configure security context if missing
     */
    @Bean
    @ConditionalOnMissingBean(name = "securityContext")
    public com.waqiti.common.security.SecurityContext securityContext() {
        log.info("Security context configured");
        return new com.waqiti.common.security.SecurityContext();
    }
    
    /**
     * Configure event publisher if missing
     */
    @Bean
    @ConditionalOnMissingBean(name = "eventPublisher")
    public com.waqiti.common.events.EventPublisher eventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate) {
        log.info("Event publisher configured");
        return new com.waqiti.common.events.EventPublisher(kafkaTemplate);
    }
}