package com.waqiti.business;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Business Service Application
 * 
 * Provides business account management and B2B payment capabilities:
 * - Business account creation and verification
 * - Corporate payment processing
 * - Multi-user business wallet management
 * - Expense management and reporting
 * - Business-specific compliance features
 */
@SpringBootApplication(scanBasePackages = {
    "com.waqiti.business",
    "com.waqiti.common"
})
@EnableEurekaClient
@EnableKafka
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
public class BusinessServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BusinessServiceApplication.class, args);
    }
}