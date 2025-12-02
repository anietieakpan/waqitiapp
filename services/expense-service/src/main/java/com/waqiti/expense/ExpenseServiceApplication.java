package com.waqiti.expense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Waqiti Expense Tracking & Budgeting Service
 * 
 * Comprehensive expense management, budgeting, and financial planning service
 * that provides real-time expense tracking, intelligent categorization,
 * budget creation and monitoring, spending insights, and financial goal management.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.waqiti")
@EnableJpaAuditing
@EnableTransactionManagement
@EnableAsync
@EnableScheduling
@EnableCaching
@EnableKafka
public class ExpenseServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpenseServiceApplication.class, args);
    }
}