package com.waqiti.accounting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Accounting Service Application
 * 
 * Provides comprehensive double-entry bookkeeping functionality including:
 * - Journal entries and general ledger
 * - Chart of accounts management
 * - Financial reporting (Trial Balance, P&L, Balance Sheet)
 * - Account reconciliation
 * - Settlement processing
 * - Audit trails and compliance reporting
 */
@SpringBootApplication(scanBasePackages = {
    "com.waqiti.accounting",
    "com.waqiti.common"
})
@EnableFeignClients
@EnableJdbcAuditing
@EnableTransactionManagement
@EnableKafka
@EnableAsync
@EnableScheduling
public class AccountingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountingServiceApplication.class, args);
    }
}