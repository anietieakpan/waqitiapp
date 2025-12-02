package com.waqiti.purchaseprotection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Purchase Protection Service Application
 * 
 * Provides comprehensive purchase protection including:
 * - Chargeback management and dispute resolution
 * - Purchase insurance and warranty tracking
 * - Fraud protection and monitoring
 * - Merchant dispute mediation
 * - Consumer protection compliance
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.waqiti.protection.client", "com.waqiti.common.client"})
@EnableJpaRepositories(basePackages = "com.waqiti.protection.repository")
@EnableJpaAuditing
@EnableTransactionManagement
@EnableAsync
@EnableScheduling
@EnableKafka
@ComponentScan(basePackages = {"com.waqiti.protection", "com.waqiti.common"})
public class PurchaseProtectionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PurchaseProtectionServiceApplication.class, args);
    }
}