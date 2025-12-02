package com.waqiti.tokenization;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Tokenization Service Application
 *
 * PCI-DSS compliant tokenization service for secure sensitive data storage.
 *
 * Features:
 * - Cryptographically secure token generation
 * - Token-to-data mapping with AWS KMS encryption
 * - PCI-DSS compliant card data tokenization
 * - Token lifecycle management (creation, validation, revocation, expiration)
 * - Audit logging for compliance
 * - Rate limiting and security controls
 * - High availability with Redis caching
 *
 * Security:
 * - All sensitive data encrypted at rest with AWS KMS
 * - Tokens are cryptographically random (256-bit)
 * - No plaintext card data stored
 * - Comprehensive audit trail
 * - Role-based access control
 *
 * Compliance:
 * - PCI-DSS v4.0 Requirement 3.2: Tokenization of PAN
 * - PCI-DSS Requirement 10: Audit logging
 * - SOC 2 compliant
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-11
 */
@SpringBootApplication(scanBasePackages = {
    "com.waqiti.tokenization",
    "com.waqiti.common"
})
@EnableDiscoveryClient
@EnableFeignClients
@EnableKafka
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
public class TokenizationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TokenizationServiceApplication.class, args);
    }
}
