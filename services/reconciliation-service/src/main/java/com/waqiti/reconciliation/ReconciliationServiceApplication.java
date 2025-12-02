package com.waqiti.reconciliation;

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
 * Reconciliation Service Application
 * 
 * Core Banking Reconciliation and Settlement Microservice:
 * - Real-time transaction reconciliation and matching
 * - End-of-day balance reconciliation across all accounts
 * - Nostro/Vostro account reconciliation with partner banks
 * - Internal ledger balance validation and correction
 * - Settlement processing and confirmation tracking
 * - Exception handling and break investigation
 * - Automated reconciliation workflows
 * - Regulatory reconciliation reporting
 * - Multi-currency settlement management
 * - Variance analysis and resolution tracking
 * 
 * This microservice ensures financial integrity and
 * accuracy across the entire banking platform.
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
public class ReconciliationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReconciliationServiceApplication.class, args);
    }
}