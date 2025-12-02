package com.waqiti.customer;

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
 * Main Spring Boot Application for Customer Service
 *
 * This service handles customer relationship management, lifecycle events,
 * complaints, account closures, and regulatory compliance notifications.
 *
 * Architecture:
 * - Event-driven microservice with Kafka integration
 * - REST API for customer queries and operations
 * - PostgreSQL for data persistence
 * - Redis for caching
 * - Eureka for service discovery
 * - Keycloak for authentication/authorization
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.waqiti.customer.client")
@EnableJpaRepositories(basePackages = "com.waqiti.customer.repository")
@EnableJpaAuditing
@EnableKafka
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
@ComponentScan(basePackages = {
    "com.waqiti.customer",
    "com.waqiti.common"  // Include shared common module
})
public class CustomerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}
