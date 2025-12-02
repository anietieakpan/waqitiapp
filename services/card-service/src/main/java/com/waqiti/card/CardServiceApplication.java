package com.waqiti.card;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Card Service Application
 *
 * Consolidated card management service handling:
 * - Card lifecycle management
 * - Transaction processing
 * - Authorization and fraud detection
 * - Disputes and chargebacks
 * - Statement generation
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated from card-service and card-processing-service)
 * @since 2025-11-09
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableTransactionManagement
@EnableKafka
@EnableScheduling
public class CardServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CardServiceApplication.class, args);
    }
}
