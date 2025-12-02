package com.waqiti.kyc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableAsync
@EnableScheduling
@ComponentScan(basePackages = {"com.waqiti.kyc", "com.waqiti.common"})
public class KYCServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(KYCServiceApplication.class, args);
    }
}