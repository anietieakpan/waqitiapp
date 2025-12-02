package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.payment.service.BankAccountVerificationService;
import com.waqiti.payment.service.FraudDetectionService;
import com.waqiti.payment.service.NotificationService;
import com.waqiti.payment.service.ComplianceService;
import com.waqiti.common.exception.PaymentProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka Consumer for Bank Account Verification Events
 * Handles micro-deposits, instant verification, and account validation processes
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BankAccountVerificationEventsConsumer {
    
    private final BankAccountVerificationService verificationService;
    private final FraudDetectionService fraudDetectionService;
    private final NotificationService notificationService;
    private final ComplianceService complianceService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final UniversalDLQHandler dlqHandler;
    
    @KafkaListener(
        topics = {"bank-account-verification-events", "micro-deposit-initiated", "instant-verification-completed", "account-verification-failed"},
        groupId = "payment-service-bank-verification-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000)
    )
    @Transactional
    public void handleBankAccountVerificationEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID verificationId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            verificationId = UUID.fromString((String) event.get("verificationId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            String verificationType = (String) event.get("verificationType"); // MICRO_DEPOSIT, INSTANT, MANUAL
            String bankAccountId = (String) event.get("bankAccountId");
            String routingNumber = (String) event.get("routingNumber");
            String accountNumber = (String) event.get("accountNumber");
            String accountType = (String) event.get("accountType"); // CHECKING, SAVINGS
            String bankName = (String) event.get("bankName");
            String accountHolderName = (String) event.get("accountHolderName");
            String verificationStatus = (String) event.get("verificationStatus"); // PENDING, VERIFIED, FAILED
            LocalDateTime timestamp = LocalDateTime.parse((String) event.get("timestamp"));
            
            // Verification specific fields
            BigDecimal microDepositAmount1 = event.containsKey("microDepositAmount1") ? 
                    new BigDecimal((String) event.get("microDepositAmount1")) : null;
            BigDecimal microDepositAmount2 = event.containsKey("microDepositAmount2") ? 
                    new BigDecimal((String) event.get("microDepositAmount2")) : null;
            String instantVerificationToken = (String) event.get("instantVerificationToken");
            String plaidAccountId = (String) event.get("plaidAccountId");
            String yodleeAccountId = (String) event.get("yodleeAccountId");
            String failureReason = (String) event.get("failureReason");
            Integer attemptCount = (Integer) event.getOrDefault("attemptCount", 1);
            LocalDateTime expiryDate = event.containsKey("expiryDate") ?
                    LocalDateTime.parse((String) event.get("expiryDate")) : null;
            
            log.info("Processing bank account verification event - VerificationId: {}, CustomerId: {}, Type: {}, Status: {}", 
                    verificationId, customerId, verificationType, verificationStatus);
            
            // Step 1: Validate bank account details
            Map<String, Object> accountValidation = verificationService.validateBankAccountDetails(
                    verificationId, routingNumber, accountNumber, accountType, bankName, accountHolderName);
            
            if ("INVALID".equals(accountValidation.get("status"))) {
                verificationService.failVerification(verificationId, 
                        (String) accountValidation.get("reason"), timestamp);
                log.warn("Bank account verification failed - Invalid details: {}", 
                        accountValidation.get("reason"));
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 2: Fraud detection for verification attempts
            Map<String, Object> fraudAssessment = fraudDetectionService.assessVerificationFraudRisk(
                    verificationId, customerId, routingNumber, bankName, verificationType, 
                    attemptCount, timestamp);
            
            String riskLevel = (String) fraudAssessment.get("riskLevel");
            if ("HIGH".equals(riskLevel) && attemptCount > 3) {
                verificationService.blockVerificationAttempts(verificationId, customerId, 
                        fraudAssessment, timestamp);
                log.warn("Bank verification blocked due to suspicious activity");
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 3: Check for duplicate verification attempts
            boolean isDuplicate = verificationService.checkDuplicateVerification(
                    customerId, routingNumber, accountNumber, timestamp);
            
            if (isDuplicate) {
                verificationService.handleDuplicateVerification(verificationId, customerId, 
                        routingNumber, accountNumber, timestamp);
                log.info("Duplicate verification detected - linking to existing verification");
            }
            
            // Step 4: Process based on verification type and event
            switch (eventType) {
                case "MICRO_DEPOSIT_INITIATED":
                    verificationService.initiateMicroDepositVerification(verificationId, customerId,
                            bankAccountId, routingNumber, accountNumber, accountType,
                            microDepositAmount1, microDepositAmount2, expiryDate, timestamp);
                    break;
                    
                case "INSTANT_VERIFICATION_COMPLETED":
                    verificationService.processInstantVerification(verificationId, customerId,
                            bankAccountId, instantVerificationToken, plaidAccountId, 
                            yodleeAccountId, verificationStatus, timestamp);
                    break;
                    
                case "ACCOUNT_VERIFICATION_FAILED":
                    verificationService.handleVerificationFailure(verificationId, customerId,
                            failureReason, attemptCount, timestamp);
                    break;
                    
                case "MICRO_DEPOSIT_VERIFIED":
                    BigDecimal submittedAmount1 = new BigDecimal((String) event.get("submittedAmount1"));
                    BigDecimal submittedAmount2 = new BigDecimal((String) event.get("submittedAmount2"));
                    verificationService.verifyMicroDepositAmounts(verificationId, submittedAmount1, 
                            submittedAmount2, timestamp);
                    break;
                    
                default:
                    verificationService.processGenericVerificationEvent(verificationId, eventType, 
                            event, timestamp);
            }
            
            // Step 5: Update account status based on verification result
            if ("VERIFIED".equals(verificationStatus)) {
                verificationService.activateBankAccount(verificationId, customerId, bankAccountId, 
                        routingNumber, accountNumber, timestamp);
                
                // Enable payment methods for verified account
                verificationService.enablePaymentMethods(customerId, bankAccountId, accountType, timestamp);
            }
            
            // Step 6: Compliance validation for verified accounts
            if ("VERIFIED".equals(verificationStatus)) {
                Map<String, Object> complianceCheck = complianceService.validateVerifiedAccount(
                        customerId, bankAccountId, bankName, accountHolderName, timestamp);
                
                if ("FLAGGED".equals(complianceCheck.get("status"))) {
                    verificationService.flagForComplianceReview(verificationId, complianceCheck, timestamp);
                }
            }
            
            // Step 7: Handle verification limits and cooling periods
            verificationService.updateVerificationLimits(customerId, verificationType, 
                    verificationStatus, attemptCount, timestamp);
            
            // Step 8: Send appropriate notifications
            notificationService.sendVerificationNotification(verificationId, customerId,
                    eventType, verificationType, verificationStatus, bankName, timestamp);
            
            // Step 9: Clean up expired verification attempts
            if (expiryDate != null && expiryDate.isBefore(LocalDateTime.now())) {
                verificationService.cleanupExpiredVerification(verificationId, timestamp);
            }
            
            // Step 10: Audit logging
            auditService.auditFinancialEvent(
                    "BANK_ACCOUNT_VERIFICATION_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Bank account verification event processed - Type: %s, Status: %s, Method: %s", 
                            eventType, verificationStatus, verificationType),
                    Map.of(
                            "verificationId", verificationId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "verificationType", verificationType,
                            "verificationStatus", verificationStatus,
                            "bankName", bankName,
                            "accountType", accountType,
                            "attemptCount", attemptCount.toString(),
                            "riskLevel", riskLevel,
                            "isDuplicate", String.valueOf(isDuplicate),
                            "routingNumber", routingNumber.substring(0, 3) + "XXXX", // Masked for security
                            "accountNumber", "XXXX" + accountNumber.substring(accountNumber.length() - 4)
                    )
            );
            
            acknowledgment.acknowledge();
            log.info("Successfully processed bank account verification event - VerificationId: {}, Status: {}", 
                    verificationId, verificationStatus);
            
        } catch (Exception e) {
            log.error("Bank account verification event processing failed - VerificationId: {}, EventType: {}, Error: {}",
                    verificationId, eventType, e.getMessage(), e);

            // Send to DLQ with context
            dlqHandler.handleFailedMessage(
                topic,
                eventJson,
                e,
                Map.of(
                    "verificationId", verificationId != null ? verificationId.toString() : "unknown",
                    "customerId", customerId != null ? customerId.toString() : "unknown",
                    "eventType", eventType != null ? eventType : "unknown",
                    "partition", String.valueOf(partition),
                    "offset", String.valueOf(offset)
                )
            );

            throw new PaymentProcessingException("Bank account verification event processing failed", e);
        }
    }
}