package com.waqiti.account;

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
 * Account Service Application
 * 
 * Core Banking Account Management Microservice:
 * - Complete account lifecycle management
 * - Multi-currency account support
 * - Account hierarchy and relationships
 * - Balance management and limits
 * - Compliance and security controls
 * - Real-time account status monitoring
 * - Account statements and reporting
 * 
 * This microservice handles all account-related operations
 * in the core banking system.
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
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}