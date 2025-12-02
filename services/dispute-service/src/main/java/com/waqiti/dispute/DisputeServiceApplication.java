package com.waqiti.dispute;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Main Spring Boot Application class for Dispute Service
 * Provides comprehensive dispute resolution and transaction dispute management
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
@ComponentScan(basePackages = {"com.waqiti.dispute", "com.waqiti.common"})
public class DisputeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DisputeServiceApplication.class, args);
    }
}