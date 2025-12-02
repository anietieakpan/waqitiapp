package com.waqiti.corebanking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Core Banking Service Application
 * 
 * Provides comprehensive internal banking functionality:
 * - Double-entry bookkeeping system
 * - Account management and lifecycle
 * - Transaction processing engine
 * - Regulatory compliance framework
 * - Real-time reconciliation
 * - Multi-currency support
 * - Audit trails and reporting
 * 
 * This service replaces external banking platforms (like Cyclos) 
 * with a fully-owned, customizable banking solution.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableJpaRepositories
@EnableJpaAuditing
@EnableTransactionManagement
@EnableCaching
@EnableAsync
@EnableScheduling
public class CoreBankingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreBankingServiceApplication.class, args);
    }
}