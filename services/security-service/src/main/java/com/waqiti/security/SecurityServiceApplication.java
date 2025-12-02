package com.waqiti.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients(basePackages = {
    "com.waqiti.security",  // Local Feign clients
    "com.waqiti.compliance.contracts.client"  // Compliance service client from shared contracts
})
@EnableKafka
@EnableAsync
@EnableScheduling
public class SecurityServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SecurityServiceApplication.class, args);
    }
}