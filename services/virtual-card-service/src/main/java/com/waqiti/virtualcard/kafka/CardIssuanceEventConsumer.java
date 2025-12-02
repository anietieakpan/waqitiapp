package com.waqiti.virtualcard.kafka;

import com.waqiti.common.events.CardIssuanceEvent;
import com.waqiti.common.events.CardProvisionedEvent;
import com.waqiti.virtualcard.domain.VirtualCard;
import com.waqiti.virtualcard.domain.CardStatus;
import com.waqiti.virtualcard.domain.CardType;
import com.waqiti.virtualcard.domain.SecurityFeatures;
import com.waqiti.virtualcard.repository.VirtualCardRepository;
import com.waqiti.virtualcard.service.CardProvisioningService;
import com.waqiti.virtualcard.service.CardSecurityService;
import com.waqiti.virtualcard.service.ComplianceService;
import com.waqiti.virtualcard.service.NotificationService;
import com.waqiti.virtualcard.service.AuditService;
import com.waqiti.virtualcard.service.RiskAssessmentService;
import com.waqiti.virtualcard.service.TokenizationService;
import com.waqiti.virtualcard.service.PINGenerationService;
import com.waqiti.virtualcard.service.LimitManagementService;
import com.waqiti.virtualcard.exception.CardIssuanceException;
import com.waqiti.virtualcard.exception.ComplianceViolationException;
import com.waqiti.virtualcard.exception.SecurityException;
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
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * CRITICAL CARD ISSUANCE EVENT CONSUMER - Consumer 39
 * 
 * Processes card issuance events with zero-tolerance 12-step processing:
 * 1. Event validation and sanitization
 * 2. Idempotency and duplicate detection
 * 3. Regulatory compliance verification
 * 4. Customer eligibility validation
 * 5. Risk assessment and fraud screening
 * 6. Card number generation and tokenization
 * 7. PIN generation and encryption
 * 8. Security features activation
 * 9. Limit configuration and validation
 * 10. Card provisioning to wallets
 * 11. Audit trail and compliance logging
 * 12. Notification and activation dispatch
 * 
 * REGULATORY COMPLIANCE:
 * - PCI DSS Level 1 compliance for card data
 * - EMV tokenization standards
 * - 3D Secure 2.0 authentication
 * - Card network regulations (Visa/MasterCard)
 * - Anti-Money Laundering (AML) checks
 * - Know Your Customer (KYC) verification
 * 
 * SECURITY FEATURES:
 * - AES-256 encryption for sensitive data
 * - HSM-based key management
 * - Dynamic CVV generation
 * - Transaction velocity controls
 * - Geolocation-based restrictions
 * 
 * SLA: 99.99% uptime, <3s processing time
 * 
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 1.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
@Validated
public class CardIssuanceEventConsumer {
    
    private final VirtualCardRepository virtualCardRepository;
    private final CardProvisioningService cardProvisioningService;
    private final CardSecurityService cardSecurityService;
    private final ComplianceService complianceService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final RiskAssessmentService riskAssessmentService;
    private final TokenizationService tokenizationService;
    private final PINGenerationService pinGenerationService;
    private final LimitManagementService limitManagementService;
    private final EncryptionService encryptionService;
    private final ComplianceValidator complianceValidator;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final String CARD_PROVISIONED_TOPIC = "card-provisioned-events";
    private static final String COMPLIANCE_ALERT_TOPIC = "compliance-alert-events";
    private static final String SECURITY_ALERT_TOPIC = "security-alert-events";
    private static final String DLQ_TOPIC = "card-issuance-events-dlq";
    
    private static final BigDecimal MAX_CREDIT_LIMIT = new BigDecimal("100000.00");
    private static final BigDecimal MIN_CREDIT_LIMIT = new BigDecimal("100.00");
    private static final BigDecimal MAX_DAILY_LIMIT = new BigDecimal("50000.00");
    private static final int MAX_CARDS_PER_CUSTOMER = 10;
    private static final int CARD_VALIDITY_YEARS = 4;

    @KafkaListener(
        topics = "card-issuance-events",
        groupId = "card-issuance-processor",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "3"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Retryable(
        value = {CardIssuanceException.class, SecurityException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2, maxDelay = 10000)
    )
    public void handleCardIssuanceEvent(
            @Payload @Valid CardIssuanceEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {
        
        String correlationId = generateCorrelationId(event, partition, offset);
        long processingStartTime = System.currentTimeMillis();
        
        log.info("STEP 1: Processing card issuance event - ID: {}, Customer: {}, Type: {}, Correlation: {}",
            event.getCardRequestId(), event.getCustomerId(), event.getCardType(), correlationId);
        
        try {
            // STEP 1: Event validation and sanitization
            validateAndSanitizeEvent(event, correlationId);
            
            // STEP 2: Idempotency and duplicate detection
            if (checkIdempotencyAndDuplicates(event, correlationId)) {
                acknowledgeAndReturn(acknowledgment, "Duplicate card issuance event detected");
                return;
            }
            
            // STEP 3: Regulatory compliance verification
            performComplianceVerification(event, correlationId);
            
            // STEP 4: Customer eligibility validation
            validateCustomerEligibility(event, correlationId);
            
            // STEP 5: Risk assessment and fraud screening
            performRiskAssessmentAndFraudScreening(event, correlationId);
            
            // STEP 6: Card number generation and tokenization
            CardGenerationResult cardGenResult = generateAndTokenizeCard(event, correlationId);
            
            // STEP 7: PIN generation and encryption
            PINGenerationResult pinResult = generateAndEncryptPIN(event, cardGenResult, correlationId);
            
            // STEP 8: Security features activation
            SecurityFeatures securityFeatures = activateSecurityFeatures(event, cardGenResult, correlationId);
            
            // STEP 9: Limit configuration and validation
            LimitConfiguration limitConfig = configureAndValidateLimits(event, correlationId);
            
            // STEP 10: Card provisioning to wallets
            ProvisioningResult provisioningResult = provisionCardToWallets(event, cardGenResult, securityFeatures, correlationId);
            
            // STEP 11: Audit trail and compliance logging
            VirtualCard virtualCard = createAuditTrailAndSaveCard(event, cardGenResult, pinResult, securityFeatures, 
                limitConfig, provisioningResult, correlationId, processingStartTime);
            
            // STEP 12: Notification and activation dispatch
            dispatchNotificationsAndActivation(event, virtualCard, provisioningResult, correlationId);
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            long processingTime = System.currentTimeMillis() - processingStartTime;
            log.info("Successfully processed card issuance - ID: {}, Card: {}, Time: {}ms, Correlation: {}",
                event.getCardRequestId(), virtualCard.getCardNumber(), processingTime, correlationId);
            
        } catch (ComplianceViolationException e) {
            handleComplianceViolation(event, e, correlationId, acknowledgment);
        } catch (SecurityException e) {
            handleSecurityViolation(event, e, correlationId, acknowledgment);
        } catch (CardIssuanceException e) {
            handleCardIssuanceError(event, e, correlationId, acknowledgment);
        } catch (Exception e) {
            handleCriticalError(event, e, correlationId, acknowledgment);
        }
    }

    /**
     * STEP 1: Event validation and sanitization
     */
    private void validateAndSanitizeEvent(CardIssuanceEvent event, String correlationId) {
        log.debug("STEP 1: Validating card issuance event - Correlation: {}", correlationId);
        
        if (event == null) {
            throw new IllegalArgumentException("Card issuance event cannot be null");
        }
        
        if (event.getCardRequestId() == null || event.getCardRequestId().trim().isEmpty()) {
            throw new IllegalArgumentException("Card request ID is required");
        }
        
        if (event.getCustomerId() == null || event.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        
        if (event.getCardType() == null || event.getCardType().trim().isEmpty()) {
            throw new IllegalArgumentException("Card type is required");
        }
        
        if (event.getCreditLimit() != null) {
            if (event.getCreditLimit().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Credit limit must be positive");
            }
            if (event.getCreditLimit().compareTo(MAX_CREDIT_LIMIT) > 0) {
                throw new CardIssuanceException("Credit limit exceeds maximum: " + MAX_CREDIT_LIMIT);
            }
            if (event.getCreditLimit().compareTo(MIN_CREDIT_LIMIT) < 0) {
                throw new CardIssuanceException("Credit limit below minimum: " + MIN_CREDIT_LIMIT);
            }
        }
        
        if (event.getDailyLimit() != null && event.getDailyLimit().compareTo(MAX_DAILY_LIMIT) > 0) {
            throw new CardIssuanceException("Daily limit exceeds maximum: " + MAX_DAILY_LIMIT);
        }
        
        // Sanitize string fields
        event.setCardRequestId(sanitizeString(event.getCardRequestId()));
        event.setCustomerId(sanitizeString(event.getCustomerId()));
        event.setCardType(sanitizeString(event.getCardType().toUpperCase()));
        
        log.debug("STEP 1: Event validation completed - Correlation: {}", correlationId);
    }

    /**
     * STEP 2: Idempotency and duplicate detection
     */
    private boolean checkIdempotencyAndDuplicates(CardIssuanceEvent event, String correlationId) {
        log.debug("STEP 2: Checking idempotency - Correlation: {}", correlationId);
        
        // Check for existing card request
        boolean isDuplicate = virtualCardRepository.existsByCardRequestIdAndCustomerId(
            event.getCardRequestId(), event.getCustomerId());
        
        if (isDuplicate) {
            log.warn("Duplicate card issuance detected - Request: {}, Customer: {}, Correlation: {}",
                event.getCardRequestId(), event.getCustomerId(), correlationId);
            
            auditService.logEvent(AuditEventType.DUPLICATE_CARD_REQUEST_DETECTED, 
                event.getCustomerId(), event.getCardRequestId(), correlationId);
        }
        
        return isDuplicate;
    }

    /**
     * STEP 3: Regulatory compliance verification
     */
    private void performComplianceVerification(CardIssuanceEvent event, String correlationId) {
        log.debug("STEP 3: Performing compliance verification - Correlation: {}", correlationId);
        
        // KYC verification
        if (!complianceService.isKYCCompliant(event.getCustomerId())) {
            throw new ComplianceViolationException("Customer KYC not compliant: " + event.getCustomerId());
        }
        
        // AML screening
        ComplianceResult amlResult = complianceService.performAMLScreening(event.getCustomerId(), event.getCreditLimit());
        if (amlResult.hasViolations()) {
            throw new ComplianceViolationException("AML violations detected: " + amlResult.getViolations());
        }
        
        // OFAC sanctions screening
        ComplianceResult sanctionsResult = complianceService.performSanctionsScreening(event.getCustomerId());
        if (sanctionsResult.hasViolations()) {
            throw new ComplianceViolationException("Sanctions violations detected: " + sanctionsResult.getViolations());
        }
        
        // Card network compliance
        if (!complianceService.isCardNetworkCompliant(event.getCardType())) {
            throw new ComplianceViolationException("Card type not compliant with network rules: " + event.getCardType());
        }
        
        log.debug("STEP 3: Compliance verification completed - Correlation: {}", correlationId);
    }

    /**
     * STEP 4: Customer eligibility validation
     */
    private void validateCustomerEligibility(CardIssuanceEvent event, String correlationId) {
        log.debug("STEP 4: Validating customer eligibility - Correlation: {}", correlationId);
        
        // Check customer account status
        if (!complianceService.isCustomerAccountActive(event.getCustomerId())) {
            throw new CardIssuanceException("Customer account is not active: " + event.getCustomerId());
        }
        
        // Check existing card count
        long existingCardCount = virtualCardRepository.countByCustomerIdAndStatus(
            event.getCustomerId(), CardStatus.ACTIVE);
        
        if (existingCardCount >= MAX_CARDS_PER_CUSTOMER) {
            throw new CardIssuanceException("Customer has reached maximum card limit: " + MAX_CARDS_PER_CUSTOMER);
        }
        
        // Credit assessment for credit cards
        if (isCredtCardType(event.getCardType()) && event.getCreditLimit() != null) {
            CreditAssessmentResult creditResult = complianceService.performCreditAssessment(
                event.getCustomerId(), event.getCreditLimit());
            
            if (!creditResult.isApproved()) {
                throw new CardIssuanceException("Credit assessment failed: " + creditResult.getReason());
            }
        }
        
        log.debug("STEP 4: Customer eligibility validated - Correlation: {}", correlationId);
    }

    /**
     * STEP 5: Risk assessment and fraud screening
     */
    private void performRiskAssessmentAndFraudScreening(CardIssuanceEvent event, String correlationId) {
        log.debug("STEP 5: Performing risk assessment - Correlation: {}", correlationId);
        
        RiskAssessmentResult riskResult = riskAssessmentService.assessCardIssuanceRisk(
            event.getCustomerId(), event.getCardType(), event.getCreditLimit());
        
        if (riskResult.getRiskScore() > 80) {
            log.warn("High risk card issuance detected - Score: {}, Customer: {}, Correlation: {}",
                riskResult.getRiskScore(), event.getCustomerId(), correlationId);
            
            // Trigger enhanced monitoring
            complianceService.triggerEnhancedMonitoring(event.getCustomerId(), riskResult);
        }
        
        if (riskResult.getRiskScore() > 95) {
            throw new CardIssuanceException("Card issuance blocked due to high risk score: " + riskResult.getRiskScore());
        }
        
        // Fraud screening
        FraudScreeningResult fraudResult = riskAssessmentService.performFraudScreening(event.getCustomerId());
        if (fraudResult.isFraudulent()) {
            throw new SecurityException("Fraudulent activity detected for customer: " + event.getCustomerId());
        }
        
        log.debug("STEP 5: Risk assessment completed - Score: {}, Correlation: {}",
            riskResult.getRiskScore(), correlationId);
    }

    /**
     * STEP 6: Card number generation and tokenization
     */
    private CardGenerationResult generateAndTokenizeCard(CardIssuanceEvent event, String correlationId) {
        log.debug("STEP 6: Generating and tokenizing card - Correlation: {}", correlationId);
        
        // Generate card number using Luhn algorithm
        String cardNumber = cardProvisioningService.generateCardNumber(event.getCardType());
        
        // Generate CVV
        String cvv = cardProvisioningService.generateCVV();
        
        // Generate expiry date
        LocalDateTime expiryDate = LocalDateTime.now().plusYears(CARD_VALIDITY_YEARS);
        
        // Tokenize card number
        String cardToken = tokenizationService.tokenizeCardNumber(cardNumber);
        
        // Encrypt sensitive data
        String encryptedCardNumber = encryptionService.encrypt(cardNumber);
        String encryptedCVV = encryptionService.encrypt(cvv);
        
        CardGenerationResult result = CardGenerationResult.builder()
            .cardNumber(cardNumber)
            .encryptedCardNumber(encryptedCardNumber)
            .cvv(cvv)
            .encryptedCVV(encryptedCVV)
            .cardToken(cardToken)
            .expiryDate(expiryDate)
            .build();
        
        log.debug("STEP 6: Card generation completed - Token: {}, Correlation: {}", cardToken, correlationId);
        return result;
    }

    /**
     * STEP 7: PIN generation and encryption
     */
    private PINGenerationResult generateAndEncryptPIN(CardIssuanceEvent event, CardGenerationResult cardGenResult, 
            String correlationId) {
        log.debug("STEP 7: Generating and encrypting PIN - Correlation: {}", correlationId);
        
        String pin = pinGenerationService.generateSecurePIN();
        String encryptedPIN = encryptionService.encrypt(pin);
        String pinHash = cardSecurityService.hashPIN(pin, cardGenResult.getCardNumber());
        
        PINGenerationResult result = PINGenerationResult.builder()
            .pin(pin)
            .encryptedPIN(encryptedPIN)
            .pinHash(pinHash)
            .build();
        
        log.debug("STEP 7: PIN generation completed - Correlation: {}", correlationId);
        return result;
    }

    /**
     * STEP 8: Security features activation
     */
    private SecurityFeatures activateSecurityFeatures(CardIssuanceEvent event, CardGenerationResult cardGenResult, 
            String correlationId) {
        log.debug("STEP 8: Activating security features - Correlation: {}", correlationId);
        
        SecurityFeatures features = SecurityFeatures.builder()
            .threeDSecureEnabled(true)
            .contactlessEnabled(event.isContactlessEnabled())
            .onlineTransactionsEnabled(event.isOnlineTransactionsEnabled())
            .internationalTransactionsEnabled(event.isInternationalTransactionsEnabled())
            .atmWithdrawalsEnabled(event.isATMWithdrawalsEnabled())
            .velocityControlsEnabled(true)
            .geolocationControlsEnabled(true)
            .dynamicCVVEnabled(true)
            .fraudMonitoringEnabled(true)
            .tokenizationEnabled(true)
            .build();
        
        // Generate security tokens
        cardSecurityService.generateSecurityTokens(cardGenResult.getCardToken(), features);
        
        log.debug("STEP 8: Security features activated - Correlation: {}", correlationId);
        return features;
    }

    /**
     * STEP 9: Limit configuration and validation
     */
    private LimitConfiguration configureAndValidateLimits(CardIssuanceEvent event, String correlationId) {
        log.debug("STEP 9: Configuring limits - Correlation: {}", correlationId);
        
        LimitConfiguration config = limitManagementService.createLimitConfiguration(
            event.getCustomerId(),
            event.getCardType(),
            event.getCreditLimit(),
            event.getDailyLimit(),
            event.getMonthlyLimit()
        );
        
        // Validate limits
        limitManagementService.validateLimits(config);
        
        log.debug("STEP 9: Limit configuration completed - Credit: {}, Daily: {}, Correlation: {}",
            config.getCreditLimit(), config.getDailyLimit(), correlationId);
        
        return config;
    }

    /**
     * STEP 10: Card provisioning to wallets
     */
    private ProvisioningResult provisionCardToWallets(CardIssuanceEvent event, CardGenerationResult cardGenResult,
            SecurityFeatures securityFeatures, String correlationId) {
        log.debug("STEP 10: Provisioning card to wallets - Correlation: {}", correlationId);
        
        ProvisioningResult result = cardProvisioningService.provisionToWallets(
            cardGenResult.getCardToken(),
            event.getCustomerId(),
            securityFeatures
        );
        
        if (!result.isSuccessful()) {
            log.warn("Card provisioning partially failed - Correlation: {}", correlationId);
        }
        
        log.debug("STEP 10: Card provisioning completed - Success: {}, Correlation: {}",
            result.isSuccessful(), correlationId);
        
        return result;
    }

    /**
     * STEP 11: Audit trail and compliance logging
     */
    private VirtualCard createAuditTrailAndSaveCard(CardIssuanceEvent event, CardGenerationResult cardGenResult,
            PINGenerationResult pinResult, SecurityFeatures securityFeatures, LimitConfiguration limitConfig,
            ProvisioningResult provisioningResult, String correlationId, long processingStartTime) {
        log.debug("STEP 11: Creating audit trail - Correlation: {}", correlationId);
        
        VirtualCard virtualCard = VirtualCard.builder()
            .cardRequestId(event.getCardRequestId())
            .customerId(event.getCustomerId())
            .cardNumber(cardGenResult.getEncryptedCardNumber())
            .cardToken(cardGenResult.getCardToken())
            .cvv(cardGenResult.getEncryptedCVV())
            .pin(pinResult.getEncryptedPIN())
            .pinHash(pinResult.getPinHash())
            .cardType(CardType.valueOf(event.getCardType()))
            .status(CardStatus.ACTIVE)
            .expiryDate(cardGenResult.getExpiryDate())
            .securityFeatures(securityFeatures)
            .creditLimit(limitConfig.getCreditLimit())
            .dailyLimit(limitConfig.getDailyLimit())
            .monthlyLimit(limitConfig.getMonthlyLimit())
            .currentBalance(BigDecimal.ZERO)
            .availableCredit(limitConfig.getCreditLimit())
            .correlationId(correlationId)
            .issuedAt(LocalDateTime.now())
            .processingTimeMs(System.currentTimeMillis() - processingStartTime)
            .build();
        
        virtualCard = virtualCardRepository.save(virtualCard);
        
        // Create detailed audit log
        auditService.logCardIssuanceEvent(event, virtualCard, limitConfig, provisioningResult, correlationId);
        
        // Generate compliance reports asynchronously
        CompletableFuture.runAsync(() -> {
            complianceService.generateCardIssuanceReport(event, virtualCard, correlationId);
        });
        
        log.debug("STEP 11: Audit trail created - Card ID: {}, Correlation: {}", virtualCard.getId(), correlationId);
        return virtualCard;
    }

    /**
     * STEP 12: Notification and activation dispatch
     */
    private void dispatchNotificationsAndActivation(CardIssuanceEvent event, VirtualCard virtualCard,
            ProvisioningResult provisioningResult, String correlationId) {
        log.debug("STEP 12: Dispatching notifications - Correlation: {}", correlationId);
        
        // Send customer notification
        CompletableFuture.runAsync(() -> {
            notificationService.sendCardIssuanceNotification(
                virtualCard.getCustomerId(),
                virtualCard.getCardToken(),
                virtualCard.getCardType().toString(),
                virtualCard.getExpiryDate()
            );
        });
        
        // Send activation instructions
        CompletableFuture.runAsync(() -> {
            notificationService.sendCardActivationInstructions(
                virtualCard.getCustomerId(),
                virtualCard.getCardToken()
            );
        });
        
        // Send internal notifications
        CompletableFuture.runAsync(() -> {
            notificationService.sendInternalCardIssuanceAlert(
                event, virtualCard, provisioningResult, correlationId);
        });
        
        // Publish card provisioned event
        CardProvisionedEvent provisionedEvent = CardProvisionedEvent.builder()
            .cardRequestId(event.getCardRequestId())
            .customerId(event.getCustomerId())
            .cardToken(virtualCard.getCardToken())
            .cardType(virtualCard.getCardType().toString())
            .status("ACTIVE")
            .issuedAt(virtualCard.getIssuedAt())
            .correlationId(correlationId)
            .build();
        
        kafkaTemplate.send(CARD_PROVISIONED_TOPIC, provisionedEvent);
        
        log.debug("STEP 12: Notifications dispatched - Correlation: {}", correlationId);
    }

    // Error handling methods
    private void handleComplianceViolation(CardIssuanceEvent event, ComplianceViolationException e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Compliance violation in card issuance - Request: {}, Error: {}, Correlation: {}",
            event.getCardRequestId(), e.getMessage(), correlationId);
        
        // Send compliance alert
        kafkaTemplate.send(COMPLIANCE_ALERT_TOPIC, Map.of(
            "eventType", "CARD_ISSUANCE_COMPLIANCE_VIOLATION",
            "cardRequestId", event.getCardRequestId(),
            "customerId", event.getCustomerId(),
            "violation", e.getMessage(),
            "correlationId", correlationId
        ));
        
        acknowledgment.acknowledge();
    }

    private void handleSecurityViolation(CardIssuanceEvent event, SecurityException e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Security violation in card issuance - Request: {}, Error: {}, Correlation: {}",
            event.getCardRequestId(), e.getMessage(), correlationId);
        
        // Send security alert
        kafkaTemplate.send(SECURITY_ALERT_TOPIC, Map.of(
            "eventType", "CARD_ISSUANCE_SECURITY_VIOLATION",
            "cardRequestId", event.getCardRequestId(),
            "customerId", event.getCustomerId(),
            "violation", e.getMessage(),
            "correlationId", correlationId
        ));
        
        acknowledgment.acknowledge();
    }

    private void handleCardIssuanceError(CardIssuanceEvent event, CardIssuanceException e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Card issuance error - Request: {}, Error: {}, Correlation: {}",
            event.getCardRequestId(), e.getMessage(), correlationId);
        
        sendToDeadLetterQueue(event, e, correlationId);
        acknowledgment.acknowledge();
    }

    private void handleCriticalError(CardIssuanceEvent event, Exception e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Critical error in card issuance - Request: {}, Error: {}, Correlation: {}",
            event.getCardRequestId(), e.getMessage(), e, correlationId);
        
        sendToDeadLetterQueue(event, e, correlationId);
        
        // Send critical alert
        notificationService.sendCriticalAlert(
            "CARD_ISSUANCE_PROCESSING_ERROR",
            String.format("Critical error processing card issuance %s: %s", event.getCardRequestId(), e.getMessage()),
            correlationId
        );
        
        acknowledgment.acknowledge();
    }

    // Utility methods
    private String generateCorrelationId(CardIssuanceEvent event, int partition, long offset) {
        return String.format("card-issuance-%s-p%d-o%d-%d",
            event.getCardRequestId(), partition, offset, System.currentTimeMillis());
    }

    private String sanitizeString(String input) {
        if (input == null) return null;
        return input.trim().replaceAll("[<>\"'&]", "");
    }

    private void acknowledgeAndReturn(Acknowledgment acknowledgment, String message) {
        log.info(message);
        acknowledgment.acknowledge();
    }

    private boolean isCredtCardType(String cardType) {
        return "CREDIT".equalsIgnoreCase(cardType) || "CREDIT_CARD".equalsIgnoreCase(cardType);
    }

    private void sendToDeadLetterQueue(CardIssuanceEvent event, Exception error, String correlationId) {
        try {
            Map<String, Object> dlqMessage = Map.of(
                "originalEvent", event,
                "errorMessage", error.getMessage(),
                "errorClass", error.getClass().getName(),
                "correlationId", correlationId,
                "failedAt", Instant.now(),
                "service", "virtual-card-service"
            );
            
            kafkaTemplate.send(DLQ_TOPIC, dlqMessage);
            log.warn("Sent failed card issuance to DLQ - Request: {}, Correlation: {}",
                event.getCardRequestId(), correlationId);
                
        } catch (Exception dlqError) {
            log.error("Failed to send card issuance to DLQ - Correlation: {}", correlationId, dlqError);
        }
    }

    // Inner classes for results
    @lombok.Data
    @lombok.Builder
    private static class CardGenerationResult {
        private String cardNumber;
        private String encryptedCardNumber;
        private String cvv;
        private String encryptedCVV;
        private String cardToken;
        private LocalDateTime expiryDate;
    }

    @lombok.Data
    @lombok.Builder
    private static class PINGenerationResult {
        private String pin;
        private String encryptedPIN;
        private String pinHash;
    }

    @lombok.Data
    @lombok.Builder
    private static class LimitConfiguration {
        private BigDecimal creditLimit;
        private BigDecimal dailyLimit;
        private BigDecimal monthlyLimit;
        private BigDecimal atmLimit;
        private BigDecimal onlineLimit;
    }

    @lombok.Data
    @lombok.Builder
    private static class ProvisioningResult {
        private boolean successful;
        private List<String> provisionedWallets;
        private List<String> failedWallets;
        private String errorMessage;
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

    @lombok.Data
    @lombok.Builder
    private static class CreditAssessmentResult {
        private boolean approved;
        private String reason;
        private BigDecimal approvedLimit;
    }
}