package com.waqiti.infrastructure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Infrastructure Service Application
 * 
 * Provides comprehensive system reliability and disaster recovery capabilities including:
 * - System health monitoring and alerting
 * - Automated backup operations with retention management
 * - Capacity monitoring and auto-scaling triggers
 * - Incident management and automated response procedures
 * - Infrastructure reporting and availability metrics
 * - Disaster recovery coordination and testing
 */
@SpringBootApplication(scanBasePackages = {
    "com.waqiti.infrastructure",
    "com.waqiti.common"
})
@EnableFeignClients
@EnableMongoAuditing
@EnableKafka
@EnableAsync
@EnableScheduling
public class InfrastructureServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InfrastructureServiceApplication.class, args);
    }
}