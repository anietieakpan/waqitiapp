package com.waqiti.lending;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Main Spring Boot Application for Waqiti Lending Service
 *
 * This service handles comprehensive lending operations including:
 * - Loan origination and underwriting
 * - Credit assessment and risk evaluation
 * - Loan disbursement and servicing
 * - Payment processing and collections
 * - Loan modifications and restructuring
 * - Regulatory compliance (TILA, ECOA, FCRA, HMDA)
 *
 * Architecture:
 * - Event-driven using Kafka for async processing
 * - RESTful API for synchronous operations
 * - PostgreSQL for persistent storage
 * - Redis for caching and idempotency
 * - Feign clients for inter-service communication
 *
 * Security:
 * - OAuth2/Keycloak for authentication
 * - Role-based access control
 * - PII encryption and data masking
 *
 * @version 2.0.0
 * @since 2025-09-25
 */
@SpringBootApplication(scanBasePackages = {
    "com.waqiti.lending",
    "com.waqiti.common"
})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {
    "com.waqiti.lending.client",
    "com.waqiti.common.client"
})
@EnableKafka
@EnableJpaRepositories(basePackages = "com.waqiti.lending.repository")
@EnableJpaAuditing
@EnableTransactionManagement
@EnableCaching
@EnableAsync
@EnableScheduling
@EnableRetry
public class LendingServiceApplication {

    /**
     * Main entry point for the Lending Service
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        // Set system properties for security and performance
        System.setProperty("java.security.egd", "file:/dev/./urandom");
        System.setProperty("spring.application.name", "lending-service");

        // Start the application
        SpringApplication application = new SpringApplication(LendingServiceApplication.class);

        // Add shutdown hook for graceful termination
        application.registerShutdownHook();

        // Run the application
        application.run(args);
    }
}
