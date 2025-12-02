package com.waqiti.insurance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Insurance Service Application
 *
 * Provides comprehensive insurance product management including:
 * - Policy lifecycle management (creation, renewal, modification, cancellation)
 * - Claims processing and settlement
 * - Premium calculation and payment processing
 * - Underwriting and risk assessment
 * - Actuarial analysis and pricing
 * - Regulatory compliance and reporting
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableKafka
@EnableAsync
@EnableScheduling
@EnableJpaRepositories(basePackages = "com.waqiti.insurance.repository")
@EnableJpaAuditing
@EnableTransactionManagement
public class InsuranceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InsuranceServiceApplication.class, args);
    }
}
