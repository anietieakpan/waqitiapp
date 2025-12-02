package com.waqiti.account.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.account.service.AccountService;
import com.waqiti.account.service.AccountValidationService;
import com.waqiti.account.repository.AccountRepository;
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

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DLQ Consumer for account creation failures.
 * Handles critical account creation errors with regulatory compliance and customer onboarding.
 */
@Component
@Slf4j
public class AccountCreationDlqConsumer extends BaseDlqConsumer {

    private final AccountService accountService;
    private final AccountValidationService accountValidationService;
    private final AccountRepository accountRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AccountCreationDlqConsumer(DlqHandler dlqHandler,
                                     AuditService auditService,
                                     NotificationService notificationService,
                                     MeterRegistry meterRegistry,
                                     AccountService accountService,
                                     AccountValidationService accountValidationService,
                                     AccountRepository accountRepository,
                                     KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.accountService = accountService;
        this.accountValidationService = accountValidationService;
        this.accountRepository = accountRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
        topics = {"account-creation.DLQ"},
        groupId = "account-creation-dlq-consumer-group",
        containerFactory = "criticalAccountKafkaListenerContainerFactory"
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "account-creation-dlq", fallbackMethod = "handleAccountCreationDlqFallback")
    public void handleAccountCreationDlq(@Payload Object originalMessage,
                                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                        @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                        @Header(KafkaHeaders.OFFSET) long offset,
                                        Acknowledgment acknowledgment,
                                        @Header Map<String, Object> headers) {

        log.info("Processing account creation DLQ message: topic={}, partition={}, offset={}",
            topic, partition, offset);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String accountId = extractAccountId(originalMessage);
            String userId = extractUserId(originalMessage);
            String accountType = extractAccountType(originalMessage);
            String accountStatus = extractAccountStatus(originalMessage);
            String kycStatus = extractKycStatus(originalMessage);
            String applicationId = extractApplicationId(originalMessage);
            String correlationId = extractCorrelationId(null, originalMessage);

            log.info("Processing account creation DLQ: accountId={}, userId={}, type={}, status={}, messageId={}",
                accountId, userId, accountType, accountStatus, messageId);

            // Validate account creation status and check for regulatory compliance
            if (accountId != null || applicationId != null) {
                validateAccountCreationStatus(accountId, applicationId, messageId);
                assessRegulatoryCompliance(accountId, accountType, kycStatus, originalMessage, messageId);
                handleAccountCreationRecovery(accountId, applicationId, accountType, originalMessage, exceptionMessage, messageId);
            }

            // Generate domain-specific alerts with regulatory urgency
            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

            // Check for customer onboarding impact
            assessCustomerOnboardingImpact(userId, accountType, originalMessage, messageId);

            // Handle specific account creation failure types
            handleSpecificAccountCreationFailure(accountType, accountId, originalMessage, messageId);

            // Trigger manual account creation review
            triggerManualAccountCreationReview(accountId, applicationId, userId, accountType, messageId);

        } catch (Exception e) {
            log.error("Error in account creation DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "account-creation-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "ACCOUNT_MANAGEMENT";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        String accountType = extractAccountType(originalMessage);
        String kycStatus = extractKycStatus(originalMessage);
        String accountStatus = extractAccountStatus(originalMessage);

        // Critical if business account
        if (isBusinessAccount(accountType)) {
            return true;
        }

        // Critical if KYC-related failure
        if (isKycCritical(kycStatus)) {
            return true;
        }

        // Critical if pre-approved account
        return isPreApprovedAccount(accountStatus);
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String accountId = extractAccountId(originalMessage);
        String userId = extractUserId(originalMessage);
        String accountType = extractAccountType(originalMessage);
        String accountStatus = extractAccountStatus(originalMessage);
        String kycStatus = extractKycStatus(originalMessage);
        String applicationId = extractApplicationId(originalMessage);

        try {
            // IMMEDIATE escalation for account creation failures - these have regulatory and customer onboarding implications
            String alertTitle = String.format("CUSTOMER CRITICAL: Account Creation Failed - %s",
                accountType != null ? accountType : "Unknown Account Type");
            String alertMessage = String.format(
                "🏦 ACCOUNT CREATION FAILURE ALERT 🏦\n\n" +
                "An account creation event has failed and requires IMMEDIATE attention:\n\n" +
                "Account ID: %s\n" +
                "Application ID: %s\n" +
                "User ID: %s\n" +
                "Account Type: %s\n" +
                "Account Status: %s\n" +
                "KYC Status: %s\n" +
                "Error: %s\n\n" +
                "🚨 CRITICAL: This failure may result in customer onboarding delays or regulatory violations.\n" +
                "Immediate account operations and compliance intervention required.",
                accountId != null ? accountId : "unknown",
                applicationId != null ? applicationId : "unknown",
                userId != null ? userId : "unknown",
                accountType != null ? accountType : "unknown",
                accountStatus != null ? accountStatus : "unknown",
                kycStatus != null ? kycStatus : "unknown",
                exceptionMessage
            );

            // Send account ops alert for all account creation DLQ issues
            notificationService.sendAccountOpsAlert(alertTitle, alertMessage, "CRITICAL");

            // Send specific onboarding alert
            notificationService.sendOnboardingAlert(
                "URGENT: Account Creation DLQ",
                alertMessage,
                "HIGH"
            );

            // Send KYC/compliance alert if relevant
            if (isKycRelated(kycStatus, exceptionMessage)) {
                notificationService.sendKycAlert(
                    "KYC Account Creation Failed",
                    String.format("KYC-related account creation %s failed for user %s. " +
                        "Review KYC compliance and regulatory requirements.", accountId, userId),
                    "HIGH"
                );
            }

            // Customer notification for account creation issue
            if (userId != null) {
                notificationService.sendNotification(userId,
                    "Account Setup Issue",
                    String.format("We're experiencing a technical issue setting up your %s account. " +
                        "Our team is working to resolve this immediately. We'll update you as soon as your account is ready.",
                        accountType != null ? accountType.toLowerCase() : "new"),
                    messageId);
            }

            // Business account escalation
            if (isBusinessAccount(accountType)) {
                notificationService.sendBusinessAccountAlert(
                    "Business Account Creation Failed",
                    String.format("Business account creation %s failed. " +
                        "Review business onboarding procedures and client communication.", accountId),
                    "HIGH"
                );
            }

            // Compliance alert for regulatory accounts
            if (isRegulatoryCompliantAccountType(accountType)) {
                notificationService.sendComplianceAlert(
                    "Regulatory Account Creation Failed",
                    String.format("Regulatory compliant account %s creation failed. " +
                        "Review compliance requirements and regulatory reporting impact.", accountId),
                    "HIGH"
                );
            }

            // Risk management alert for account creation patterns
            notificationService.sendRiskManagementAlert(
                "Account Creation Risk Alert",
                String.format("Account creation failure for user %s may indicate system or process risks. " +
                    "Review account creation infrastructure.", userId),
                "MEDIUM"
            );

            // Customer service alert for follow-up
            notificationService.sendCustomerServiceAlert(
                "Customer Account Creation Follow-up Required",
                String.format("Customer %s account creation failed. " +
                    "Customer service follow-up required for expectation management.", userId),
                "MEDIUM"
            );

        } catch (Exception e) {
            log.error("Failed to send account creation DLQ notifications: {}", e.getMessage());
        }
    }

    private void validateAccountCreationStatus(String accountId, String applicationId, String messageId) {
        try {
            // Check account status if account was partially created
            if (accountId != null) {
                var account = accountRepository.findById(UUID.fromString(accountId));
                if (account.isPresent()) {
                    String status = String.valueOf(account.get().getStatus());
                    String state = account.get().getState();

                    log.info("Account creation status validation for DLQ: accountId={}, status={}, state={}, messageId={}",
                        accountId, status, state, messageId);

                    // Check for critical account states
                    if ("PENDING_ACTIVATION".equals(status) || "INCOMPLETE".equals(status)) {
                        log.warn("Critical account creation state in DLQ: accountId={}, status={}", accountId, status);

                        notificationService.sendAccountOpsAlert(
                            "URGENT: Critical Account State Failed",
                            String.format("Account %s in critical state %s has failed processing. " +
                                "Immediate account operations review required.", accountId, status),
                            "CRITICAL"
                        );
                    }

                    // Check for regulatory compliance states
                    if ("PENDING_KYC".equals(state) || "PENDING_COMPLIANCE".equals(state)) {
                        notificationService.sendComplianceAlert(
                            "Compliance Account State Failed",
                            String.format("Account %s with compliance state %s failed processing. " +
                                "Review regulatory requirements.", accountId, state),
                            "HIGH"
                        );
                    }
                } else {
                    log.warn("Account not found despite accountId present: accountId={}, messageId={}", accountId, messageId);
                }
            }

            // Check application status
            if (applicationId != null) {
                boolean isValidApplication = accountService.isValidApplication(applicationId);
                if (!isValidApplication) {
                    log.error("Invalid application for DLQ: applicationId={}, messageId={}", applicationId, messageId);

                    notificationService.sendOnboardingAlert(
                        "Invalid Application in DLQ",
                        String.format("Invalid application %s found in account creation DLQ. " +
                            "Review application validation procedures.", applicationId),
                        "MEDIUM"
                    );
                }
            }

        } catch (Exception e) {
            log.error("Error validating account creation status for DLQ: accountId={}, error={}",
                accountId, e.getMessage());
        }
    }

    private void assessRegulatoryCompliance(String accountId, String accountType, String kycStatus,
                                          Object originalMessage, String messageId) {
        try {
            log.info("Assessing regulatory compliance: accountId={}, type={}, kycStatus={}", accountId, accountType, kycStatus);

            // Check for KYC compliance requirements
            if (requiresKycCompliance(accountType)) {
                log.warn("KYC-required account creation failure: accountId={}, type={}", accountId, accountType);

                notificationService.sendKycAlert(
                    "CRITICAL: KYC Required Account Failed",
                    String.format("Account %s of type %s requires KYC compliance and failed creation. " +
                        "This may violate anti-money laundering regulations. " +
                        "Immediate KYC team review required.", accountId, accountType),
                    "CRITICAL"
                );

                // Create KYC compliance incident
                kafkaTemplate.send("kyc-compliance-incidents", Map.of(
                    "accountId", accountId != null ? accountId : "unknown",
                    "accountType", accountType,
                    "kycStatus", kycStatus != null ? kycStatus : "unknown",
                    "incidentType", "ACCOUNT_CREATION_KYC_FAILURE",
                    "severity", "HIGH",
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }

            // Check for BSA/AML compliance
            if (isBsaAmlAccount(accountType)) {
                notificationService.sendAmlAlert(
                    "BSA/AML Account Creation Failed",
                    String.format("BSA/AML compliant account %s failed creation. " +
                        "Review Bank Secrecy Act compliance requirements.", accountId),
                    "HIGH"
                );
            }

            // Check for OFAC compliance
            if (requiresOfacScreening(accountType)) {
                notificationService.sendOfacAlert(
                    "OFAC Screening Account Failed",
                    String.format("Account %s requiring OFAC screening failed creation. " +
                        "Review sanctions screening compliance.", accountId),
                    "HIGH"
                );
            }

            // Assess CDD (Customer Due Diligence) requirements
            if (requiresCddCompliance(accountType)) {
                notificationService.sendComplianceAlert(
                    "CDD Required Account Failed",
                    String.format("Account %s requiring Customer Due Diligence failed creation. " +
                        "Review CDD compliance procedures.", accountId),
                    "MEDIUM"
                );
            }

        } catch (Exception e) {
            log.error("Error assessing regulatory compliance: accountId={}, error={}", accountId, e.getMessage());
        }
    }

    private void handleAccountCreationRecovery(String accountId, String applicationId, String accountType,
                                             Object originalMessage, String exceptionMessage, String messageId) {
        try {
            // Attempt automatic account creation recovery for recoverable failures
            boolean recoveryAttempted = accountService.attemptAccountCreationRecovery(
                accountId, applicationId, accountType, exceptionMessage);

            if (recoveryAttempted) {
                log.info("Automatic account creation recovery attempted: accountId={}, applicationId={}, type={}",
                    accountId, applicationId, accountType);

                kafkaTemplate.send("account-creation-recovery-queue", Map.of(
                    "accountId", accountId != null ? accountId : "unknown",
                    "applicationId", applicationId != null ? applicationId : "unknown",
                    "accountType", accountType,
                    "recoveryType", "AUTOMATIC_DLQ_RECOVERY",
                    "originalError", exceptionMessage,
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            } else {
                // Recovery not possible - escalate for manual intervention
                log.warn("Automatic account creation recovery not possible: accountId={}, applicationId={}",
                    accountId, applicationId);

                notificationService.sendAccountOpsAlert(
                    "Manual Account Creation Recovery Required",
                    String.format("Account creation %s (application %s) requires manual recovery intervention. " +
                        "Automatic recovery was not successful.", accountId, applicationId),
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Error handling account creation recovery: accountId={}, error={}", accountId, e.getMessage());
        }
    }

    private void assessCustomerOnboardingImpact(String userId, String accountType, Object originalMessage, String messageId) {
        try {
            if (userId != null) {
                // Check for customer onboarding journey impact
                boolean isFirstAccount = accountService.isFirstAccountForCustomer(userId);
                if (isFirstAccount) {
                    log.warn("First account creation failure impacts customer onboarding: userId={}, type={}",
                        userId, accountType);

                    notificationService.sendOnboardingAlert(
                        "CRITICAL: First Account Creation Failed",
                        String.format("First account creation for customer %s failed. " +
                            "This significantly impacts customer onboarding experience. " +
                            "Immediate customer success intervention required.", userId),
                        "HIGH"
                    );

                    // Create customer onboarding incident
                    kafkaTemplate.send("customer-onboarding-incidents", Map.of(
                        "userId", userId,
                        "accountType", accountType,
                        "incidentType", "FIRST_ACCOUNT_CREATION_FAILURE",
                        "priority", "HIGH",
                        "messageId", messageId,
                        "timestamp", Instant.now()
                    ));
                }

                // Check for existing customer expectations
                boolean hasWaitingCustomer = accountService.hasCustomerWaitingForAccount(userId, accountType);
                if (hasWaitingCustomer) {
                    notificationService.sendCustomerServiceAlert(
                        "Customer Waiting for Account Creation",
                        String.format("Customer %s is waiting for %s account creation that failed. " +
                            "Immediate customer communication and expectation management required.", userId, accountType),
                        "HIGH"
                    );
                }

                // Check for premium/VIP customer impact
                if (accountService.isPremiumCustomer(userId)) {
                    notificationService.sendVipCustomerAlert(
                        "VIP Customer Account Creation Failed",
                        String.format("VIP customer %s account creation failed. " +
                            "Priority escalation and white-glove recovery required.", userId),
                        "HIGH"
                    );
                }
            }
        } catch (Exception e) {
            log.error("Error assessing customer onboarding impact: error={}", e.getMessage());
        }
    }

    private void handleSpecificAccountCreationFailure(String accountType, String accountId, Object originalMessage, String messageId) {
        try {
            switch (accountType) {
                case "CHECKING":
                    handleCheckingAccountCreationFailure(accountId, originalMessage, messageId);
                    break;
                case "SAVINGS":
                    handleSavingsAccountCreationFailure(accountId, originalMessage, messageId);
                    break;
                case "BUSINESS_CHECKING":
                    handleBusinessCheckingCreationFailure(accountId, originalMessage, messageId);
                    break;
                case "MONEY_MARKET":
                    handleMoneyMarketAccountCreationFailure(accountId, originalMessage, messageId);
                    break;
                case "CERTIFICATE_OF_DEPOSIT":
                    handleCdAccountCreationFailure(accountId, originalMessage, messageId);
                    break;
                case "INVESTMENT":
                    handleInvestmentAccountCreationFailure(accountId, originalMessage, messageId);
                    break;
                case "CREDIT_CARD":
                    handleCreditCardAccountCreationFailure(accountId, originalMessage, messageId);
                    break;
                default:
                    log.info("No specific handling for account type: {}", accountType);
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling specific account creation failure: type={}, accountId={}, error={}",
                accountType, accountId, e.getMessage());
        }
    }

    private void handleCheckingAccountCreationFailure(String accountId, Object originalMessage, String messageId) {
        notificationService.sendAccountOpsAlert(
            "Checking Account Creation Failed",
            String.format("Checking account %s creation failed. Review basic banking service availability.", accountId),
            "HIGH"
        );
    }

    private void handleSavingsAccountCreationFailure(String accountId, Object originalMessage, String messageId) {
        notificationService.sendAccountOpsAlert(
            "Savings Account Creation Failed",
            String.format("Savings account %s creation failed. Review deposit product procedures.", accountId),
            "MEDIUM"
        );
    }

    private void handleBusinessCheckingCreationFailure(String accountId, Object originalMessage, String messageId) {
        notificationService.sendBusinessAccountAlert(
            "Business Checking Account Failed",
            String.format("Business checking account %s creation failed. " +
                "Review commercial banking procedures and client impact.", accountId),
            "HIGH"
        );
    }

    private void handleMoneyMarketAccountCreationFailure(String accountId, Object originalMessage, String messageId) {
        notificationService.sendAccountOpsAlert(
            "Money Market Account Failed",
            String.format("Money market account %s creation failed. Review investment product procedures.", accountId),
            "MEDIUM"
        );
    }

    private void handleCdAccountCreationFailure(String accountId, Object originalMessage, String messageId) {
        notificationService.sendAccountOpsAlert(
            "Certificate of Deposit Failed",
            String.format("CD account %s creation failed. Review term deposit procedures and rate commitments.", accountId),
            "MEDIUM"
        );
    }

    private void handleInvestmentAccountCreationFailure(String accountId, Object originalMessage, String messageId) {
        notificationService.sendInvestmentAlert(
            "Investment Account Creation Failed",
            String.format("Investment account %s creation failed. " +
                "Review securities regulations and suitability requirements.", accountId),
            "HIGH"
        );
    }

    private void handleCreditCardAccountCreationFailure(String accountId, Object originalMessage, String messageId) {
        notificationService.sendCreditAlert(
            "Credit Card Account Failed",
            String.format("Credit card account %s creation failed. " +
                "Review credit underwriting and approval processes.", accountId),
            "HIGH"
        );
    }

    private void triggerManualAccountCreationReview(String accountId, String applicationId, String userId,
                                                   String accountType, String messageId) {
        try {
            // All account creation DLQ messages require manual review due to customer impact
            kafkaTemplate.send("manual-account-creation-review-queue", Map.of(
                "accountId", accountId != null ? accountId : "unknown",
                "applicationId", applicationId != null ? applicationId : "unknown",
                "userId", userId,
                "accountType", accountType,
                "reviewReason", "DLQ_PROCESSING_FAILURE",
                "priority", "HIGH",
                "messageId", messageId,
                "customerImpact", true,
                "timestamp", Instant.now()
            ));

            log.info("Triggered manual account creation review for DLQ: accountId={}, applicationId={}, userId={}",
                accountId, applicationId, userId);
        } catch (Exception e) {
            log.error("Error triggering manual account creation review: accountId={}, error={}", accountId, e.getMessage());
        }
    }

    // Circuit breaker fallback method
    public void handleAccountCreationDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                                 int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String accountId = extractAccountId(originalMessage);
        String userId = extractUserId(originalMessage);
        String accountType = extractAccountType(originalMessage);

        // This is a CRITICAL situation - account creation system circuit breaker
        try {
            notificationService.sendExecutiveAlert(
                "CRITICAL SYSTEM FAILURE: Account Creation DLQ Circuit Breaker",
                String.format("EMERGENCY: Account Creation DLQ circuit breaker triggered for account %s (user %s, type %s). " +
                    "This represents a complete failure of account creation systems. " +
                    "IMMEDIATE C-LEVEL AND OPERATIONS ESCALATION REQUIRED.", accountId, userId, accountType)
            );

            // Mark as emergency customer service issue
            accountService.markEmergencyCustomerIssue(accountId, userId, "CIRCUIT_BREAKER_ACCOUNT_CREATION_DLQ");

        } catch (Exception e) {
            log.error("Error in account creation DLQ fallback: {}", e.getMessage());
        }
    }

    // Helper methods for classification
    private boolean isBusinessAccount(String accountType) {
        return accountType != null && accountType.toLowerCase().contains("business");
    }

    private boolean isKycCritical(String kycStatus) {
        return kycStatus != null && (
            kycStatus.contains("PENDING") || kycStatus.contains("REQUIRED") ||
            kycStatus.contains("FAILED")
        );
    }

    private boolean isPreApprovedAccount(String accountStatus) {
        return accountStatus != null && (
            accountStatus.contains("PRE_APPROVED") || accountStatus.contains("APPROVED")
        );
    }

    private boolean isKycRelated(String kycStatus, String exceptionMessage) {
        return (kycStatus != null && kycStatus.contains("KYC")) ||
               (exceptionMessage != null && exceptionMessage.toLowerCase().contains("kyc"));
    }

    private boolean isRegulatoryCompliantAccountType(String accountType) {
        return accountType != null && (
            accountType.contains("BUSINESS") || accountType.contains("INVESTMENT") ||
            accountType.contains("TRUST")
        );
    }

    private boolean requiresKycCompliance(String accountType) {
        return accountType != null && (
            accountType.contains("BUSINESS") || accountType.contains("INVESTMENT") ||
            accountType.contains("CHECKING") || accountType.contains("SAVINGS")
        );
    }

    private boolean isBsaAmlAccount(String accountType) {
        return accountType != null && (
            accountType.contains("BUSINESS") || accountType.contains("WIRE") ||
            accountType.contains("INTERNATIONAL")
        );
    }

    private boolean requiresOfacScreening(String accountType) {
        return accountType != null && (
            accountType.contains("BUSINESS") || accountType.contains("INTERNATIONAL") ||
            accountType.contains("INVESTMENT")
        );
    }

    private boolean requiresCddCompliance(String accountType) {
        return accountType != null && (
            accountType.contains("BUSINESS") || accountType.contains("HIGH_VALUE") ||
            accountType.contains("PRIVATE_BANKING")
        );
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

    private String extractUserId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object userId = messageMap.get("userId");
                if (userId == null) userId = messageMap.get("customerId");
                return userId != null ? userId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract userId from message: {}", e.getMessage());
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
                Object accountStatus = messageMap.get("accountStatus");
                if (accountStatus == null) accountStatus = messageMap.get("status");
                return accountStatus != null ? accountStatus.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract accountStatus from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractKycStatus(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object kycStatus = messageMap.get("kycStatus");
                return kycStatus != null ? kycStatus.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract kycStatus from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractApplicationId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object applicationId = messageMap.get("applicationId");
                return applicationId != null ? applicationId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract applicationId from message: {}", e.getMessage());
        }
        return null;
    }
}