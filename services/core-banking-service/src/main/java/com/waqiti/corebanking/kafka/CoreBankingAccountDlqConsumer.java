package com.waqiti.corebanking.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.corebanking.service.CoreBankingService;
import com.waqiti.corebanking.service.AccountIntegrityService;
import com.waqiti.corebanking.repository.CoreBankingAccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * DLQ Consumer for core banking account failures.
 * Handles critical core banking account management errors with immediate escalation.
 */
@Component
@Slf4j
public class CoreBankingAccountDlqConsumer extends BaseDlqConsumer {

    private final CoreBankingService coreBankingService;
    private final AccountIntegrityService accountIntegrityService;
    private final CoreBankingAccountRepository accountRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public CoreBankingAccountDlqConsumer(DlqHandler dlqHandler,
                                       AuditService auditService,
                                       NotificationService notificationService,
                                       MeterRegistry meterRegistry,
                                       CoreBankingService coreBankingService,
                                       AccountIntegrityService accountIntegrityService,
                                       CoreBankingAccountRepository accountRepository,
                                       KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.coreBankingService = coreBankingService;
        this.accountIntegrityService = accountIntegrityService;
        this.accountRepository = accountRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
        topics = {"core-banking-account.DLQ"},
        groupId = "core-banking-account-dlq-consumer-group",
        containerFactory = "criticalCoreBankingKafkaListenerContainerFactory"
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "core-banking-account-dlq", fallbackMethod = "handleCoreBankingAccountDlqFallback")
    public void handleCoreBankingAccountDlq(@Payload Object originalMessage,
                                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                          @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                          @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                          @Header(KafkaHeaders.OFFSET) long offset,
                                          Acknowledgment acknowledgment,
                                          @Header Map<String, Object> headers) {

        log.info("Processing core banking account DLQ message: topic={}, partition={}, offset={}",
            topic, partition, offset);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String accountId = extractAccountId(originalMessage);
            String customerId = extractCustomerId(originalMessage);
            String accountType = extractAccountType(originalMessage);
            String accountStatus = extractAccountStatus(originalMessage);
            String balance = extractBalance(originalMessage);
            String correlationId = extractCorrelationId(null, originalMessage);

            log.info("Processing core banking account DLQ: accountId={}, customerId={}, type={}, status={}, messageId={}",
                accountId, customerId, accountType, accountStatus, messageId);

            // Validate account state and integrity
            if (accountId != null) {
                validateAccountState(accountId, messageId);
                assessAccountIntegrity(accountId, originalMessage, messageId);
                handleAccountRecovery(accountId, customerId, originalMessage, exceptionMessage, messageId);
            }

            // Generate critical alerts
            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

            // Check for customer impact
            assessCustomerAccountImpact(customerId, accountType, accountStatus, originalMessage, messageId);

            // Handle regulatory compliance impact
            assessRegulatoryAccountImpact(accountId, accountType, balance, originalMessage, messageId);

            // Trigger manual account review
            triggerManualAccountReview(accountId, customerId, accountType, accountStatus, messageId);

        } catch (Exception e) {
            log.error("Error in core banking account DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "core-banking-account-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "CORE_BANKING";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        String accountType = extractAccountType(originalMessage);
        String balance = extractBalance(originalMessage);
        String accountStatus = extractAccountStatus(originalMessage);

        // Business accounts are critical
        if (isBusinessAccount(accountType)) {
            return true;
        }

        // High balance accounts are critical
        if (isHighBalanceAccount(balance)) {
            return true;
        }

        // Active accounts are critical
        return isActiveAccount(accountStatus);
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String accountId = extractAccountId(originalMessage);
        String customerId = extractCustomerId(originalMessage);
        String accountType = extractAccountType(originalMessage);
        String accountStatus = extractAccountStatus(originalMessage);
        String balance = extractBalance(originalMessage);

        try {
            // CRITICAL escalation for core banking account failures
            String alertTitle = String.format("CORE BANKING CRITICAL: Account Management Failed - %s",
                accountType != null ? accountType : "Unknown Account");
            String alertMessage = String.format(
                "🏦 CORE BANKING ACCOUNT FAILURE 🏦\n\n" +
                "A core banking account operation has FAILED and requires IMMEDIATE attention:\n\n" +
                "Account ID: %s\n" +
                "Customer ID: %s\n" +
                "Account Type: %s\n" +
                "Account Status: %s\n" +
                "Balance: %s\n" +
                "Error: %s\n\n" +
                "🚨 CRITICAL: Core banking account failures can cause:\n" +
                "- Customer fund access issues\n" +
                "- Account balance discrepancies\n" +
                "- Regulatory compliance violations\n" +
                "- Financial system integrity problems\n\n" +
                "IMMEDIATE core banking operations and customer service escalation required.",
                accountId != null ? accountId : "unknown",
                customerId != null ? customerId : "unknown",
                accountType != null ? accountType : "unknown",
                accountStatus != null ? accountStatus : "unknown",
                balance != null ? balance : "unknown",
                exceptionMessage
            );

            // Send core banking operations alert
            notificationService.sendCoreBankingAlert(alertTitle, alertMessage, "CRITICAL");

            // Send account management alert
            notificationService.sendAccountOpsAlert(
                "URGENT: Core Banking Account Failed",
                alertMessage,
                "CRITICAL"
            );

            // Send customer service alert for customer impact
            notificationService.sendCustomerServiceAlert(
                "Customer Account Issue - Core Banking",
                String.format("Customer %s account %s failed in core banking. " +
                    "Immediate customer communication and account access verification required.",
                    customerId, accountId),
                "HIGH"
            );

            // Business account specific alerts
            if (isBusinessAccount(accountType)) {
                notificationService.sendBusinessAccountAlert(
                    "Business Account Core Banking Failure",
                    String.format("Business account %s failed in core banking. " +
                        "Review commercial banking impact and client communication.", accountId),
                    "HIGH"
                );
            }

            // High balance account alerts
            if (isHighBalanceAccount(balance)) {
                notificationService.sendHighValueAccountAlert(
                    "HIGH BALANCE ACCOUNT FAILED",
                    String.format("High-balance account %s (%s) failed in core banking. " +
                        "Immediate review for financial impact and customer escalation.", accountId, balance),
                    "CRITICAL"
                );
            }

            // Regulatory compliance alert for certain account types
            if (isRegulatoryAccount(accountType)) {
                notificationService.sendRegulatoryAlert(
                    "Regulatory Account Failed",
                    String.format("Regulatory compliant account %s failed in core banking. " +
                        "Review compliance requirements and regulatory reporting impact.", accountId),
                    "HIGH"
                );
            }

            // Technology escalation for system integrity
            notificationService.sendTechnologyAlert(
                "Core Banking Account System Alert",
                String.format("Core banking account management failure may indicate system integrity issues. " +
                    "Account: %s, Customer: %s", accountId, customerId),
                "HIGH"
            );

            // Risk management alert
            notificationService.sendRiskManagementAlert(
                "Account Management Risk Alert",
                String.format("Core banking account %s failure may impact customer relationships and financial operations.", accountId),
                "MEDIUM"
            );

        } catch (Exception e) {
            log.error("Failed to send core banking account DLQ notifications: {}", e.getMessage());
        }
    }

    private void validateAccountState(String accountId, String messageId) {
        try {
            var account = accountRepository.findById(accountId);
            if (account.isPresent()) {
                String status = account.get().getStatus();
                String state = account.get().getState();
                String accountType = account.get().getAccountType();

                log.info("Core banking account state validation: accountId={}, status={}, state={}, type={}, messageId={}",
                    accountId, status, state, accountType, messageId);

                // Check for critical account states
                if ("FROZEN".equals(status) || "SUSPENDED".equals(status)) {
                    log.warn("Critical account state in DLQ: accountId={}, status={}", accountId, status);

                    notificationService.sendCoreBankingAlert(
                        "CRITICAL: Frozen/Suspended Account in DLQ",
                        String.format("Account %s with status %s found in core banking DLQ. " +
                            "This may indicate system inconsistencies. Immediate review required.", accountId, status),
                        "CRITICAL"
                    );
                }

                // Check for regulatory compliance states
                if ("PENDING_CLOSURE".equals(state) || "DORMANT".equals(state)) {
                    notificationService.sendRegulatoryAlert(
                        "Regulatory Account State Failed",
                        String.format("Account %s with regulatory state %s failed. " +
                            "Review compliance and reporting requirements.", accountId, state),
                        "HIGH"
                    );
                }
            } else {
                log.warn("Account not found despite accountId present: accountId={}, messageId={}", accountId, messageId);
            }

        } catch (Exception e) {
            log.error("Error validating account state: accountId={}, error={}",
                accountId, e.getMessage());
        }
    }

    private void assessAccountIntegrity(String accountId, Object originalMessage, String messageId) {
        try {
            // Validate account data integrity
            boolean integrityValid = accountIntegrityService.validateAccountIntegrity(accountId);
            if (!integrityValid) {
                log.error("Account integrity validation failed: accountId={}", accountId);

                notificationService.sendCoreBankingAlert(
                    "CRITICAL: Account Data Integrity Failure",
                    String.format("Account %s failed data integrity validation in DLQ processing. " +
                        "Immediate data consistency review required.", accountId),
                    "CRITICAL"
                );

                // Create data integrity incident
                kafkaTemplate.send("data-integrity-incidents", Map.of(
                    "accountId", accountId,
                    "incidentType", "ACCOUNT_INTEGRITY_FAILURE",
                    "severity", "CRITICAL",
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

        } catch (Exception e) {
            log.error("Error assessing account integrity: accountId={}, error={}",
                accountId, e.getMessage());
        }
    }

    private void handleAccountRecovery(String accountId, String customerId, Object originalMessage,
                                     String exceptionMessage, String messageId) {
        try {
            // Attempt automatic account recovery
            boolean recoveryAttempted = coreBankingService.attemptAccountRecovery(
                accountId, customerId, exceptionMessage);

            if (recoveryAttempted) {
                log.info("Automatic account recovery attempted: accountId={}, customerId={}",
                    accountId, customerId);

                kafkaTemplate.send("account-recovery-queue", Map.of(
                    "accountId", accountId,
                    "customerId", customerId,
                    "recoveryType", "AUTOMATIC_DLQ_RECOVERY",
                    "originalError", exceptionMessage,
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            } else {
                log.warn("Automatic account recovery not possible: accountId={}", accountId);

                notificationService.sendCoreBankingAlert(
                    "Manual Account Recovery Required",
                    String.format("Core banking account %s requires manual recovery intervention. " +
                        "Automatic recovery was not successful.", accountId),
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Error handling account recovery: accountId={}, error={}",
                accountId, e.getMessage());
        }
    }

    private void assessCustomerAccountImpact(String customerId, String accountType, String accountStatus,
                                           Object originalMessage, String messageId) {
        try {
            if (customerId != null) {
                // Check for primary account impact
                boolean isPrimaryAccount = coreBankingService.isPrimaryAccount(customerId, accountType);
                if (isPrimaryAccount) {
                    log.warn("Primary account failure impacts customer: customerId={}, accountType={}",
                        customerId, accountType);

                    notificationService.sendCustomerServiceAlert(
                        "CRITICAL: Primary Account Failed",
                        String.format("Primary account failure for customer %s. " +
                            "This significantly impacts customer banking access. " +
                            "Immediate customer service intervention required.", customerId),
                        "HIGH"
                    );
                }

                // Check for VIP customer impact
                if (coreBankingService.isVipCustomer(customerId)) {
                    notificationService.sendVipCustomerAlert(
                        "VIP Customer Account Failed",
                        String.format("VIP customer %s account failed in core banking. " +
                            "Priority escalation and white-glove recovery required.", customerId),
                        "HIGH"
                    );
                }

                // Check for business customer impact
                if (coreBankingService.isBusinessCustomer(customerId)) {
                    notificationService.sendBusinessAccountAlert(
                        "Business Customer Account Failed",
                        String.format("Business customer %s account failed in core banking. " +
                            "Review commercial banking procedures and client impact.", customerId),
                        "HIGH"
                    );
                }
            }
        } catch (Exception e) {
            log.error("Error assessing customer account impact: error={}", e.getMessage());
        }
    }

    private void assessRegulatoryAccountImpact(String accountId, String accountType, String balance,
                                             Object originalMessage, String messageId) {
        try {
            if (isRegulatoryReportableAccount(accountType, balance)) {
                log.warn("Regulatory reportable account failure: accountId={}, type={}, balance={}",
                    accountId, accountType, balance);

                notificationService.sendRegulatoryAlert(
                    "CRITICAL: Regulatory Account Failed",
                    String.format("Regulatory reportable account %s (type: %s, balance: %s) failed. " +
                        "This may impact regulatory reporting and compliance. " +
                        "Immediate compliance team review required.", accountId, accountType, balance),
                    "CRITICAL"
                );

                // Create regulatory compliance incident
                kafkaTemplate.send("regulatory-compliance-incidents", Map.of(
                    "accountId", accountId,
                    "accountType", accountType,
                    "balance", balance,
                    "incidentType", "REGULATORY_ACCOUNT_FAILURE",
                    "severity", "HIGH",
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

        } catch (Exception e) {
            log.error("Error assessing regulatory account impact: accountId={}, error={}",
                accountId, e.getMessage());
        }
    }

    private void triggerManualAccountReview(String accountId, String customerId, String accountType,
                                          String accountStatus, String messageId) {
        try {
            // All core banking account DLQ requires manual review
            kafkaTemplate.send("manual-account-review-queue", Map.of(
                "accountId", accountId,
                "customerId", customerId,
                "accountType", accountType,
                "accountStatus", accountStatus,
                "reviewReason", "CORE_BANKING_DLQ_FAILURE",
                "priority", "HIGH",
                "messageId", messageId,
                "requiresIntegrityCheck", true,
                "customerImpact", true,
                "timestamp", Instant.now()
            ));

            log.info("Triggered manual account review for core banking DLQ: accountId={}", accountId);
        } catch (Exception e) {
            log.error("Error triggering manual account review: accountId={}, error={}",
                accountId, e.getMessage());
        }
    }

    // Circuit breaker fallback method
    public void handleCoreBankingAccountDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                                   int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String accountId = extractAccountId(originalMessage);
        String customerId = extractCustomerId(originalMessage);
        String accountType = extractAccountType(originalMessage);

        // EMERGENCY situation - core banking account circuit breaker
        try {
            notificationService.sendExecutiveAlert(
                "EMERGENCY: Core Banking Account DLQ Circuit Breaker",
                String.format("CRITICAL SYSTEM FAILURE: Core banking account DLQ circuit breaker triggered " +
                    "for account %s (customer %s, type %s). " +
                    "This represents complete failure of core banking account management. " +
                    "IMMEDIATE C-LEVEL, TECHNOLOGY, AND OPERATIONS ESCALATION REQUIRED.",
                    accountId, customerId, accountType)
            );

            // Mark as emergency customer service issue
            coreBankingService.markEmergencyAccountIssue(accountId, customerId, "CIRCUIT_BREAKER_CORE_BANKING_ACCOUNT_DLQ");

        } catch (Exception e) {
            log.error("Error in core banking account DLQ fallback: {}", e.getMessage());
        }
    }

    // Helper methods for account classification
    private boolean isBusinessAccount(String accountType) {
        return accountType != null && accountType.toLowerCase().contains("business");
    }

    private boolean isHighBalanceAccount(String balance) {
        try {
            if (balance != null) {
                BigDecimal accountBalance = new BigDecimal(balance);
                return accountBalance.compareTo(new BigDecimal("100000")) >= 0; // $100,000+
            }
        } catch (Exception e) {
            log.debug("Could not parse account balance: {}", balance);
        }
        return false;
    }

    private boolean isActiveAccount(String accountStatus) {
        return accountStatus != null && "ACTIVE".equals(accountStatus);
    }

    private boolean isRegulatoryAccount(String accountType) {
        return accountType != null && (
            accountType.contains("BUSINESS") || accountType.contains("TRUST") ||
            accountType.contains("ESCROW") || accountType.contains("FIDUCIARY")
        );
    }

    private boolean isRegulatoryReportableAccount(String accountType, String balance) {
        return isRegulatoryAccount(accountType) || isHighBalanceAccount(balance);
    }

    // Data extraction helper methods
    private String extractAccountId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object accountId = messageMap.get("accountId");
                if (accountId == null) accountId = messageMap.get("id");
                return accountId != null ? accountId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract accountId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractCustomerId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object customerId = messageMap.get("customerId");
                if (customerId == null) customerId = messageMap.get("userId");
                return customerId != null ? customerId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract customerId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractAccountType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object accountType = messageMap.get("accountType");
                if (accountType == null) accountType = messageMap.get("type");
                return accountType != null ? accountType.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract accountType from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractAccountStatus(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object status = messageMap.get("accountStatus");
                if (status == null) status = messageMap.get("status");
                return status != null ? status.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract accountStatus from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractBalance(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object balance = messageMap.get("balance");
                if (balance == null) balance = messageMap.get("currentBalance");
                return balance != null ? balance.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract balance from message: {}", e.getMessage());
        }
        return null;
    }
}