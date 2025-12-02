package com.waqiti.billpayment;

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
 * Bill Payment Service Application
 * 
 * This service manages utility bill payments, recurring bill scheduling,
 * biller integration, payment reminders, and bill payment history.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.waqiti.billpayment", "com.waqiti.common"})
@EnableJpaRepositories(basePackages = "com.waqiti.billpayment.repository")
@ComponentScan(basePackages = {"com.waqiti.billpayment", "com.waqiti.common"})
@EnableTransactionManagement
@EnableScheduling
@EnableAsync
public class BillPaymentServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(BillPaymentServiceApplication.class, args);
    }
}