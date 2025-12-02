package com.waqiti.risk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Risk Service Application
 *
 * Comprehensive risk assessment and fraud detection service providing:
 * - Real-time transaction risk scoring
 * - ML-based fraud detection
 * - Rule-based risk assessment
 * - Behavioral analysis
 * - Device fingerprinting
 * - Velocity checks
 * - Pattern recognition
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableKafka
@EnableAsync
@EnableCaching
@EnableScheduling
@EnableMongoRepositories(basePackages = "com.waqiti.risk.repository")
public class RiskServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskServiceApplication.class, args);
    }
}
