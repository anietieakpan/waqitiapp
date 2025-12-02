package com.waqiti.bankintegration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bank Integration Service Application
 * 
 * This service handles all external banking and payment provider integrations:
 * - Bank account verification and linking
 * - ACH transfers and wire transfers
 * - Card payment processing
 * - Digital wallet integrations
 * - Payment provider webhooks
 * - Settlement and reconciliation
 * - Multi-currency support
 * - Regulatory compliance reporting
 * 
 * @author Waqiti Platform Team
 */
@SpringBootApplication(scanBasePackages = {
    "com.waqiti.bankintegration",
    "com.waqiti.common"
})
@EnableEurekaClient
@EnableFeignClients
@EnableAsync
@EnableScheduling
public class BankIntegrationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankIntegrationServiceApplication.class, args);
    }
}