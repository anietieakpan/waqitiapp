package com.waqiti.bankintegration.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.bankintegration.service.BankVerificationService;
import com.waqiti.bankintegration.service.AccountValidationService;
import com.waqiti.bankintegration.service.KYCVerificationService;
import com.waqiti.bankintegration.service.RiskAssessmentService;
import com.waqiti.bankintegration.service.AuditService;
import com.waqiti.bankintegration.entity.BankVerification;
import com.waqiti.bankintegration.entity.AccountValidation;
import com.waqiti.bankintegration.entity.KYCCheck;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

/**
 * Critical Event Consumer #2: Bank Verification Events Consumer
 * Processes bank account verification, micro-deposits, and account ownership validation
 * Implements 12-step zero-tolerance processing for bank verification security
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BankVerificationEventsConsumer extends BaseKafkaConsumer {

    private final BankVerificationService bankVerificationService;
    private final AccountValidationService accountValidationService;
    private final KYCVerificationService kycVerificationService;
    private final RiskAssessmentService riskAssessmentService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "bank-verification-events", 
        groupId = "bank-verification-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR
    )
    @CircuitBreaker(name = "bank-verification-consumer")
    @Retry(name = "bank-verification-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleBankVerificationEvent(
            ConsumerRecord<String, String> record, 
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "bank-verification-event");
        MDC.put("partition", String.valueOf(partition));
        
        try {
            log.info("Step 1: Processing bank verification event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String userId = eventData.path("userId").asText();
            String accountNumber = eventData.path("accountNumber").asText();
            String routingNumber = eventData.path("routingNumber").asText();
            String bankName = eventData.path("bankName").asText();
            String accountType = eventData.path("accountType").asText(); // CHECKING, SAVINGS
            String verificationMethod = eventData.path("verificationMethod").asText(); // MICRO_DEPOSIT, INSTANT, PLAID
            String accountHolderName = eventData.path("accountHolderName").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            String verificationStatus = eventData.path("verificationStatus").asText();
            
            log.info("Step 2: Extracted verification details: userId={}, account={}, method={}, bank={}", 
                    userId, maskAccountNumber(accountNumber), verificationMethod, bankName);
            
            // Step 3: Bank verification initiation and validation
            log.info("Step 3: Initiating bank verification process and validating account details");
            BankVerification verification = bankVerificationService.createBankVerification(eventData);
            
            bankVerificationService.validateRoutingNumber(routingNumber);
            bankVerificationService.validateAccountNumber(accountNumber);
            bankVerificationService.validateBankName(bankName, routingNumber);
            bankVerificationService.checkBlacklistedBanks(routingNumber);
            
            if (!bankVerificationService.isValidAccountType(accountType)) {
                throw new IllegalStateException("Invalid account type: " + accountType);
            }
            
            // Step 4: Account ownership and identity verification
            log.info("Step 4: Verifying account ownership and validating identity information");
            AccountValidation accountValidation = accountValidationService.createAccountValidation(verification);
            
            accountValidationService.verifyAccountOwnership(userId, accountHolderName);
            accountValidationService.validateIdentityMatch(userId, accountHolderName);
            accountValidationService.checkNameVariations(accountHolderName);
            
            KYCCheck kycCheck = kycVerificationService.performKYCCheck(userId, accountHolderName);
            if (!kycVerificationService.isKYCCompliant(kycCheck)) {
                bankVerificationService.failVerification(verification, "KYC_NON_COMPLIANT");
                return;
            }
            
            // Step 5: Micro-deposit verification processing
            log.info("Step 5: Processing micro-deposit verification and amount validation");
            if ("MICRO_DEPOSIT".equals(verificationMethod)) {
                List<BigDecimal> microDepositAmounts = bankVerificationService.generateMicroDepositAmounts();
                bankVerificationService.initiateMicroDeposits(verification, microDepositAmounts);
                bankVerificationService.scheduleMicroDepositValidation(verification);
                
                BigDecimal totalMicroAmount = microDepositAmounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                bankVerificationService.validateMicroDepositLimits(totalMicroAmount);
                
                verification.setStatus("MICRO_DEPOSIT_PENDING");
            }
            
            // Step 6: Instant verification and third-party integration
            log.info("Step 6: Conducting instant verification through third-party providers");
            if ("INSTANT".equals(verificationMethod) || "PLAID".equals(verificationMethod)) {
                boolean instantVerified = bankVerificationService.performInstantVerification(verification);
                
                if (instantVerified) {
                    accountValidationService.confirmAccountActive(verification);
                    verification.setStatus("VERIFIED");
                } else {
                    bankVerificationService.fallbackToMicroDeposit(verification);
                }
                
                bankVerificationService.validateThirdPartyResponse(verification);
                bankVerificationService.updateProviderMetrics(verificationMethod);
            }
            
            // Step 7: Risk assessment and fraud detection
            log.info("Step 7: Conducting risk assessment and fraud detection analysis");
            int riskScore = riskAssessmentService.calculateVerificationRiskScore(verification);
            
            riskAssessmentService.checkSuspiciousAccounts(accountNumber, routingNumber);
            riskAssessmentService.validateAccountAge(verification);
            riskAssessmentService.checkMultipleVerificationAttempts(userId);
            
            if (riskScore > 80) {
                riskAssessmentService.escalateHighRiskVerification(verification);
                bankVerificationService.requireManualReview(verification);
            }
            
            riskAssessmentService.detectFraudulentPatterns(verification);
            
            // Step 8: Account activity and transaction history analysis
            log.info("Step 8: Analyzing account activity and transaction history patterns");
            if ("VERIFIED".equals(verification.getStatus())) {
                accountValidationService.analyzeAccountActivity(verification);
                accountValidationService.validateTransactionHistory(verification);
                accountValidationService.checkAccountBalance(verification);
                
                boolean suspiciousActivity = accountValidationService.detectSuspiciousActivity(verification);
                if (suspiciousActivity) {
                    riskAssessmentService.flagSuspiciousAccount(verification);
                }
                
                accountValidationService.updateAccountMetadata(verification);
            }
            
            // Step 9: Compliance and regulatory validation
            log.info("Step 9: Ensuring compliance with banking regulations and requirements");
            bankVerificationService.validateBSACompliance(verification);
            bankVerificationService.checkOFACLists(accountHolderName);
            bankVerificationService.validateAMLRequirements(verification);
            
            if (bankVerificationService.requiresCTRReporting(verification)) {
                bankVerificationService.generateCTRReport(verification);
            }
            
            bankVerificationService.updateComplianceMetrics(verification);
            
            // Step 10: Verification completion and account linking
            log.info("Step 10: Completing verification process and linking bank account");
            if ("VERIFIED".equals(verification.getStatus())) {
                bankVerificationService.linkBankAccount(verification);
                bankVerificationService.enableACHTransfers(verification);
                bankVerificationService.setTransactionLimits(verification);
                
                bankVerificationService.generateVerificationCertificate(verification);
                bankVerificationService.notifyUserOfSuccess(verification);
            } else {
                bankVerificationService.scheduleRetryAttempt(verification);
                bankVerificationService.notifyUserOfPendingStatus(verification);
            }
            
            // Step 11: Security measures and monitoring setup
            log.info("Step 11: Implementing security measures and continuous monitoring");
            bankVerificationService.enableFraudMonitoring(verification);
            bankVerificationService.setAccountAlerts(verification);
            bankVerificationService.configureTransactionLimits(verification);
            
            riskAssessmentService.establishBaselineActivity(verification);
            riskAssessmentService.configureBehavioralMonitoring(verification);
            
            // Step 12: Audit trail and reporting
            log.info("Step 12: Completing audit trail and generating verification reports");
            auditService.logBankVerification(verification);
            auditService.logAccountValidation(accountValidation);
            auditService.logKYCCheck(kycCheck);
            
            bankVerificationService.updateVerificationMetrics(verification);
            accountValidationService.updateValidationStatistics(accountValidation);
            
            auditService.generateVerificationReport(verification);
            auditService.updateRegulatoryReporting(verification);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed bank verification: userId={}, eventId={}, status={}", 
                    userId, eventId, verification.getStatus());
            
        } catch (Exception e) {
            log.error("Error processing bank verification event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("userId") || 
            !eventData.has("accountNumber") || !eventData.has("routingNumber") ||
            !eventData.has("bankName") || !eventData.has("accountType") ||
            !eventData.has("verificationMethod") || !eventData.has("accountHolderName") ||
            !eventData.has("timestamp") || !eventData.has("verificationStatus")) {
            throw new IllegalArgumentException("Invalid bank verification event structure");
        }
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }
}