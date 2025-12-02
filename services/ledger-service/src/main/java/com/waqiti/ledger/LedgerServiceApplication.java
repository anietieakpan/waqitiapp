package com.waqiti.ledger;

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
 * Ledger Service Application
 * 
 * Core Banking Double-Entry Bookkeeping Microservice:
 * - Comprehensive double-entry ledger system
 * - Real-time balance calculations and validations
 * - Multi-currency transaction processing
 * - Chart of accounts management
 * - Financial reporting and analytics
 * - Audit trail and compliance tracking
 * - Transaction reconciliation support
 * 
 * This microservice is the authoritative source for all
 * financial account balances and transaction records.
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
public class LedgerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerServiceApplication.class, args);
    }
}