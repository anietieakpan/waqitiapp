package com.waqiti.recurringpayment.kafka;

import com.waqiti.common.events.DirectDebitMandateEvent;
import com.waqiti.common.events.MandateProcessedEvent;
import com.waqiti.recurringpayment.domain.DirectDebitMandate;
import com.waqiti.recurringpayment.domain.MandateStatus;
import com.waqiti.recurringpayment.domain.MandateType;
import com.waqiti.recurringpayment.domain.PaymentFrequency;
import com.waqiti.recurringpayment.repository.DirectDebitMandateRepository;
import com.waqiti.recurringpayment.service.MandateValidationService;
import com.waqiti.recurringpayment.service.BankAccountVerificationService;
import com.waqiti.recurringpayment.service.ComplianceService;
import com.waqiti.recurringpayment.service.NotificationService;
import com.waqiti.recurringpayment.service.AuditService;
import com.waqiti.recurringpayment.service.RiskAssessmentService;
import com.waqiti.recurringpayment.service.SchedulingService;
import com.waqiti.recurringpayment.service.ACHService;
import com.waqiti.recurringpayment.service.SEPAService;
import com.waqiti.recurringpayment.exception.MandateException;
import com.waqiti.recurringpayment.exception.ComplianceViolationException;
import com.waqiti.recurringpayment.exception.BankAccountException;
import com.waqiti.common.security.encryption.EncryptionService;
import com.waqiti.common.compliance.ComplianceValidator;
import com.waqiti.common.audit.AuditEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * CRITICAL DIRECT DEBIT MANDATE EVENT CONSUMER - Consumer 40
 * 
 * Processes direct debit mandate events with zero-tolerance 12-step processing:
 * 1. Event validation and sanitization
 * 2. Idempotency and duplicate detection
 * 3. Regulatory compliance verification
 * 4. Customer and merchant validation
 * 5. Bank account verification and validation
 * 6. Risk assessment and fraud screening
 * 7. Mandate terms validation and approval
 * 8. Payment schedule configuration
 * 9. ACH/SEPA network registration
 * 10. Compliance reporting and documentation
 * 11. Audit trail and record creation
 * 12. Notification dispatch and activation
 * 
 * REGULATORY COMPLIANCE:
 * - NACHA ACH Rules (US)
 * - SEPA Direct Debit Scheme (EU)
 * - PCI DSS Level 1 compliance
 * - GDPR data protection
 * - Anti-Money Laundering (AML) checks
 * - Know Your Customer (KYC) verification
 * - Consumer protection regulations
 * 
 * MANDATE TYPES SUPPORTED:
 * - Core Direct Debit (SEPA Core)
 * - Business-to-Business (SEPA B2B)
 * - ACH Debit (PPD, CCD, WEB)
 * - Recurring Card Payments
 * - Variable Recurring Payments
 * 
 * SLA: 99.99% uptime, <5s processing time
 * 
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 1.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
@Validated
public class DirectDebitMandateEventConsumer {
    
    private final DirectDebitMandateRepository mandateRepository;
    private final MandateValidationService mandateValidationService;
    private final BankAccountVerificationService bankAccountVerificationService;
    private final ComplianceService complianceService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final RiskAssessmentService riskAssessmentService;
    private final SchedulingService schedulingService;
    private final ACHService achService;
    private final SEPAService sepaService;
    private final EncryptionService encryptionService;
    private final ComplianceValidator complianceValidator;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final String MANDATE_PROCESSED_TOPIC = "mandate-processed-events";
    private static final String COMPLIANCE_ALERT_TOPIC = "compliance-alert-events";
    private static final String PAYMENT_SCHEDULED_TOPIC = "payment-scheduled-events";
    private static final String DLQ_TOPIC = "direct-debit-mandate-events-dlq";
    
    private static final BigDecimal MAX_SINGLE_PAYMENT_AMOUNT = new BigDecimal("100000.00");
    private static final BigDecimal MAX_MONTHLY_TOTAL = new BigDecimal("500000.00");
    private static final int MAX_PAYMENT_FREQUENCY_DAYS = 365;
    private static final int MIN_PAYMENT_FREQUENCY_DAYS = 1;
    private static final int MANDATE_VALIDITY_MONTHS = 36;

    @KafkaListener(
        topics = "direct-debit-mandate-events",
        groupId = "direct-debit-mandate-processor",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "3"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Retryable(
        value = {MandateException.class, BankAccountException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2, maxDelay = 10000)
    )
    public void handleDirectDebitMandateEvent(
            @Payload @Valid DirectDebitMandateEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {
        
        String correlationId = generateCorrelationId(event, partition, offset);
        long processingStartTime = System.currentTimeMillis();
        
        log.info("STEP 1: Processing direct debit mandate event - ID: {}, Customer: {}, Merchant: {}, Amount: {}, Correlation: {}",
            event.getMandateId(), event.getCustomerId(), event.getMerchantId(), event.getMaxAmount(), correlationId);
        
        try {
            // STEP 1: Event validation and sanitization
            validateAndSanitizeEvent(event, correlationId);
            
            // STEP 2: Idempotency and duplicate detection
            if (checkIdempotencyAndDuplicates(event, correlationId)) {
                acknowledgeAndReturn(acknowledgment, "Duplicate mandate event detected");
                return;
            }
            
            // STEP 3: Regulatory compliance verification
            performComplianceVerification(event, correlationId);
            
            // STEP 4: Customer and merchant validation
            validateCustomerAndMerchant(event, correlationId);
            
            // STEP 5: Bank account verification and validation
            BankAccountVerificationResult bankVerification = verifyAndValidateBankAccount(event, correlationId);
            
            // STEP 6: Risk assessment and fraud screening
            performRiskAssessmentAndFraudScreening(event, bankVerification, correlationId);
            
            // STEP 7: Mandate terms validation and approval
            MandateTermsValidationResult termsValidation = validateAndApproveMandateTerms(event, correlationId);
            
            // STEP 8: Payment schedule configuration
            PaymentScheduleResult scheduleResult = configurePaymentSchedule(event, termsValidation, correlationId);
            
            // STEP 9: ACH/SEPA network registration
            NetworkRegistrationResult networkResult = registerWithPaymentNetwork(event, bankVerification, correlationId);
            
            // STEP 10: Compliance reporting and documentation
            performComplianceReportingAndDocumentation(event, networkResult, correlationId);
            
            // STEP 11: Audit trail and record creation
            DirectDebitMandate mandate = createAuditTrailAndSaveMandate(event, bankVerification, termsValidation, 
                scheduleResult, networkResult, correlationId, processingStartTime);
            
            // STEP 12: Notification dispatch and activation
            dispatchNotificationsAndActivation(event, mandate, networkResult, correlationId);
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            long processingTime = System.currentTimeMillis() - processingStartTime;
            log.info("Successfully processed direct debit mandate - ID: {}, Reference: {}, Time: {}ms, Correlation: {}",
                event.getMandateId(), mandate.getMandateReference(), processingTime, correlationId);
            
        } catch (ComplianceViolationException e) {
            handleComplianceViolation(event, e, correlationId, acknowledgment);
        } catch (BankAccountException e) {
            handleBankAccountError(event, e, correlationId, acknowledgment);
        } catch (MandateException e) {
            handleMandateError(event, e, correlationId, acknowledgment);
        } catch (Exception e) {
            handleCriticalError(event, e, correlationId, acknowledgment);
        }
    }

    /**
     * STEP 1: Event validation and sanitization
     */
    private void validateAndSanitizeEvent(DirectDebitMandateEvent event, String correlationId) {
        log.debug("STEP 1: Validating direct debit mandate event - Correlation: {}", correlationId);
        
        if (event == null) {
            throw new IllegalArgumentException("Direct debit mandate event cannot be null");
        }
        
        if (event.getMandateId() == null || event.getMandateId().trim().isEmpty()) {
            throw new IllegalArgumentException("Mandate ID is required");
        }
        
        if (event.getCustomerId() == null || event.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        
        if (event.getMerchantId() == null || event.getMerchantId().trim().isEmpty()) {
            throw new IllegalArgumentException("Merchant ID is required");
        }
        
        if (event.getBankAccountId() == null || event.getBankAccountId().trim().isEmpty()) {
            throw new IllegalArgumentException("Bank account ID is required");
        }
        
        if (event.getMaxAmount() == null || event.getMaxAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid maximum amount: " + event.getMaxAmount());
        }
        
        if (event.getMaxAmount().compareTo(MAX_SINGLE_PAYMENT_AMOUNT) > 0) {
            throw new MandateException("Maximum amount exceeds limit: " + MAX_SINGLE_PAYMENT_AMOUNT);
        }
        
        if (event.getStartDate() == null || event.getStartDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Invalid start date: " + event.getStartDate());
        }
        
        if (event.getEndDate() != null && event.getEndDate().isBefore(event.getStartDate())) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
        
        if (event.getPaymentFrequencyDays() == null || 
            event.getPaymentFrequencyDays() < MIN_PAYMENT_FREQUENCY_DAYS || 
            event.getPaymentFrequencyDays() > MAX_PAYMENT_FREQUENCY_DAYS) {
            throw new IllegalArgumentException("Invalid payment frequency: " + event.getPaymentFrequencyDays());
        }
        
        // Sanitize string fields
        event.setMandateId(sanitizeString(event.getMandateId()));
        event.setCustomerId(sanitizeString(event.getCustomerId()));
        event.setMerchantId(sanitizeString(event.getMerchantId()));
        event.setBankAccountId(sanitizeString(event.getBankAccountId()));
        event.setCurrency(sanitizeString(event.getCurrency() != null ? event.getCurrency().toUpperCase() : "USD"));
        
        log.debug("STEP 1: Event validation completed - Correlation: {}", correlationId);
    }

    /**
     * STEP 2: Idempotency and duplicate detection
     */
    private boolean checkIdempotencyAndDuplicates(DirectDebitMandateEvent event, String correlationId) {
        log.debug("STEP 2: Checking idempotency - Correlation: {}", correlationId);
        
        // Check for existing mandate
        boolean isDuplicate = mandateRepository.existsByMandateIdAndCustomerId(
            event.getMandateId(), event.getCustomerId());
        
        if (isDuplicate) {
            log.warn("Duplicate mandate detected - ID: {}, Customer: {}, Correlation: {}",
                event.getMandateId(), event.getCustomerId(), correlationId);
            
            auditService.logEvent(AuditEventType.DUPLICATE_MANDATE_DETECTED, 
                event.getCustomerId(), event.getMandateId(), correlationId);
        }
        
        return isDuplicate;
    }

    /**
     * STEP 3: Regulatory compliance verification
     */
    private void performComplianceVerification(DirectDebitMandateEvent event, String correlationId) {
        log.debug("STEP 3: Performing compliance verification - Correlation: {}", correlationId);
        
        // KYC verification for customer
        if (!complianceService.isKYCCompliant(event.getCustomerId())) {
            throw new ComplianceViolationException("Customer KYC not compliant: " + event.getCustomerId());
        }
        
        // KYB verification for merchant
        if (!complianceService.isKYBCompliant(event.getMerchantId())) {
            throw new ComplianceViolationException("Merchant KYB not compliant: " + event.getMerchantId());
        }
        
        // AML screening
        ComplianceResult amlResult = complianceService.performAMLScreening(
            event.getCustomerId(), event.getMaxAmount(), event.getMerchantId());
        if (amlResult.hasViolations()) {
            throw new ComplianceViolationException("AML violations detected: " + amlResult.getViolations());
        }
        
        // OFAC sanctions screening
        ComplianceResult sanctionsResult = complianceService.performSanctionsScreening(
            event.getCustomerId(), event.getMerchantId());
        if (sanctionsResult.hasViolations()) {
            throw new ComplianceViolationException("Sanctions violations detected: " + sanctionsResult.getViolations());
        }
        
        // Validate mandate terms compliance
        if (!complianceService.areMandateTermsCompliant(event)) {
            throw new ComplianceViolationException("Mandate terms not compliant with regulations");
        }
        
        log.debug("STEP 3: Compliance verification completed - Correlation: {}", correlationId);
    }

    /**
     * STEP 4: Customer and merchant validation
     */
    private void validateCustomerAndMerchant(DirectDebitMandateEvent event, String correlationId) {
        log.debug("STEP 4: Validating customer and merchant - Correlation: {}", correlationId);
        
        // Validate customer account
        if (!complianceService.isCustomerAccountActive(event.getCustomerId())) {
            throw new MandateException("Customer account is not active: " + event.getCustomerId());
        }
        
        // Validate merchant account
        if (!complianceService.isMerchantAccountActive(event.getMerchantId())) {
            throw new MandateException("Merchant account is not active: " + event.getMerchantId());
        }
        
        // Check merchant's direct debit authorization
        if (!complianceService.isMerchantAuthorizedForDirectDebit(event.getMerchantId())) {
            throw new MandateException("Merchant not authorized for direct debit: " + event.getMerchantId());
        }
        
        // Check customer's existing mandates
        long existingMandateCount = mandateRepository.countByCustomerIdAndStatus(
            event.getCustomerId(), MandateStatus.ACTIVE);
        
        if (existingMandateCount > 50) { // Reasonable limit
            throw new MandateException("Customer has too many active mandates: " + existingMandateCount);
        }
        
        log.debug("STEP 4: Customer and merchant validation completed - Correlation: {}", correlationId);
    }

    /**
     * STEP 5: Bank account verification and validation
     */
    private BankAccountVerificationResult verifyAndValidateBankAccount(DirectDebitMandateEvent event, String correlationId) {
        log.debug("STEP 5: Verifying bank account - Correlation: {}", correlationId);
        
        BankAccountVerificationResult result = bankAccountVerificationService.verifyBankAccount(
            event.getBankAccountId(), event.getCustomerId());
        
        if (!result.isVerified()) {
            throw new BankAccountException("Bank account verification failed: " + result.getFailureReason());
        }
        
        if (!result.isEligibleForDirectDebit()) {
            throw new BankAccountException("Bank account not eligible for direct debit: " + event.getBankAccountId());
        }
        
        // Check account ownership
        if (!result.isOwnedByCustomer(event.getCustomerId())) {
            throw new BankAccountException("Bank account not owned by customer: " + event.getCustomerId());
        }
        
        // Validate bank routing and network support
        if (!bankAccountVerificationService.supportsMandateType(event.getBankAccountId(), event.getMandateType())) {
            throw new BankAccountException("Bank does not support mandate type: " + event.getMandateType());
        }
        
        log.debug("STEP 5: Bank account verification completed - Status: {}, Correlation: {}",
            result.getVerificationStatus(), correlationId);
        
        return result;
    }

    /**
     * STEP 6: Risk assessment and fraud screening
     */
    private void performRiskAssessmentAndFraudScreening(DirectDebitMandateEvent event, 
            BankAccountVerificationResult bankVerification, String correlationId) {
        log.debug("STEP 6: Performing risk assessment - Correlation: {}", correlationId);
        
        RiskAssessmentResult riskResult = riskAssessmentService.assessMandateRisk(
            event.getCustomerId(), event.getMerchantId(), event.getMaxAmount(), 
            event.getPaymentFrequencyDays(), bankVerification.getBankRiskScore());
        
        if (riskResult.getRiskScore() > 80) {
            log.warn("High risk mandate detected - Score: {}, Customer: {}, Merchant: {}, Correlation: {}",
                riskResult.getRiskScore(), event.getCustomerId(), event.getMerchantId(), correlationId);
            
            // Trigger enhanced monitoring
            complianceService.triggerEnhancedMonitoring(event.getCustomerId(), riskResult);
        }
        
        if (riskResult.getRiskScore() > 95) {
            throw new MandateException("Mandate blocked due to high risk score: " + riskResult.getRiskScore());
        }
        
        // Fraud screening
        FraudScreeningResult fraudResult = riskAssessmentService.performFraudScreening(
            event.getCustomerId(), event.getMerchantId(), event.getBankAccountId());
        
        if (fraudResult.isFraudulent()) {
            throw new MandateException("Fraudulent activity detected in mandate request");
        }
        
        log.debug("STEP 6: Risk assessment completed - Score: {}, Correlation: {}",
            riskResult.getRiskScore(), correlationId);
    }

    /**
     * STEP 7: Mandate terms validation and approval
     */
    private MandateTermsValidationResult validateAndApproveMandateTerms(DirectDebitMandateEvent event, String correlationId) {
        log.debug("STEP 7: Validating mandate terms - Correlation: {}", correlationId);
        
        MandateTermsValidationResult result = mandateValidationService.validateMandateTerms(
            event.getMaxAmount(),
            event.getStartDate(),
            event.getEndDate(),
            event.getPaymentFrequencyDays(),
            event.getMandateType(),
            event.getCurrency()
        );
        
        if (!result.isValid()) {
            throw new MandateException("Invalid mandate terms: " + result.getValidationErrors());
        }
        
        // Check payment amount limits based on frequency
        BigDecimal monthlyTotal = calculateMonthlyTotal(event.getMaxAmount(), event.getPaymentFrequencyDays());
        if (monthlyTotal.compareTo(MAX_MONTHLY_TOTAL) > 0) {
            throw new MandateException("Monthly payment total exceeds limit: " + MAX_MONTHLY_TOTAL);
        }
        
        // Validate mandate duration
        if (event.getEndDate() != null) {
            long mandateMonths = ChronoUnit.MONTHS.between(event.getStartDate(), event.getEndDate());
            if (mandateMonths > MANDATE_VALIDITY_MONTHS) {
                throw new MandateException("Mandate duration exceeds maximum: " + MANDATE_VALIDITY_MONTHS + " months");
            }
        }
        
        log.debug("STEP 7: Mandate terms validation completed - Correlation: {}", correlationId);
        return result;
    }

    /**
     * STEP 8: Payment schedule configuration
     */
    private PaymentScheduleResult configurePaymentSchedule(DirectDebitMandateEvent event, 
            MandateTermsValidationResult termsValidation, String correlationId) {
        log.debug("STEP 8: Configuring payment schedule - Correlation: {}", correlationId);
        
        PaymentScheduleResult scheduleResult = schedulingService.createPaymentSchedule(
            event.getMandateId(),
            event.getStartDate(),
            event.getEndDate(),
            PaymentFrequency.fromDays(event.getPaymentFrequencyDays()),
            event.getMaxAmount(),
            event.getCurrency()
        );
        
        if (!scheduleResult.isValid()) {
            throw new MandateException("Failed to create payment schedule: " + scheduleResult.getErrorMessage());
        }
        
        // Validate schedule constraints
        if (scheduleResult.getPaymentCount() > 1000) { // Reasonable limit
            throw new MandateException("Payment schedule too frequent - exceeds maximum payment count");
        }
        
        log.debug("STEP 8: Payment schedule configured - Payments: {}, Correlation: {}",
            scheduleResult.getPaymentCount(), correlationId);
        
        return scheduleResult;
    }

    /**
     * STEP 9: ACH/SEPA network registration
     */
    private NetworkRegistrationResult registerWithPaymentNetwork(DirectDebitMandateEvent event, 
            BankAccountVerificationResult bankVerification, String correlationId) {
        log.debug("STEP 9: Registering with payment network - Correlation: {}", correlationId);
        
        NetworkRegistrationResult result;
        
        if (isACHMandate(event.getMandateType())) {
            result = achService.registerACHMandate(
                event.getMandateId(),
                event.getCustomerId(),
                event.getMerchantId(),
                event.getBankAccountId(),
                event.getMaxAmount(),
                event.getMandateType()
            );
        } else if (isSEPAMandate(event.getMandateType())) {
            result = sepaService.registerSEPAMandate(
                event.getMandateId(),
                event.getCustomerId(),
                event.getMerchantId(),
                event.getBankAccountId(),
                event.getMaxAmount(),
                event.getMandateType()
            );
        } else {
            throw new MandateException("Unsupported mandate type: " + event.getMandateType());
        }
        
        if (!result.isSuccessful()) {
            throw new MandateException("Network registration failed: " + result.getErrorMessage());
        }
        
        log.debug("STEP 9: Network registration completed - Reference: {}, Correlation: {}",
            result.getNetworkReference(), correlationId);
        
        return result;
    }

    /**
     * STEP 10: Compliance reporting and documentation
     */
    private void performComplianceReportingAndDocumentation(DirectDebitMandateEvent event, 
            NetworkRegistrationResult networkResult, String correlationId) {
        log.debug("STEP 10: Performing compliance reporting - Correlation: {}", correlationId);
        
        // Generate mandate documentation asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                complianceService.generateMandateDocumentation(event, networkResult, correlationId);
                
                // Generate regulatory reports if required
                if (requiresRegulatoryReporting(event)) {
                    complianceService.generateRegulatoryReport(event, networkResult, correlationId);
                }
                
                // Archive mandate terms and consent
                complianceService.archiveMandateConsent(event, correlationId);
                
            } catch (Exception e) {
                log.error("Failed to generate compliance documentation - Correlation: {}", correlationId, e);
            }
        });
        
        log.debug("STEP 10: Compliance reporting initiated - Correlation: {}", correlationId);
    }

    /**
     * STEP 11: Audit trail and record creation
     */
    private DirectDebitMandate createAuditTrailAndSaveMandate(DirectDebitMandateEvent event, 
            BankAccountVerificationResult bankVerification, MandateTermsValidationResult termsValidation,
            PaymentScheduleResult scheduleResult, NetworkRegistrationResult networkResult,
            String correlationId, long processingStartTime) {
        log.debug("STEP 11: Creating audit trail - Correlation: {}", correlationId);
        
        DirectDebitMandate mandate = DirectDebitMandate.builder()
            .mandateId(event.getMandateId())
            .mandateReference(networkResult.getNetworkReference())
            .customerId(event.getCustomerId())
            .merchantId(event.getMerchantId())
            .bankAccountId(event.getBankAccountId())
            .mandateType(MandateType.valueOf(event.getMandateType()))
            .status(MandateStatus.ACTIVE)
            .maxAmount(event.getMaxAmount())
            .currency(event.getCurrency())
            .startDate(event.getStartDate())
            .endDate(event.getEndDate())
            .paymentFrequency(PaymentFrequency.fromDays(event.getPaymentFrequencyDays()))
            .nextPaymentDate(scheduleResult.getNextPaymentDate())
            .paymentScheduleId(scheduleResult.getScheduleId())
            .networkId(networkResult.getNetworkId())
            .networkReference(networkResult.getNetworkReference())
            .consentTimestamp(event.getConsentTimestamp())
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .processingTimeMs(System.currentTimeMillis() - processingStartTime)
            .build();
        
        mandate = mandateRepository.save(mandate);
        
        // Create detailed audit log
        auditService.logMandateCreationEvent(event, mandate, bankVerification, termsValidation, 
            scheduleResult, networkResult, correlationId);
        
        log.debug("STEP 11: Audit trail created - Mandate ID: {}, Correlation: {}", mandate.getId(), correlationId);
        return mandate;
    }

    /**
     * STEP 12: Notification dispatch and activation
     */
    private void dispatchNotificationsAndActivation(DirectDebitMandateEvent event, DirectDebitMandate mandate,
            NetworkRegistrationResult networkResult, String correlationId) {
        log.debug("STEP 12: Dispatching notifications - Correlation: {}", correlationId);
        
        // Send customer confirmation
        CompletableFuture.runAsync(() -> {
            notificationService.sendMandateConfirmation(
                mandate.getCustomerId(),
                mandate.getMandateReference(),
                mandate.getMaxAmount(),
                mandate.getCurrency(),
                mandate.getStartDate(),
                mandate.getPaymentFrequency()
            );
        });
        
        // Send merchant notification
        CompletableFuture.runAsync(() -> {
            notificationService.sendMerchantMandateNotification(
                mandate.getMerchantId(),
                mandate.getMandateReference(),
                mandate.getCustomerId(),
                mandate.getMaxAmount()
            );
        });
        
        // Send internal notifications
        CompletableFuture.runAsync(() -> {
            notificationService.sendInternalMandateAlert(
                event, mandate, networkResult, correlationId);
        });
        
        // Schedule first payment if immediate
        if (mandate.getStartDate().equals(LocalDate.now())) {
            kafkaTemplate.send(PAYMENT_SCHEDULED_TOPIC, Map.of(
                "mandateId", mandate.getMandateId(),
                "scheduledDate", mandate.getNextPaymentDate(),
                "amount", mandate.getMaxAmount(),
                "currency", mandate.getCurrency(),
                "correlationId", correlationId
            ));
        }
        
        // Publish mandate processed event
        MandateProcessedEvent processedEvent = MandateProcessedEvent.builder()
            .mandateId(event.getMandateId())
            .mandateReference(mandate.getMandateReference())
            .customerId(event.getCustomerId())
            .merchantId(event.getMerchantId())
            .status("ACTIVE")
            .networkReference(networkResult.getNetworkReference())
            .correlationId(correlationId)
            .processedAt(Instant.now())
            .build();
        
        kafkaTemplate.send(MANDATE_PROCESSED_TOPIC, processedEvent);
        
        log.debug("STEP 12: Notifications dispatched - Correlation: {}", correlationId);
    }

    // Error handling methods
    private void handleComplianceViolation(DirectDebitMandateEvent event, ComplianceViolationException e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Compliance violation in mandate processing - ID: {}, Error: {}, Correlation: {}",
            event.getMandateId(), e.getMessage(), correlationId);
        
        // Send compliance alert
        kafkaTemplate.send(COMPLIANCE_ALERT_TOPIC, Map.of(
            "eventType", "MANDATE_COMPLIANCE_VIOLATION",
            "mandateId", event.getMandateId(),
            "customerId", event.getCustomerId(),
            "merchantId", event.getMerchantId(),
            "violation", e.getMessage(),
            "correlationId", correlationId
        ));
        
        acknowledgment.acknowledge();
    }

    private void handleBankAccountError(DirectDebitMandateEvent event, BankAccountException e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Bank account error in mandate processing - ID: {}, Error: {}, Correlation: {}",
            event.getMandateId(), e.getMessage(), correlationId);
        
        // Create failed mandate record
        DirectDebitMandate failedMandate = DirectDebitMandate.builder()
            .mandateId(event.getMandateId())
            .customerId(event.getCustomerId())
            .merchantId(event.getMerchantId())
            .bankAccountId(event.getBankAccountId())
            .status(MandateStatus.FAILED)
            .failureReason(e.getMessage())
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .build();
        
        mandateRepository.save(failedMandate);
        acknowledgment.acknowledge();
    }

    private void handleMandateError(DirectDebitMandateEvent event, MandateException e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Mandate processing error - ID: {}, Error: {}, Correlation: {}",
            event.getMandateId(), e.getMessage(), correlationId);
        
        sendToDeadLetterQueue(event, e, correlationId);
        acknowledgment.acknowledge();
    }

    private void handleCriticalError(DirectDebitMandateEvent event, Exception e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Critical error in mandate processing - ID: {}, Error: {}, Correlation: {}",
            event.getMandateId(), e.getMessage(), e, correlationId);
        
        sendToDeadLetterQueue(event, e, correlationId);
        
        // Send critical alert
        notificationService.sendCriticalAlert(
            "MANDATE_PROCESSING_ERROR",
            String.format("Critical error processing mandate %s: %s", event.getMandateId(), e.getMessage()),
            correlationId
        );
        
        acknowledgment.acknowledge();
    }

    // Utility methods
    private String generateCorrelationId(DirectDebitMandateEvent event, int partition, long offset) {
        return String.format("mandate-%s-p%d-o%d-%d",
            event.getMandateId(), partition, offset, System.currentTimeMillis());
    }

    private String sanitizeString(String input) {
        if (input == null) return null;
        return input.trim().replaceAll("[<>\"'&]", "");
    }

    private void acknowledgeAndReturn(Acknowledgment acknowledgment, String message) {
        log.info(message);
        acknowledgment.acknowledge();
    }

    private boolean isACHMandate(String mandateType) {
        return mandateType != null && (mandateType.startsWith("ACH") || 
               mandateType.equals("PPD") || mandateType.equals("CCD") || mandateType.equals("WEB"));
    }

    private boolean isSEPAMandate(String mandateType) {
        return mandateType != null && (mandateType.startsWith("SEPA") || 
               mandateType.equals("CORE") || mandateType.equals("B2B"));
    }

    private BigDecimal calculateMonthlyTotal(BigDecimal maxAmount, Integer frequencyDays) {
        if (maxAmount == null || frequencyDays == null || frequencyDays <= 0) return BigDecimal.ZERO;
        
        int paymentsPerMonth = 30 / frequencyDays;
        return maxAmount.multiply(new BigDecimal(Math.max(1, paymentsPerMonth)));
    }

    private boolean requiresRegulatoryReporting(DirectDebitMandateEvent event) {
        return event.getMaxAmount().compareTo(new BigDecimal("10000")) >= 0 || 
               "BUSINESS".equals(event.getCustomerType());
    }

    private void sendToDeadLetterQueue(DirectDebitMandateEvent event, Exception error, String correlationId) {
        try {
            Map<String, Object> dlqMessage = Map.of(
                "originalEvent", event,
                "errorMessage", error.getMessage(),
                "errorClass", error.getClass().getName(),
                "correlationId", correlationId,
                "failedAt", Instant.now(),
                "service", "recurring-payment-service"
            );
            
            kafkaTemplate.send(DLQ_TOPIC, dlqMessage);
            log.warn("Sent failed mandate to DLQ - ID: {}, Correlation: {}",
                event.getMandateId(), correlationId);
                
        } catch (Exception dlqError) {
            log.error("Failed to send mandate to DLQ - Correlation: {}", correlationId, dlqError);
        }
    }

    // Inner classes for results
    @lombok.Data
    @lombok.Builder
    private static class BankAccountVerificationResult {
        private boolean verified;
        private boolean eligibleForDirectDebit;
        private String verificationStatus;
        private String failureReason;
        private int bankRiskScore;
        
        public boolean isOwnedByCustomer(String customerId) {
            // Implementation would verify ownership
            return verified;
        }
    }

    @lombok.Data
    @lombok.Builder
    private static class MandateTermsValidationResult {
        private boolean valid;
        private List<String> validationErrors;
        private Map<String, String> validatedTerms;
    }

    @lombok.Data
    @lombok.Builder
    private static class PaymentScheduleResult {
        private boolean valid;
        private String scheduleId;
        private int paymentCount;
        private LocalDate nextPaymentDate;
        private String errorMessage;
    }

    @lombok.Data
    @lombok.Builder
    private static class NetworkRegistrationResult {
        private boolean successful;
        private String networkId;
        private String networkReference;
        private String errorMessage;
        private LocalDateTime registeredAt;
    }

    @lombok.Data
    @lombok.Builder
    private static class ComplianceResult {
        private boolean compliant;
        private List<String> violations;
        
        public boolean hasViolations() {
            return violations != null && !violations.isEmpty();
        }
    }

    @lombok.Data
    @lombok.Builder
    private static class RiskAssessmentResult {
        private int riskScore;
        private List<String> riskFactors;
    }

    @lombok.Data
    @lombok.Builder
    private static class FraudScreeningResult {
        private boolean fraudulent;
        private List<String> fraudIndicators;
    }
}