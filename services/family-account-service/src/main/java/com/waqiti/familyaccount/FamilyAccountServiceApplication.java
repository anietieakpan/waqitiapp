package com.waqiti.familyaccount;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Family Account Service Application
 * 
 * Provides comprehensive family account management including:
 * - Family group creation and management
 * - Parental controls and spending limits
 * - Child account management
 * - Family sharing features
 * - Educational financial tools
 * - Family analytics and reporting
 */
@SpringBootApplication(scanBasePackages = {
    "com.waqiti.familyaccount",
    "com.waqiti.common"
})
@EnableEurekaClient
@EnableFeignClients
@EnableKafka
@EnableCaching
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
public class FamilyAccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FamilyAccountServiceApplication.class, args);
    }
}