package com.waqiti.rewards;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Main application class for the Rewards Service
 * 
 * This service handles:
 * - Cashback calculation and processing
 * - Points accumulation and management
 * - Loyalty tier progression
 * - Rewards redemption
 * - Campaign and merchant offer management
 */
@SpringBootApplication(scanBasePackages = {
    "com.waqiti.rewards",
    "com.waqiti.common"
})
@EnableDiscoveryClient
@EnableJpaRepositories
@EnableTransactionManagement
@EnableAsync
@EnableScheduling
@EnableCaching
public class RewardsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RewardsServiceApplication.class, args);
    }
}