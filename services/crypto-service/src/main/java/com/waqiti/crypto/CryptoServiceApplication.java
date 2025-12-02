package com.waqiti.crypto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Cryptocurrency Service Application
 * 
 * This service handles cryptocurrency transactions, wallet management, blockchain integration,
 * crypto trading, price tracking, compliance screening, and fraud detection.
 * 
 * Features:
 * - Multi-blockchain support (Bitcoin, Ethereum, Litecoin)
 * - HD Wallet generation and management
 * - Multi-signature wallet support
 * - Real-time price tracking
 * - Advanced trading functionality
 * - Compliance and sanctions screening
 * - Address risk analysis
 * - KMS integration for security
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.waqiti.crypto", "com.waqiti.common"})
@EnableJpaRepositories(basePackages = "com.waqiti.crypto.repository")
@ComponentScan(basePackages = {"com.waqiti.crypto", "com.waqiti.common"})
@EnableTransactionManagement
@EnableScheduling
@EnableAsync
public class CryptoServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(CryptoServiceApplication.class, args);
    }
}