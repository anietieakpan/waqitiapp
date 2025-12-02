package com.waqiti.reporting;

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
 * Reporting Service Application
 * 
 * Core Banking Financial Reporting and Analytics Microservice:
 * - Real-time financial dashboard and metrics
 * - Regulatory reporting (BSA, FinCEN, FFIEC)
 * - Management information system (MIS) reports
 * - Customer account statements and notifications
 * - Transaction analytics and trend analysis
 * - Risk management reporting and monitoring
 * - Compliance reporting and audit trails
 * - Multi-format report generation (PDF, Excel, CSV)
 * - Scheduled report automation and distribution
 * - Data visualization and business intelligence
 * - Performance metrics and KPI tracking
 * - Financial forecasting and predictive analytics
 * 
 * This microservice aggregates data from all banking
 * services to provide comprehensive reporting capabilities.
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
public class ReportingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportingServiceApplication.class, args);
    }
}