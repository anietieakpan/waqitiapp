package com.waqiti.virtualcard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Main application class for the Virtual Card Service
 * 
 * This service handles:
 * - Virtual card creation and management
 * - Card transaction processing
 * - Spending limits and controls
 * - Card funding and withdrawals
 * - Transaction analytics and insights
 * - Fraud detection and security
 */
@SpringBootApplication(scanBasePackages = {
    "com.waqiti.virtualcard",
    "com.waqiti.common"
})
@EnableDiscoveryClient
@EnableJpaRepositories
@EnableTransactionManagement
@EnableAsync
@EnableScheduling
@EnableCaching
public class VirtualCardServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VirtualCardServiceApplication.class, args);
    }
}