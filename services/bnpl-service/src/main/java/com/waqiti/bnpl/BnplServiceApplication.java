/**
 * Lending Service Application
 * Comprehensive lending platform supporting BNPL and traditional loan products
 * Formerly BNPL Service - now expanded to full lending capabilities
 * 
 * Features:
 * - Buy Now Pay Later (BNPL) products
 * - Traditional loan management (Personal, Business, Education, etc.)
 * - Comprehensive repayment scheduling
 * - Credit scoring and risk assessment
 * - Loan portfolio management
 * - Advanced transaction tracking
 */
package com.waqiti.bnpl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.waqiti")
@EnableJpaAuditing
@EnableTransactionManagement
@EnableAsync
@EnableScheduling
@EnableCaching
@EnableKafka
public class BnplServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BnplServiceApplication.class, args);
    }
}