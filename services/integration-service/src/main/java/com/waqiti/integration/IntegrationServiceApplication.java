package com.waqiti.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableDiscoveryClient
@EnableRetry
public class IntegrationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntegrationServiceApplication.class, args);
    }
}