package com.waqiti.webhook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot Application class for Webhook Service
 * Provides webhook management, delivery, and retry mechanisms
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
@ComponentScan(basePackages = {"com.waqiti.webhookservice", "com.waqiti.common"})
public class WebhookServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebhookServiceApplication.class, args);
    }
}