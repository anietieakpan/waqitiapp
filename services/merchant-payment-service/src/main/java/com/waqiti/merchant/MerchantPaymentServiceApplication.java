package com.waqiti.merchant;

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
 * Merchant Payment Service Application
 * 
 * Comprehensive merchant payment processing service providing:
 * - Merchant account registration and management
 * - Payment processing with multiple methods
 * - QR code payment generation and processing
 * - POS terminal management
 * - Settlement and payout processing
 * - Real-time analytics and reporting
 * - Fraud detection and prevention
 * - Webhook notifications
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
public class MerchantPaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MerchantPaymentServiceApplication.class, args);
    }
}