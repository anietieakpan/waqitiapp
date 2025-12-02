package com.waqiti.saga;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Saga Orchestration Service Application
 * 
 * This service implements the Saga pattern for managing distributed transactions
 * across multiple microservices in the Waqiti P2P Platform.
 * 
 * Key responsibilities:
 * - Orchestrate complex business transactions spanning multiple services
 * - Maintain transaction consistency across service boundaries
 * - Handle compensation actions for failed transactions
 * - Provide transaction visibility and monitoring
 * - Manage transaction timeouts and retries
 * - Support both orchestration and choreography patterns
 * 
 * Supported Saga Types:
 * - P2P Transfer Saga (Wallet -> Transaction -> Notification -> Analytics)
 * - Bank Deposit Saga (Bank -> Wallet -> Transaction -> Compliance)
 * - Bank Withdrawal Saga (Wallet -> Bank -> Transaction -> Notification)
 * - Payment Request Saga (Request -> Authorization -> Processing -> Settlement)
 * - Refund Saga (Refund -> Wallet -> Bank -> Notification)
 * 
 * @author Waqiti Platform Team
 */
@SpringBootApplication(scanBasePackages = {
    "com.waqiti.saga",
    "com.waqiti.common"
})
@EnableEurekaClient
@EnableFeignClients
@EnableAsync
@EnableScheduling
public class SagaOrchestrationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SagaOrchestrationServiceApplication.class, args);
    }
}