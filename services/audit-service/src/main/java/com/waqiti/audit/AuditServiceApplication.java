package com.waqiti.audit;

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
 * Audit Service Application
 * 
 * Core Banking Audit Trail and Compliance Logging Microservice:
 * - Comprehensive audit trail capturing for all banking operations
 * - Real-time activity monitoring and suspicious behavior detection
 * - Regulatory compliance audit logging (SOX, PCI DSS, GDPR)
 * - User access logging and privilege escalation tracking
 * - Transaction audit trails with complete data lineage
 * - System configuration change tracking and approval workflows
 * - Security event logging and incident response automation
 * - Data integrity validation and tamper detection
 * - Audit report generation and forensic analysis tools
 * - Immutable audit log storage with cryptographic verification
 * - Role-based audit access controls and data retention policies
 * - Integration with SIEM systems for security monitoring
 * 
 * This microservice ensures complete accountability and
 * traceability across the entire banking platform.
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
public class AuditServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditServiceApplication.class, args);
    }
}