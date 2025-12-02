package com.waqiti.compliance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Compliance Service Application
 * 
 * Core Banking Regulatory Compliance Microservice:
 * - AML (Anti-Money Laundering) compliance monitoring
 * - KYC (Know Your Customer) validation and verification
 * - BSA (Bank Secrecy Act) reporting and compliance
 * - OFAC (Office of Foreign Assets Control) sanctions screening
 * - Suspicious Activity Reporting (SAR) generation
 * - PEP (Politically Exposed Person) screening
 * - Transaction monitoring and pattern analysis
 * - Regulatory reporting and audit trail management
 * - Real-time compliance risk assessment
 * - Automated compliance workflows and escalations
 * 
 * This microservice ensures all banking operations
 * meet regulatory requirements and compliance standards.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableJpaRepositories
@EnableJpaAuditing
@EnableTransactionManagement
@EnableCaching
@EnableAsync
@EnableScheduling
public class ComplianceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComplianceServiceApplication.class, args);
    }
}