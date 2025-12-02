/**
 * SMS/USSD Banking Service Application
 * Provides basic banking operations via SMS and USSD channels
 * Ensures financial inclusion for users without smartphones
 */
package com.waqiti.smsbanking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.waqiti")
@EnableJpaAuditing
@EnableTransactionManagement
@EnableAsync
@EnableScheduling
@EnableKafka
public class SmsUssdBankingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmsUssdBankingServiceApplication.class, args);
    }
}