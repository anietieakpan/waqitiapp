package com.waqiti.atm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * ATM Service Application
 * 
 * This service handles cardless ATM operations, QR code generation for ATM withdrawals,
 * ATM location services, and cash withdrawal management.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.waqiti.atm", "com.waqiti.common"})
@EnableJpaRepositories(basePackages = "com.waqiti.atm.repository")
@ComponentScan(basePackages = {"com.waqiti.atm", "com.waqiti.common"})
@EnableTransactionManagement
@EnableScheduling
@EnableAsync
public class ATMServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ATMServiceApplication.class, args);
    }
}