package com.waqiti.payroll;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Payroll Service Application
 *
 * Enterprise-grade payroll processing and tax compliance microservice
 *
 * FEATURES:
 * - Employee payroll batch processing
 * - Tax calculation and withholding (Federal, State, FICA, FUTA, SUI)
 * - Regulatory compliance (FLSA, Equal Pay Act, AML)
 * - ACH/Direct deposit bank transfers
 * - Tax reporting and regulatory filing
 * - Comprehensive audit trails
 *
 * COMPLIANCE:
 * - Fair Labor Standards Act (FLSA)
 * - Federal Insurance Contributions Act (FICA)
 * - Federal Unemployment Tax Act (FUTA)
 * - State Unemployment Insurance (SUI)
 * - Equal Pay Act
 * - Anti-Money Laundering (AML)
 *
 * SLA: 99.99% uptime, <60s processing time
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2025-11-08
 */
@SpringBootApplication(scanBasePackages = {"com.waqiti.payroll", "com.waqiti.common"})
@EnableDiscoveryClient
@EnableFeignClients
@EnableKafka
@EnableJpaAuditing
@EnableTransactionManagement
@EnableRetry
@EnableAsync
@EnableScheduling
public class PayrollServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayrollServiceApplication.class, args);
    }
}
