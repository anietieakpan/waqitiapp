package com.waqiti.transaction;

import com.waqiti.payment.commons.config.WaqitiServiceConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.statemachine.config.EnableStateMachine;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Transaction Service Application
 * 
 * This service is responsible for:
 * - Managing the complete lifecycle of financial transactions
 * - Orchestrating funds transfers between wallets
 * - Ensuring transaction consistency and atomicity
 * - Handling transaction state transitions
 * - Managing transaction limits and validations
 * - Publishing transaction events
 * - Integrating with external payment processors
 * 
 * @author Waqiti Platform Team
 */
@SpringBootApplication(scanBasePackages = {
    "com.waqiti.transaction",
    "com.waqiti.common"
})
@EnableEurekaClient
@EnableFeignClients
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
@EnableStateMachine
public class TransactionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionServiceApplication.class, args);
    }
}