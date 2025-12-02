package com.waqiti.billingorchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Billing Orchestrator Service Application
 * 
 * Handles:
 * - Billing cycle management
 * - Invoice generation and delivery
 * - Payment collection orchestration
 * - Subscription billing
 * - Usage-based billing
 * - Dunning management
 * - Revenue recognition
 */
@SpringBootApplication(scanBasePackages = {"com.waqiti.billingorchestrator", "com.waqiti.common"})
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
@EnableAsync
public class BillingOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingOrchestratorApplication.class, args);
    }
}