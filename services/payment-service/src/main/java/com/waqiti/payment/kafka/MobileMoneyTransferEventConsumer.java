package com.waqiti.payment.kafka;

import com.waqiti.common.audit.AuditEvent;
import com.waqiti.common.audit.AuditEventType;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.cache.CacheService;
import com.waqiti.common.compliance.ComplianceService;
import com.waqiti.common.compliance.dto.ComplianceCheckRequest;
import com.waqiti.common.compliance.dto.ComplianceCheckResult;
import com.waqiti.common.events.DomainEvent;
import com.waqiti.common.exception.ComplianceViolationException;
import com.waqiti.common.exception.FraudDetectedException;
import com.waqiti.common.exception.InsufficientFundsException;
import com.waqiti.common.exception.InvalidTransactionException;
import com.waqiti.common.fraud.FraudDetectionService;
import com.waqiti.common.fraud.dto.FraudCheckRequest;
import com.waqiti.common.fraud.dto.FraudCheckResult;
import com.waqiti.common.messaging.EventPublisher;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.notification.dto.NotificationRequest;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.validation.ValidationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Consumer 47: MobileMoneyTransferEventConsumer
 * 
 * Handles mobile payment processing events with comprehensive regulatory compliance
 * for mobile money transfer operations including mobile wallets, cross-border mobile payments,
 * USSD transactions, mobile banking, and agent network transactions.
 * 
 * Regulatory Framework:
 * - Mobile Financial Services regulations
 * - Cross-border payment compliance (SWIFT, correspondent banking)
 * - AML/CTF for mobile transactions
 * - Consumer protection for mobile payments
 * - Data privacy and security (GDPR, CCPA)
 * - Central bank mobile payment guidelines
 * 
 * Supported Mobile Payment Types:
 * - Mobile wallet transfers (M-Pesa, MTN Mobile Money, Airtel Money)
 * - Cross-border mobile remittances
 * - USSD-based transactions
 * - Mobile banking transfers
 * - Agent-assisted transactions
 * - QR code mobile payments
 * - NFC mobile payments
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MobileMoneyTransferEventConsumer {

    // Core Services
    private final AuditService auditService;
    private final CacheService cacheService;
    private final ComplianceService complianceService;
    private final FraudDetectionService fraudDetectionService;
    private final ValidationService validationService;
    private final NotificationService notificationService;
    private final EventPublisher eventPublisher;
    private final SecurityContext securityContext;
    private final ObjectMapper objectMapper;
    
    // Mobile Money Specific Services
    private final MobileMoneyService mobileMoneyService;
    private final MobileWalletService mobileWalletService;
    private final CrossBorderMobilePaymentService crossBorderMobilePaymentService;
    private final USSDTransactionService ussdTransactionService;
    private final AgentNetworkService agentNetworkService;
    private final MobilePaymentFraudService mobilePaymentFraudService;
    private final MobilePaymentComplianceService mobilePaymentComplianceService;
    private final CurrencyConversionService currencyConversionService;
    private final MobilePaymentLimitService mobilePaymentLimitService;
    private final MobilePaymentFeeService mobilePaymentFeeService;

    private static final String CONSUMER_GROUP = "mobile-money-transfer-consumer-group";
    private static final String DLQ_TOPIC = "mobile-money-transfer-dlq";
    private static final String IDEMPOTENCY_KEY_PREFIX = "mobile-money-transfer:";
    
    @KafkaListener(
        topics = "mobile-money-transfer-events",
        groupId = CONSUMER_GROUP,
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void processMobileMoneyTransferEvent(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_MESSAGE_KEY, required = false) String messageKey,
            @Header(value = "idempotencyKey", required = false) String idempotencyKey,
            Acknowledgment acknowledgment) {

        String transferId = null;
        String transactionId = UUID.randomUUID().toString();
        LocalDateTime startTime = LocalDateTime.now();

        try {
            log.info("Starting mobile money transfer processing - Transaction: {}, Topic: {}, Partition: {}, Offset: {}", 
                    transactionId, topic, partition, offset);

            // Parse the event payload
            JsonNode eventNode = objectMapper.readTree(eventPayload);
            transferId = eventNode.path("transferId").asText();
            
            // Step 1: Idempotency Check
            performIdempotencyCheck(transferId, idempotencyKey, transactionId);
            
            // Step 2: Event Validation and Parsing  
            MobileMoneyTransferRequest transferRequest = parseAndValidateEvent(eventNode, transactionId);
            
            // Step 3: Security and Authentication Validation
            performSecurityValidation(transferRequest, transactionId);
            
            // Step 4: Business Logic Validation
            performBusinessValidation(transferRequest, transactionId);
            
            // Step 5: Regulatory Compliance Checks
            performComplianceValidation(transferRequest, transactionId);
            
            // Step 6: Fraud Detection and Risk Assessment
            performFraudDetection(transferRequest, transactionId);
            
            // Step 7: Mobile Money Transfer Processing
            MobileMoneyTransferResult transferResult = processMobileMoneyTransfer(transferRequest, transactionId);
            
            // Step 8: Cross-border Compliance (if applicable)
            if (transferRequest.isCrossBorder()) {
                performCrossBorderCompliance(transferRequest, transferResult, transactionId);
            }
            
            // Step 9: Fee Calculation and Deduction
            BigDecimal totalFees = calculateAndDeductFees(transferRequest, transferResult, transactionId);
            
            // Step 10: Transaction Finalization and Settlement
            finalizeTransactionAndSettlement(transferRequest, transferResult, totalFees, transactionId);
            
            // Step 11: Post-Processing Activities
            performPostProcessingActivities(transferRequest, transferResult, transactionId);
            
            // Step 12: Notification Dispatch and Audit Completion
            dispatchNotificationsAndCompleteAudit(transferRequest, transferResult, transactionId, startTime);
            
            // Mark as processed in cache
            cacheService.put(IDEMPOTENCY_KEY_PREFIX + transferId, "PROCESSED", 3600);
            
            // Acknowledge the message
            acknowledgment.acknowledge();
            
            log.info("Successfully completed mobile money transfer processing - Transfer: {}, Transaction: {}, Duration: {}ms", 
                    transferId, transactionId, 
                    java.time.Duration.between(startTime, LocalDateTime.now()).toMillis());
            
        } catch (Exception e) {
            log.error("Failed to process mobile money transfer - Transfer: {}, Transaction: {}, Error: {}", 
                    transferId, transactionId, e.getMessage(), e);
            
            handleProcessingFailure(transferId, transactionId, eventPayload, e, startTime);
            throw new RuntimeException("Mobile money transfer processing failed", e);
        }
    }
    
    /**
     * Step 1: Idempotency Check
     * Ensures the mobile money transfer event is not processed multiple times
     */
    private void performIdempotencyCheck(String transferId, String idempotencyKey, String transactionId) {
        log.debug("Step 1: Performing idempotency check - Transfer: {}, Transaction: {}", transferId, transactionId);
        
        String cacheKey = IDEMPOTENCY_KEY_PREFIX + transferId;
        String existingStatus = cacheService.get(cacheKey, String.class);
        
        if (existingStatus != null) {
            if ("PROCESSED".equals(existingStatus)) {
                throw new InvalidTransactionException("Mobile money transfer already processed: " + transferId);
            } else if ("PROCESSING".equals(existingStatus)) {
                throw new InvalidTransactionException("Mobile money transfer currently being processed: " + transferId);
            }
        }
        
        // Mark as processing
        cacheService.put(cacheKey, "PROCESSING", 1800); // 30 minutes
        
        // Additional idempotency key validation
        if (idempotencyKey != null) {
            String idempotencyCacheKey = "idempotency:" + idempotencyKey;
            if (cacheService.exists(idempotencyCacheKey)) {
                throw new InvalidTransactionException("Duplicate idempotency key detected: " + idempotencyKey);
            }
            cacheService.put(idempotencyCacheKey, transferId, 3600);
        }
        
        auditService.createAuditEvent(AuditEvent.builder()
                .eventType(AuditEventType.MOBILE_MONEY_TRANSFER_IDEMPOTENCY_CHECK)
                .entityId(transferId)
                .transactionId(transactionId)
                .details("Idempotency check completed successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }
    
    /**
     * Step 2: Event Validation and Parsing
     * Validates the mobile money transfer event structure and required fields
     */
    private MobileMoneyTransferRequest parseAndValidateEvent(JsonNode eventNode, String transactionId) {
        log.debug("Step 2: Parsing and validating mobile money transfer event - Transaction: {}", transactionId);
        
        try {
            // Extract required fields
            String transferId = eventNode.path("transferId").asText();
            String senderId = eventNode.path("senderId").asText();
            String receiverId = eventNode.path("receiverId").asText();
            String senderMobileNumber = eventNode.path("senderMobileNumber").asText();
            String receiverMobileNumber = eventNode.path("receiverMobileNumber").asText();
            String mobileMoneyProvider = eventNode.path("mobileMoneyProvider").asText();
            String transferType = eventNode.path("transferType").asText();
            BigDecimal amount = new BigDecimal(eventNode.path("amount").asText());
            String currency = eventNode.path("currency").asText();
            String purpose = eventNode.path("purpose").asText();
            
            // Validate required fields
            if (transferId == null || transferId.isEmpty()) {
                throw new InvalidTransactionException("Transfer ID is required");
            }
            if (senderId == null || senderId.isEmpty()) {
                throw new InvalidTransactionException("Sender ID is required");
            }
            if (receiverId == null || receiverId.isEmpty()) {
                throw new InvalidTransactionException("Receiver ID is required");
            }
            if (senderMobileNumber == null || senderMobileNumber.isEmpty()) {
                throw new InvalidTransactionException("Sender mobile number is required");
            }
            if (receiverMobileNumber == null || receiverMobileNumber.isEmpty()) {
                throw new InvalidTransactionException("Receiver mobile number is required");
            }
            if (mobileMoneyProvider == null || mobileMoneyProvider.isEmpty()) {
                throw new InvalidTransactionException("Mobile money provider is required");
            }
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidTransactionException("Amount must be greater than zero");
            }
            
            // Build transfer request
            MobileMoneyTransferRequest transferRequest = MobileMoneyTransferRequest.builder()
                    .transferId(transferId)
                    .senderId(senderId)
                    .receiverId(receiverId)
                    .senderMobileNumber(senderMobileNumber)
                    .receiverMobileNumber(receiverMobileNumber)
                    .mobileMoneyProvider(mobileMoneyProvider)
                    .transferType(transferType)
                    .amount(amount)
                    .currency(currency)
                    .purpose(purpose)
                    .senderCountryCode(eventNode.path("senderCountryCode").asText())
                    .receiverCountryCode(eventNode.path("receiverCountryCode").asText())
                    .ussdCode(eventNode.path("ussdCode").asText())
                    .agentCode(eventNode.path("agentCode").asText())
                    .reference(eventNode.path("reference").asText())
                    .metadata(extractMetadata(eventNode.path("metadata")))
                    .crossBorder(determineCrossBorderStatus(eventNode))
                    .requestedAt(LocalDateTime.now())
                    .build();
            
            // Validate the complete request
            validationService.validate(transferRequest);
            
            auditService.createAuditEvent(AuditEvent.builder()
                    .eventType(AuditEventType.MOBILE_MONEY_TRANSFER_VALIDATION)
                    .entityId(transferId)
                    .transactionId(transactionId)
                    .details("Event validation completed successfully")
                    .timestamp(LocalDateTime.now())
                    .build());
            
            return transferRequest;
            
        } catch (Exception e) {
            log.error("Failed to parse mobile money transfer event - Transaction: {}, Error: {}", transactionId, e.getMessage());
            throw new InvalidTransactionException("Invalid mobile money transfer event format", e);
        }
    }
    
    /**
     * Step 3: Security and Authentication Validation
     * Validates security context and authentication for mobile money transfers
     */
    private void performSecurityValidation(MobileMoneyTransferRequest request, String transactionId) {
        log.debug("Step 3: Performing security validation - Transfer: {}, Transaction: {}", 
                request.getTransferId(), transactionId);
        
        try {
            // Validate mobile number formats and authenticity
            mobileMoneyService.validateMobileNumber(request.getSenderMobileNumber(), request.getSenderCountryCode());
            mobileMoneyService.validateMobileNumber(request.getReceiverMobileNumber(), request.getReceiverCountryCode());
            
            // Validate mobile money provider credentials
            mobileWalletService.validateProviderCredentials(request.getMobileMoneyProvider(), request.getSenderId());
            
            // Validate USSD session if applicable
            if (request.getUssdCode() != null && !request.getUssdCode().isEmpty()) {
                ussdTransactionService.validateUSSDSession(request.getUssdCode(), request.getSenderMobileNumber());
            }
            
            // Validate agent credentials if applicable
            if (request.getAgentCode() != null && !request.getAgentCode().isEmpty()) {
                agentNetworkService.validateAgent(request.getAgentCode(), request.getMobileMoneyProvider());
            }
            
            // Additional security checks for high-value transactions
            if (request.getAmount().compareTo(new BigDecimal("1000")) > 0) {
                mobileMoneyService.performAdditionalSecurityChecks(request);
            }
            
            auditService.createAuditEvent(AuditEvent.builder()
                    .eventType(AuditEventType.MOBILE_MONEY_TRANSFER_SECURITY_VALIDATION)
                    .entityId(request.getTransferId())
                    .transactionId(transactionId)
                    .details("Security validation completed successfully")
                    .timestamp(LocalDateTime.now())
                    .build());
                    
        } catch (Exception e) {
            log.error("Security validation failed - Transfer: {}, Transaction: {}, Error: {}", 
                    request.getTransferId(), transactionId, e.getMessage());
            throw new InvalidTransactionException("Security validation failed for mobile money transfer", e);
        }
    }
    
    /**
     * Step 4: Business Logic Validation
     * Validates business rules specific to mobile money transfers
     */
    private void performBusinessValidation(MobileMoneyTransferRequest request, String transactionId) {
        log.debug("Step 4: Performing business validation - Transfer: {}, Transaction: {}", 
                request.getTransferId(), transactionId);
        
        try {
            // Validate mobile money provider limits
            mobilePaymentLimitService.validateTransactionLimits(request);
            
            // Validate wallet balances
            mobileWalletService.validateSenderBalance(request.getSenderId(), request.getAmount(), request.getCurrency());
            
            // Validate receiver wallet status
            mobileWalletService.validateReceiverWallet(request.getReceiverId(), request.getCurrency());
            
            // Validate transfer type and provider compatibility
            mobileMoneyService.validateTransferTypeCompatibility(request.getTransferType(), request.getMobileMoneyProvider());
            
            // Validate business hours for the mobile money provider
            mobileMoneyService.validateBusinessHours(request.getMobileMoneyProvider(), request.getSenderCountryCode());
            
            // Validate currency support
            if (request.isCrossBorder()) {
                currencyConversionService.validateCurrencySupport(request.getCurrency(), 
                        request.getSenderCountryCode(), request.getReceiverCountryCode());
            }
            
            auditService.createAuditEvent(AuditEvent.builder()
                    .eventType(AuditEventType.MOBILE_MONEY_TRANSFER_BUSINESS_VALIDATION)
                    .entityId(request.getTransferId())
                    .transactionId(transactionId)
                    .details("Business validation completed successfully")
                    .timestamp(LocalDateTime.now())
                    .build());
                    
        } catch (InsufficientFundsException e) {
            log.error("Insufficient funds - Transfer: {}, Transaction: {}", request.getTransferId(), transactionId);
            throw e;
        } catch (Exception e) {
            log.error("Business validation failed - Transfer: {}, Transaction: {}, Error: {}", 
                    request.getTransferId(), transactionId, e.getMessage());
            throw new InvalidTransactionException("Business validation failed for mobile money transfer", e);
        }
    }
    
    /**
     * Step 5: Regulatory Compliance Checks
     * Performs comprehensive compliance validation for mobile money transfers
     */
    private void performComplianceValidation(MobileMoneyTransferRequest request, String transactionId) {
        log.debug("Step 5: Performing compliance validation - Transfer: {}, Transaction: {}", 
                request.getTransferId(), transactionId);
        
        try {
            // Build compliance check request
            ComplianceCheckRequest complianceRequest = ComplianceCheckRequest.builder()
                    .transactionId(request.getTransferId())
                    .transactionType("MOBILE_MONEY_TRANSFER")
                    .senderId(request.getSenderId())
                    .receiverId(request.getReceiverId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .senderCountry(request.getSenderCountryCode())
                    .receiverCountry(request.getReceiverCountryCode())
                    .purpose(request.getPurpose())
                    .crossBorder(request.isCrossBorder())
                    .build();
            
            // Perform comprehensive compliance checks
            ComplianceCheckResult complianceResult = complianceService.performComplianceCheck(complianceRequest);
            
            if (!complianceResult.isCompliant()) {
                throw new ComplianceViolationException("Mobile money transfer failed compliance checks: " + 
                        complianceResult.getViolations());
            }
            
            // Mobile Money specific compliance checks
            mobilePaymentComplianceService.performMobileSpecificCompliance(request);
            
            // Additional checks for cross-border transfers
            if (request.isCrossBorder()) {
                mobilePaymentComplianceService.performCrossBorderCompliance(request);
            }
            
            auditService.createAuditEvent(AuditEvent.builder()
                    .eventType(AuditEventType.MOBILE_MONEY_TRANSFER_COMPLIANCE_CHECK)
                    .entityId(request.getTransferId())
                    .transactionId(transactionId)
                    .details("Compliance validation completed - Status: " + complianceResult.getStatus())
                    .timestamp(LocalDateTime.now())
                    .build());
                    
        } catch (ComplianceViolationException e) {
            log.error("Compliance violation - Transfer: {}, Transaction: {}, Violations: {}", 
                    request.getTransferId(), transactionId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Compliance validation failed - Transfer: {}, Transaction: {}, Error: {}", 
                    request.getTransferId(), transactionId, e.getMessage());
            throw new ComplianceViolationException("Compliance validation failed for mobile money transfer", e);
        }
    }
    
    /**
     * Step 6: Fraud Detection and Risk Assessment
     * Performs comprehensive fraud detection for mobile money transfers
     */
    private void performFraudDetection(MobileMoneyTransferRequest request, String transactionId) {
        log.debug("Step 6: Performing fraud detection - Transfer: {}, Transaction: {}", 
                request.getTransferId(), transactionId);
        
        try {
            // Build fraud check request
            FraudCheckRequest fraudRequest = FraudCheckRequest.builder()
                    .transactionId(request.getTransferId())
                    .transactionType("MOBILE_MONEY_TRANSFER")
                    .senderId(request.getSenderId())
                    .receiverId(request.getReceiverId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .senderMobileNumber(request.getSenderMobileNumber())
                    .receiverMobileNumber(request.getReceiverMobileNumber())
                    .mobileProvider(request.getMobileMoneyProvider())
                    .transferType(request.getTransferType())
                    .crossBorder(request.isCrossBorder())
                    .build();
            
            // Perform ML-based fraud detection
            FraudCheckResult fraudResult = fraudDetectionService.checkForFraud(fraudRequest);
            
            // Perform mobile-specific fraud checks
            mobilePaymentFraudService.performMobileFraudChecks(request);
            
            // High-risk score handling
            if (fraudResult.getRiskScore() > 0.8) {
                log.warn("High fraud risk detected - Transfer: {}, Risk Score: {}", 
                        request.getTransferId(), fraudResult.getRiskScore());
                        
                if (fraudResult.getRiskScore() > 0.95) {
                    throw new FraudDetectedException("Transaction blocked due to high fraud risk: " + 
                            fraudResult.getRiskScore());
                }
                
                // Additional verification for high-risk transactions
                mobilePaymentFraudService.requireAdditionalVerification(request);
            }
            
            auditService.createAuditEvent(AuditEvent.builder()
                    .eventType(AuditEventType.MOBILE_MONEY_TRANSFER_FRAUD_CHECK)
                    .entityId(request.getTransferId())
                    .transactionId(transactionId)
                    .details("Fraud detection completed - Risk Score: " + fraudResult.getRiskScore())
                    .timestamp(LocalDateTime.now())
                    .build());
                    
        } catch (FraudDetectedException e) {
            log.error("Fraud detected - Transfer: {}, Transaction: {}, Details: {}", 
                    request.getTransferId(), transactionId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Fraud detection failed - Transfer: {}, Transaction: {}, Error: {}", 
                    request.getTransferId(), transactionId, e.getMessage());
            throw new FraudDetectedException("Fraud detection failed for mobile money transfer", e);
        }
    }
    
    /**
     * Step 7: Mobile Money Transfer Processing
     * Processes the actual mobile money transfer
     */
    private MobileMoneyTransferResult processMobileMoneyTransfer(MobileMoneyTransferRequest request, String transactionId) {
        log.debug("Step 7: Processing mobile money transfer - Transfer: {}, Transaction: {}", 
                request.getTransferId(), transactionId);
        
        try {
            // Initiate the transfer based on type
            MobileMoneyTransferResult result;
            
            switch (request.getTransferType().toUpperCase()) {
                case "WALLET_TO_WALLET":
                    result = mobileWalletService.processWalletToWalletTransfer(request);
                    break;
                case "USSD_TRANSFER":
                    result = ussdTransactionService.processUSSDTransfer(request);
                    break;
                case "AGENT_ASSISTED":
                    result = agentNetworkService.processAgentAssistedTransfer(request);
                    break;
                case "CROSS_BORDER":
                    result = crossBorderMobilePaymentService.processCrossBorderTransfer(request);
                    break;
                default:
                    result = mobileMoneyService.processGenericMobileTransfer(request);
                    break;
            }
            
            // Validate transfer result
            if (result == null || result.getStatus() == null) {
                throw new InvalidTransactionException("Invalid transfer result from mobile money service");
            }
            
            // Handle transfer result status
            if (!"SUCCESS".equals(result.getStatus()) && !"PENDING".equals(result.getStatus())) {
                throw new InvalidTransactionException("Mobile money transfer failed: " + result.getFailureReason());
            }
            
            auditService.createAuditEvent(AuditEvent.builder()
                    .eventType(AuditEventType.MOBILE_MONEY_TRANSFER_PROCESSING)
                    .entityId(request.getTransferId())
                    .transactionId(transactionId)
                    .details("Mobile money transfer processed - Status: " + result.getStatus())
                    .timestamp(LocalDateTime.now())
                    .build());
            
            return result;
            
        } catch (Exception e) {
            log.error("Mobile money transfer processing failed - Transfer: {}, Transaction: {}, Error: {}", 
                    request.getTransferId(), transactionId, e.getMessage());
            throw new InvalidTransactionException("Mobile money transfer processing failed", e);
        }
    }
    
    /**
     * Step 8: Cross-border Compliance (if applicable)
     * Performs additional compliance checks for cross-border mobile transfers
     */
    private void performCrossBorderCompliance(MobileMoneyTransferRequest request, MobileMoneyTransferResult result, String transactionId) {
        log.debug("Step 8: Performing cross-border compliance - Transfer: {}, Transaction: {}", 
                request.getTransferId(), transactionId);
        
        try {
            // Perform cross-border regulatory reporting
            crossBorderMobilePaymentService.performRegulatoryReporting(request, result);
            
            // Update cross-border transaction limits
            mobilePaymentLimitService.updateCrossBorderLimits(request.getSenderId(), request.getAmount());
            
            // Perform currency conversion compliance
            if (!request.getCurrency().equals(result.getSettlementCurrency())) {
                currencyConversionService.performConversionCompliance(request, result);
            }
            
            auditService.createAuditEvent(AuditEvent.builder()
                    .eventType(AuditEventType.MOBILE_MONEY_CROSS_BORDER_COMPLIANCE)
                    .entityId(request.getTransferId())
                    .transactionId(transactionId)
                    .details("Cross-border compliance completed successfully")
                    .timestamp(LocalDateTime.now())
                    .build());
                    
        } catch (Exception e) {
            log.error("Cross-border compliance failed - Transfer: {}, Transaction: {}, Error: {}", 
                    request.getTransferId(), transactionId, e.getMessage());
            throw new ComplianceViolationException("Cross-border compliance failed", e);
        }
    }
    
    /**
     * Step 9: Fee Calculation and Deduction
     * Calculates and deducts applicable fees for mobile money transfers
     */
    private BigDecimal calculateAndDeductFees(MobileMoneyTransferRequest request, MobileMoneyTransferResult result, String transactionId) {
        log.debug("Step 9: Calculating and deducting fees - Transfer: {}, Transaction: {}", 
                request.getTransferId(), transactionId);
        
        try {
            // Calculate mobile money transfer fees
            BigDecimal transferFee = mobilePaymentFeeService.calculateTransferFee(request);
            BigDecimal networkFee = mobilePaymentFeeService.calculateNetworkFee(request);
            BigDecimal regulatoryFee = mobilePaymentFeeService.calculateRegulatoryFee(request);
            BigDecimal crossBorderFee = BigDecimal.ZERO;
            
            if (request.isCrossBorder()) {
                crossBorderFee = mobilePaymentFeeService.calculateCrossBorderFee(request);
            }
            
            BigDecimal totalFees = transferFee.add(networkFee).add(regulatoryFee).add(crossBorderFee);
            
            // Deduct fees from sender's wallet
            mobileWalletService.deductFees(request.getSenderId(), totalFees, request.getCurrency());
            
            // Update result with fee information
            result.setTotalFees(totalFees);
            result.setFeeBreakdown(Map.of(
                    "transferFee", transferFee,
                    "networkFee", networkFee,
                    "regulatoryFee", regulatoryFee,
                    "crossBorderFee", crossBorderFee
            ));
            
            auditService.createAuditEvent(AuditEvent.builder()
                    .eventType(AuditEventType.MOBILE_MONEY_FEE_CALCULATION)
                    .entityId(request.getTransferId())
                    .transactionId(transactionId)
                    .details("Fee calculation completed - Total Fees: " + totalFees)
                    .timestamp(LocalDateTime.now())
                    .build());
            
            return totalFees;
            
        } catch (Exception e) {
            log.error("Fee calculation failed - Transfer: {}, Transaction: {}, Error: {}", 
                    request.getTransferId(), transactionId, e.getMessage());
            throw new InvalidTransactionException("Fee calculation failed", e);
        }
    }
    
    /**
     * Step 10: Transaction Finalization and Settlement
     * Finalizes the mobile money transfer and initiates settlement
     */
    private void finalizeTransactionAndSettlement(MobileMoneyTransferRequest request, MobileMoneyTransferResult result, 
            BigDecimal totalFees, String transactionId) {
        log.debug("Step 10: Finalizing transaction and settlement - Transfer: {}, Transaction: {}", 
                request.getTransferId(), transactionId);
        
        try {
            // Finalize the transfer
            mobileMoneyService.finalizeTransfer(request, result, totalFees);
            
            // Initiate settlement with mobile money provider
            mobileWalletService.initiateProviderSettlement(request, result);
            
            // Update transaction status
            result.setStatus("COMPLETED");
            result.setCompletedAt(LocalDateTime.now());
            
            // Create settlement record
            mobileMoneyService.createSettlementRecord(request, result, totalFees);
            
            auditService.createAuditEvent(AuditEvent.builder()
                    .eventType(AuditEventType.MOBILE_MONEY_TRANSACTION_FINALIZATION)
                    .entityId(request.getTransferId())
                    .transactionId(transactionId)
                    .details("Transaction finalization completed successfully")
                    .timestamp(LocalDateTime.now())
                    .build());
                    
        } catch (Exception e) {
            log.error("Transaction finalization failed - Transfer: {}, Transaction: {}, Error: {}", 
                    request.getTransferId(), transactionId, e.getMessage());
            throw new InvalidTransactionException("Transaction finalization failed", e);
        }
    }
    
    /**
     * Step 11: Post-Processing Activities
     * Performs post-processing activities after successful mobile money transfer
     */
    private void performPostProcessingActivities(MobileMoneyTransferRequest request, MobileMoneyTransferResult result, String transactionId) {
        log.debug("Step 11: Performing post-processing activities - Transfer: {}, Transaction: {}", 
                request.getTransferId(), transactionId);
        
        try {
            // Update user transaction limits
            mobilePaymentLimitService.updateUserLimits(request.getSenderId(), request.getAmount());
            
            // Update provider statistics
            mobileMoneyService.updateProviderStatistics(request.getMobileMoneyProvider(), request, result);
            
            // Generate transaction receipt
            String receiptId = mobileMoneyService.generateTransactionReceipt(request, result);
            result.setReceiptId(receiptId);
            
            // Update loyalty points (if applicable)
            if (mobileWalletService.hasLoyaltyProgram(request.getSenderId())) {
                mobileWalletService.updateLoyaltyPoints(request.getSenderId(), request.getAmount());
            }
            
            // Publish domain events for downstream processing
            publishDomainEvents(request, result, transactionId);
            
            auditService.createAuditEvent(AuditEvent.builder()
                    .eventType(AuditEventType.MOBILE_MONEY_POST_PROCESSING)
                    .entityId(request.getTransferId())
                    .transactionId(transactionId)
                    .details("Post-processing activities completed successfully")
                    .timestamp(LocalDateTime.now())
                    .build());
                    
        } catch (Exception e) {
            log.error("Post-processing activities failed - Transfer: {}, Transaction: {}, Error: {}", 
                    request.getTransferId(), transactionId, e.getMessage());
            // Don't fail the entire transaction for post-processing errors
            log.warn("Continuing despite post-processing failure");
        }
    }
    
    /**
     * Step 12: Notification Dispatch and Audit Completion
     * Dispatches notifications and completes audit trail
     */
    private void dispatchNotificationsAndCompleteAudit(MobileMoneyTransferRequest request, MobileMoneyTransferResult result, 
            String transactionId, LocalDateTime startTime) {
        log.debug("Step 12: Dispatching notifications and completing audit - Transfer: {}, Transaction: {}", 
                request.getTransferId(), transactionId);
        
        try {
            // Dispatch notifications asynchronously
            CompletableFuture.runAsync(() -> {
                try {
                    // SMS notification to sender
                    notificationService.sendNotification(NotificationRequest.builder()
                            .userId(request.getSenderId())
                            .type("SMS")
                            .channel("MOBILE_MONEY_TRANSFER_CONFIRMATION")
                            .message(buildSenderNotificationMessage(request, result))
                            .mobileNumber(request.getSenderMobileNumber())
                            .build());
                    
                    // SMS notification to receiver
                    notificationService.sendNotification(NotificationRequest.builder()
                            .userId(request.getReceiverId())
                            .type("SMS")
                            .channel("MOBILE_MONEY_TRANSFER_RECEIVED")
                            .message(buildReceiverNotificationMessage(request, result))
                            .mobileNumber(request.getReceiverMobileNumber())
                            .build());
                    
                    // Email notification (if email addresses available)
                    if (mobileWalletService.hasEmailNotificationEnabled(request.getSenderId())) {
                        notificationService.sendEmailNotification(request.getSenderId(), 
                                "Mobile Money Transfer Confirmation", 
                                buildEmailNotificationContent(request, result));
                    }
                    
                } catch (Exception e) {
                    log.error("Failed to dispatch notifications - Transfer: {}, Error: {}", 
                            request.getTransferId(), e.getMessage());
                }
            });
            
            // Complete audit trail
            long processingDuration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.createAuditEvent(AuditEvent.builder()
                    .eventType(AuditEventType.MOBILE_MONEY_TRANSFER_COMPLETED)
                    .entityId(request.getTransferId())
                    .transactionId(transactionId)
                    .details(String.format("Mobile money transfer completed successfully - Duration: %dms, Amount: %s %s, Status: %s",
                            processingDuration, request.getAmount(), request.getCurrency(), result.getStatus()))
                    .timestamp(LocalDateTime.now())
                    .build());
                    
        } catch (Exception e) {
            log.error("Failed to complete notifications and audit - Transfer: {}, Transaction: {}, Error: {}", 
                    request.getTransferId(), transactionId, e.getMessage());
            // Don't fail the transaction for notification/audit errors
        }
    }
    
    /**
     * Publishes domain events for downstream processing
     */
    private void publishDomainEvents(MobileMoneyTransferRequest request, MobileMoneyTransferResult result, String transactionId) {
        try {
            // Publish mobile money transfer completed event
            DomainEvent transferCompletedEvent = DomainEvent.builder()
                    .eventType("MOBILE_MONEY_TRANSFER_COMPLETED")
                    .entityId(request.getTransferId())
                    .entityType("MOBILE_MONEY_TRANSFER")
                    .eventData(Map.of(
                            "transferId", request.getTransferId(),
                            "senderId", request.getSenderId(),
                            "receiverId", request.getReceiverId(),
                            "amount", request.getAmount(),
                            "currency", request.getCurrency(),
                            "provider", request.getMobileMoneyProvider(),
                            "status", result.getStatus(),
                            "fees", result.getTotalFees()
                    ))
                    .timestamp(LocalDateTime.now())
                    .transactionId(transactionId)
                    .build();
            
            eventPublisher.publishEvent(transferCompletedEvent);
            
            // Publish cross-border event if applicable
            if (request.isCrossBorder()) {
                DomainEvent crossBorderEvent = DomainEvent.builder()
                        .eventType("CROSS_BORDER_MOBILE_TRANSFER_COMPLETED")
                        .entityId(request.getTransferId())
                        .eventData(Map.of(
                                "senderCountry", request.getSenderCountryCode(),
                                "receiverCountry", request.getReceiverCountryCode(),
                                "amount", request.getAmount(),
                                "currency", request.getCurrency()
                        ))
                        .timestamp(LocalDateTime.now())
                        .build();
                
                eventPublisher.publishEvent(crossBorderEvent);
            }
            
        } catch (Exception e) {
            log.error("Failed to publish domain events - Transfer: {}, Error: {}", 
                    request.getTransferId(), e.getMessage());
        }
    }
    
    /**
     * Handles processing failures and sends to DLQ
     */
    private void handleProcessingFailure(String transferId, String transactionId, String eventPayload, 
            Exception error, LocalDateTime startTime) {
        try {
            log.error("Handling mobile money transfer processing failure - Transfer: {}, Transaction: {}", 
                    transferId, transactionId);
            
            // Clear processing cache
            if (transferId != null) {
                cacheService.delete(IDEMPOTENCY_KEY_PREFIX + transferId);
            }
            
            // Create failure audit event
            auditService.createAuditEvent(AuditEvent.builder()
                    .eventType(AuditEventType.MOBILE_MONEY_TRANSFER_FAILED)
                    .entityId(transferId)
                    .transactionId(transactionId)
                    .details("Mobile money transfer processing failed: " + error.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build());
            
            // Send to DLQ for manual investigation
            eventPublisher.publishToDLQ(DLQ_TOPIC, eventPayload, Map.of(
                    "transferId", transferId != null ? transferId : "UNKNOWN",
                    "transactionId", transactionId,
                    "errorMessage", error.getMessage(),
                    "errorType", error.getClass().getSimpleName(),
                    "processingDuration", String.valueOf(java.time.Duration.between(startTime, LocalDateTime.now()).toMillis())
            ));
            
        } catch (Exception e) {
            log.error("Failed to handle processing failure - Transfer: {}, Transaction: {}, Error: {}", 
                    transferId, transactionId, e.getMessage());
        }
    }
    
    // Helper methods for message building and utility functions
    
    private Map<String, Object> extractMetadata(JsonNode metadataNode) {
        Map<String, Object> metadata = new HashMap<>();
        if (metadataNode != null && !metadataNode.isNull()) {
            metadataNode.fields().forEachRemaining(entry -> 
                    metadata.put(entry.getKey(), entry.getValue().asText()));
        }
        return metadata;
    }
    
    private boolean determineCrossBorderStatus(JsonNode eventNode) {
        String senderCountry = eventNode.path("senderCountryCode").asText();
        String receiverCountry = eventNode.path("receiverCountryCode").asText();
        return senderCountry != null && receiverCountry != null && 
               !senderCountry.equals(receiverCountry);
    }
    
    private String buildSenderNotificationMessage(MobileMoneyTransferRequest request, MobileMoneyTransferResult result) {
        return String.format("Your mobile money transfer of %s %s to %s has been completed. Reference: %s. Fees: %s %s",
                request.getAmount(), request.getCurrency(), 
                maskMobileNumber(request.getReceiverMobileNumber()),
                result.getTransactionReference(),
                result.getTotalFees(), request.getCurrency());
    }
    
    private String buildReceiverNotificationMessage(MobileMoneyTransferRequest request, MobileMoneyTransferResult result) {
        return String.format("You have received %s %s from %s via mobile money transfer. Reference: %s",
                request.getAmount(), request.getCurrency(),
                maskMobileNumber(request.getSenderMobileNumber()),
                result.getTransactionReference());
    }
    
    private String buildEmailNotificationContent(MobileMoneyTransferRequest request, MobileMoneyTransferResult result) {
        return String.format("""
                Dear Customer,
                
                Your mobile money transfer has been completed successfully:
                
                Transfer Details:
                - Amount: %s %s
                - Recipient: %s
                - Provider: %s
                - Reference: %s
                - Date: %s
                - Fees: %s %s
                
                Thank you for using our mobile money services.
                
                Best regards,
                Waqiti Mobile Money Team
                """, 
                request.getAmount(), request.getCurrency(),
                maskMobileNumber(request.getReceiverMobileNumber()),
                request.getMobileMoneyProvider(),
                result.getTransactionReference(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                result.getTotalFees(), request.getCurrency());
    }
    
    private String maskMobileNumber(String mobileNumber) {
        if (mobileNumber == null || mobileNumber.length() < 4) {
            return "***";
        }
        return mobileNumber.substring(0, 3) + "***" + mobileNumber.substring(mobileNumber.length() - 2);
    }
}