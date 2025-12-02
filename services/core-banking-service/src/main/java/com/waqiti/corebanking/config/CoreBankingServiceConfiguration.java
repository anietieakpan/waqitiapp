package com.waqiti.corebanking.config;

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
 * Comprehensive configuration for Core Banking Service.
 * 
 * Provides configuration for account management, transaction processing,
 * and distributed services integration.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Configuration
@EnableJpaRepositories(basePackages = {
    "com.waqiti.corebanking.repository"
})
@ComponentScan(basePackages = {
    "com.waqiti.corebanking",
    "com.waqiti.common.client",
    "com.waqiti.common.distributed",
    "com.waqiti.common.encryption",
    "com.waqiti.common.security",
    "com.waqiti.common.events",
    "com.waqiti.common.locking"
})
@EnableFeignClients(basePackages = {
    "com.waqiti.corebanking.client",
    "com.waqiti.common.client"
})
@EnableTransactionManagement
@EnableRetry
@EnableAsync
@Slf4j
public class CoreBankingServiceConfiguration {
    
    /**
     * Configure RestTemplate for core banking
     */
    @Bean("coreBankingRestTemplate")
    @ConditionalOnMissingBean(name = "coreBankingRestTemplate")
    public RestTemplate coreBankingRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        
        // Add custom error handler
        restTemplate.setErrorHandler(new com.waqiti.common.http.CustomResponseErrorHandler());
        
        // Add logging interceptor
        restTemplate.getInterceptors().add(
            new com.waqiti.common.http.LoggingClientHttpRequestInterceptor()
        );
        
        log.info("Core Banking RestTemplate configured");
        return restTemplate;
    }
    
    /**
     * Configure distributed lock service
     */
    @Bean
    @ConditionalOnMissingBean(name = "distributedLockService")
    public com.waqiti.common.distributed.DistributedLockService distributedLockService() {
        log.info("Distributed lock service configured for core banking");
        return new com.waqiti.common.distributed.DistributedLockService();
    }
    
    /**
     * Configure idempotency service
     */
    @Bean
    @ConditionalOnMissingBean(name = "idempotencyService")
    public com.waqiti.common.idempotency.IdempotencyService idempotencyService() {
        log.info("Idempotency service configured for core banking");
        return new com.waqiti.common.idempotency.IdempotencyService();
    }
    
    
    /**
     * Configure ledger service client
     */
    @Bean
    @ConditionalOnMissingBean(name = "ledgerServiceClient")
    public com.waqiti.corebanking.client.LedgerServiceClient ledgerServiceClient() {
        log.info("Ledger service client configured");
        return new com.waqiti.corebanking.client.LedgerServiceClientImpl();
    }
    
    /**
     * Configure compliance service client
     */
    @Bean
    @ConditionalOnMissingBean(name = "complianceServiceClient")
    public com.waqiti.corebanking.client.ComplianceServiceClient complianceServiceClient() {
        log.info("Compliance service client configured");
        return new com.waqiti.corebanking.client.ComplianceServiceClientImpl();
    }
    
    /**
     * Configure notification service client
     */
    @Bean
    @ConditionalOnMissingBean(name = "notificationServiceClient")
    public com.waqiti.corebanking.client.NotificationServiceClient notificationServiceClient() {
        log.info("Notification service client configured");
        return new com.waqiti.corebanking.client.NotificationServiceClientImpl();
    }
    
    /**
     * Configure account management service
     */
    @Bean
    @ConditionalOnMissingBean
    public com.waqiti.corebanking.service.AccountManagementService accountManagementService() {
        log.info("Account management service configured");
        return new com.waqiti.corebanking.service.impl.AccountManagementServiceImpl();
    }
    
    /**
     * Configure compliance integration service
     */
    @Bean
    @ConditionalOnMissingBean
    public com.waqiti.corebanking.service.ComplianceIntegrationService complianceIntegrationService() {
        log.info("Compliance integration service configured");
        return new com.waqiti.corebanking.service.impl.ComplianceIntegrationServiceImpl();
    }
    
    /**
     * Configure double entry bookkeeping service
     */
    @Bean
    @ConditionalOnMissingBean
    public com.waqiti.corebanking.service.DoubleEntryBookkeepingService doubleEntryBookkeepingService() {
        log.info("Double entry bookkeeping service configured");
        return new com.waqiti.corebanking.service.impl.DoubleEntryBookkeepingServiceImpl();
    }
    
    
}