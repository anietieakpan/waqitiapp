package com.waqiti.tax;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Tax Service Application - CashApp-style Tax Filing Platform
 * 
 * Provides comprehensive tax preparation and filing capabilities:
 * - Free tax filing for simple returns (income under $73k)
 * - Automatic import of W-2 and 1099 forms
 * - Cryptocurrency tax reporting (Form 8949)
 * - Investment income reporting (1099-B)
 * - IRS e-filing integration
 * - Maximum refund guarantee
 * - Direct deposit of refunds
 * - Year-round tax planning and estimation
 * - Multi-state tax filing
 * - Premium features for complex returns
 */
@SpringBootApplication(scanBasePackages = {
    "com.waqiti.tax",
    "com.waqiti.common"
})
@EnableEurekaClient
@EnableFeignClients
@EnableKafka
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
@EnableCaching
@EnableJpaRepositories(basePackages = "com.waqiti.tax.repository")
@ConfigurationPropertiesScan
public class TaxServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaxServiceApplication.class, args);
    }
}