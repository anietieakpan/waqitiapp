package com.waqiti.integration.config;

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
 * Comprehensive configuration for Integration Service.
 * 
 * Provides external system integration, API management,
 * and service mesh coordination.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Configuration
@EnableJpaRepositories(basePackages = {
    "com.waqiti.integration.repository"
})
@ComponentScan(basePackages = {
    "com.waqiti.integration",
    "com.waqiti.common.audit",
    "com.waqiti.common.security",
    "com.waqiti.common.events"
})
@EnableFeignClients(basePackages = {
    "com.waqiti.integration.client",
    "com.waqiti.common.client"
})
@EnableTransactionManagement
@EnableRetry
@EnableAsync
@Slf4j
public class IntegrationServiceConfiguration {
    
    /**
     * Configure RestTemplate for integration service
     */
    @Bean("integrationRestTemplate")
    @ConditionalOnMissingBean(name = "integrationRestTemplate")
    public RestTemplate integrationRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        
        // Add custom error handler
        restTemplate.setErrorHandler(new com.waqiti.common.http.CustomResponseErrorHandler());
        
        // Add logging interceptor
        restTemplate.getInterceptors().add(
            new com.waqiti.common.http.LoggingClientHttpRequestInterceptor()
        );
        
        log.info("Integration RestTemplate configured");
        return restTemplate;
    }
    
    /**
     * Configure audit service
     */
    @Bean
    @ConditionalOnMissingBean(name = "auditService")
    public com.waqiti.common.audit.AuditService auditService() {
        log.info("Audit service configured for integration");
        return new com.waqiti.common.audit.AuditServiceImpl();
    }
    
    /**
     * Configure ledger service client
     */
    @Bean
    @ConditionalOnMissingBean(name = "ledgerServiceClient")
    public com.waqiti.integration.client.LedgerServiceClient ledgerServiceClient() {
        log.info("Ledger service client configured");
        return new com.waqiti.integration.client.LedgerServiceClientImpl();
    }
    
    /**
     * Configure notification service client
     */
    @Bean
    @ConditionalOnMissingBean(name = "notificationServiceClient")
    public com.waqiti.integration.client.NotificationServiceClient notificationServiceClient() {
        log.info("Notification service client configured");
        return new com.waqiti.integration.client.NotificationServiceClientImpl();
    }
    
    /**
     * Configure compliance service client
     */
    @Bean
    @ConditionalOnMissingBean(name = "complianceServiceClient")
    public com.waqiti.integration.client.ComplianceServiceClient complianceServiceClient() {
        log.info("Compliance service client configured");
        return new com.waqiti.integration.client.ComplianceServiceClientImpl();
    }
    
    /**
     * Configure integration audit service
     */
    @Bean
    @ConditionalOnMissingBean
    public com.waqiti.integration.audit.IntegrationAuditService integrationAuditService() {
        log.info("Integration audit service configured");
        return new com.waqiti.integration.audit.IntegrationAuditServiceImpl();
    }
    
    /**
     * Configure integration metrics
     */
    @Bean
    @ConditionalOnMissingBean
    public com.waqiti.integration.metrics.IntegrationMetrics integrationMetrics() {
        log.info("Integration metrics configured");
        return new com.waqiti.integration.metrics.IntegrationMetricsImpl();
    }
    
    /**
     * Configure internal core banking service
     */
    @Bean
    @ConditionalOnMissingBean
    public com.waqiti.integration.service.InternalCoreBankingService internalCoreBankingService() {
        log.info("Internal core banking service configured");
        return new com.waqiti.integration.service.impl.InternalCoreBankingServiceImpl();
    }
}