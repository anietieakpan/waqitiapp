package com.waqiti.layer2;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Layer 2 Blockchain Service - Main Application
 *
 * Provides Layer 2 scaling solutions for blockchain transactions:
 * - Optimistic Rollups (fast, cost-effective)
 * - ZK Rollups (privacy-focused, validity proofs)
 * - State Channels (instant, off-chain)
 * - Plasma Chains (high throughput, micropayments)
 *
 * Integrated with Arbitrum for production-grade Layer 2 functionality.
 *
 * @author Waqiti Platform
 * @version 1.0.0
 */
@SpringBootApplication(scanBasePackages = {
    "com.waqiti.layer2",
    "com.waqiti.common"
})
@EnableEurekaClient
@EnableFeignClients(basePackages = "com.waqiti.layer2.client")
@EnableKafka
@EnableAsync
@EnableScheduling
@EnableJpaAuditing
@Slf4j
public class Layer2ServiceApplication {

    public static void main(String[] args) {
        try {
            log.info("========================================");
            log.info("Starting Layer 2 Blockchain Service");
            log.info("========================================");

            SpringApplication.run(Layer2ServiceApplication.java, args);

            log.info("========================================");
            log.info("Layer 2 Service Started Successfully");
            log.info("========================================");
        } catch (Exception e) {
            log.error("Failed to start Layer 2 Service", e);
            System.exit(1);
        }
    }
}
