package com.waqiti.ledger.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.client.RestTemplate;

/**
 * Comprehensive configuration for Ledger Service.
 * 
 * Provides double-entry bookkeeping, financial reconciliation,
 * and distributed transaction management.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Configuration
@EnableJpaRepositories(basePackages = {
    "com.waqiti.ledger.repository"
})
@ComponentScan(basePackages = {
    "com.waqiti.ledger",
    "com.waqiti.common.distributed",
    "com.waqiti.common.encryption",
    "com.waqiti.common.audit",
    "com.waqiti.common.events"
})
@EnableFeignClients(basePackages = {
    "com.waqiti.ledger.client",
    "com.waqiti.common.client"
})
@EnableTransactionManagement
@EnableRetry
@EnableAsync
@Slf4j
public class LedgerServiceConfiguration {
    
    /**
     * Configure RestTemplate for ledger service
     */
    @Bean("ledgerRestTemplate")
    @ConditionalOnMissingBean(name = "ledgerRestTemplate")
    public RestTemplate ledgerRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        
        // Add custom error handler
        restTemplate.setErrorHandler(new com.waqiti.common.http.CustomResponseErrorHandler());
        
        // Add logging interceptor
        restTemplate.getInterceptors().add(
            new com.waqiti.common.http.LoggingClientHttpRequestInterceptor()
        );
        
        log.info("Ledger RestTemplate configured");
        return restTemplate;
    }
    
    /**
     * Configure production ledger service (PRIMARY to resolve multiple bean conflicts)
     */
    @Bean("productionLedgerService")
    @Primary
    @ConditionalOnMissingBean(name = "productionLedgerService")
    public com.waqiti.ledger.service.LedgerService productionLedgerService() {
        log.info("Production ledger service configured as primary");
        return new com.waqiti.ledger.service.impl.ProductionLedgerService();
    }
    
    /**
     * Configure legacy ledger service for backward compatibility
     */
    @Bean("legacyLedgerService")
    @ConditionalOnMissingBean(name = "legacyLedgerService")
    public com.waqiti.ledger.service.LedgerService legacyLedgerService() {
        log.info("Legacy ledger service configured for backward compatibility");
        return new com.waqiti.ledger.service.impl.LegacyLedgerService();
    }
    
    /**
     * Configure ledger account repository
     */
    @Bean
    @ConditionalOnMissingBean(name = "ledgerAccountRepository")
    public com.waqiti.ledger.repository.LedgerAccountRepository ledgerAccountRepository() {
        log.info("Ledger account repository configured");
        return new com.waqiti.ledger.repository.LedgerAccountRepositoryImpl();
    }
    
    /**
     * Configure reconciliation repository
     */
    @Bean
    @ConditionalOnMissingBean(name = "reconciliationRepository")
    public com.waqiti.ledger.repository.ReconciliationRepository reconciliationRepository() {
        log.info("Reconciliation repository configured");
        return new com.waqiti.ledger.repository.ReconciliationRepositoryImpl();
    }
    
    /**
     * Configure chart of accounts repository
     */
    @Bean
    @ConditionalOnMissingBean(name = "chartOfAccountsRepository")
    public com.waqiti.ledger.repository.ChartOfAccountsRepository chartOfAccountsRepository() {
        log.info("Chart of accounts repository configured");
        return new com.waqiti.ledger.repository.ChartOfAccountsRepositoryImpl();
    }
    
    /**
     * Configure ledger validator
     */
    @Bean
    @ConditionalOnMissingBean(name = "ledgerValidator")
    public com.waqiti.ledger.service.LedgerValidator ledgerValidator() {
        log.info("Ledger validator configured");
        return new com.waqiti.ledger.service.impl.LedgerValidatorImpl();
    }
    
    /**
     * Configure ledger audit service
     */
    @Bean
    @ConditionalOnMissingBean(name = "ledgerAuditService")
    public com.waqiti.ledger.service.LedgerAuditService ledgerAuditService() {
        log.info("Ledger audit service configured");
        return new com.waqiti.ledger.service.impl.LedgerAuditServiceImpl();
    }
    
    /**
     * Configure distributed lock service
     */
    @Bean
    @ConditionalOnMissingBean(name = "distributedLockService")
    public com.waqiti.common.distributed.DistributedLockService distributedLockService() {
        log.info("Distributed lock service configured for ledger");
        return new com.waqiti.common.distributed.DistributedLockService();
    }
    
    /**
     * Configure distributed cache service
     */
    @Bean
    @ConditionalOnMissingBean(name = "distributedCacheService")
    public com.waqiti.common.distributed.DistributedCacheService distributedCacheService() {
        log.info("Distributed cache service configured for ledger");
        return new com.waqiti.common.distributed.DistributedCacheService();
    }
    
    /**
     * Configure encryption service
     */
    @Bean
    @ConditionalOnMissingBean(name = "encryptionService")
    public com.waqiti.common.encryption.EncryptionService encryptionService() {
        log.info("Encryption service configured for ledger");
        return new com.waqiti.common.encryption.EncryptionService(null, null);
    }
    
    /**
     * Configure budget controller
     */
    @Bean
    @ConditionalOnMissingBean
    public com.waqiti.ledger.controller.BudgetController budgetController() {
        log.info("Budget controller configured");
        return new com.waqiti.ledger.controller.BudgetController();
    }
    
    /**
     * Configure ledger controller
     */
    @Bean
    @ConditionalOnMissingBean
    public com.waqiti.ledger.controller.LedgerController ledgerController() {
        log.info("Ledger controller configured");
        return new com.waqiti.ledger.controller.LedgerController();
    }
    
    /**
     * Configure payment event listener
     */
    @Bean
    @ConditionalOnMissingBean
    public com.waqiti.ledger.event.PaymentEventListener paymentEventListener() {
        log.info("Payment event listener configured");
        return new com.waqiti.ledger.event.PaymentEventListenerImpl();
    }
    
    /**
     * Configure payment event consumer
     */
    @Bean
    @ConditionalOnMissingBean
    public com.waqiti.ledger.kafka.PaymentEventConsumer paymentEventConsumer() {
        log.info("Payment event consumer configured");
        return new com.waqiti.ledger.kafka.PaymentEventConsumerImpl();
    }
    
    /**
     * Configure chargeback reserve service
     */
    @Bean
    @ConditionalOnMissingBean
    public com.waqiti.ledger.service.ChargebackReserveService chargebackReserveService() {
        log.info("Chargeback reserve service configured");
        return new com.waqiti.ledger.service.impl.ChargebackReserveServiceImpl();
    }
    
    /**
     * Configure enhanced double entry ledger service
     */
    @Bean
    @ConditionalOnMissingBean
    public com.waqiti.ledger.service.EnhancedDoubleEntryLedgerService enhancedDoubleEntryLedgerService() {
        log.info("Enhanced double entry ledger service configured");
        return new com.waqiti.ledger.service.impl.EnhancedDoubleEntryLedgerServiceImpl();
    }
}