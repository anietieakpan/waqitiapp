package com.waqiti.payment;

import com.waqiti.payment.commons.config.WaqitiServiceConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Waqiti Payment Service Application
 * 
 * Production-ready payment processing microservice providing:
 * - P2P transfers and payments
 * - Multi-network cash deposit integration
 * - NFC and contactless payments  
 * - Advanced fraud detection with ML
 * - Comprehensive saga-based transaction management
 * - Real-time reconciliation and settlement
 * - Rate limiting and security controls
 * - Multi-provider payment routing
 * - International transfers and FX
 * - Subscription and recurring payments
 * 
 * Architecture:
 * - Event-driven with Kafka messaging
 * - SAGA pattern for distributed transactions
 * - Circuit breakers and resilience patterns
 * - Distributed tracing and monitoring
 * - Redis-based caching and rate limiting
 * - PostgreSQL with optimistic locking
 * - OAuth2/JWT security with Keycloak
 * 
 * @author Waqiti Payment Team
 * @version 2.0.0
 * @since 2025-01-16
 */
@SpringBootApplication(scanBasePackages = "com.waqiti.payment")
@EnableFeignClients(basePackages = {
    "com.waqiti.payment.client"
})
@EnableJpaRepositories(basePackages = "com.waqiti.payment.repository")
@EnableKafka
@EnableScheduling
@EnableAsync
@EnableTransactionManagement
@Import(WaqitiServiceConfiguration.class)
public class PaymentServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}