package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.fraud.ComprehensiveFraudDetectionService;
import com.waqiti.common.fraud.RegulatoryComplianceService;
import com.waqiti.common.audit.AuditLogger;
import com.waqiti.payment.model.ACHReturn;
import com.waqiti.payment.model.ACHReturnCode;
import com.waqiti.payment.service.ACHProcessingService;
import com.waqiti.payment.service.NotificationService;
import com.waqiti.payment.service.PaymentReconciliationService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Consumer #141: ACH Return Processing Event Consumer
 * Handles ACH return and reversal transactions with comprehensive compliance and fraud detection
 * Implements zero-tolerance 12-step processing pattern with SERIALIZABLE transaction isolation
 */
@Component
public class ACHReturnProcessingEventConsumer {
    private static final Logger logger = LoggerFactory.getLogger(ACHReturnProcessingEventConsumer.class);
    
    private static final String TOPIC = "ach-return-processing";
    private static final String DLQ_TOPIC = "ach-return-processing-dlq";
    private static final String CONSUMER_GROUP = "ach-return-processing-consumer-group";
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Autowired
    private ACHProcessingService achProcessingService;
    
    @Autowired
    private PaymentReconciliationService reconciliationService;
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private RegulatoryComplianceService complianceEngine;

    @Autowired
    private ComprehensiveFraudDetectionService fraudDetectionEngine;

    @Autowired
    private AuditLogger auditLogger;
    
    @Value("${payment.ach.return.processing.timeout:30}")
    private int processingTimeoutSeconds;
    
    @Value("${payment.ach.return.retry.max-attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${payment.ach.return.batch.size:100}")
    private int batchSize;
    
    private Counter returnsProcessedCounter;
    private Counter returnsReversedCounter;
    private Counter fraudDetectedCounter;
    private Counter complianceViolationsCounter;
    private Counter errorCounter;
    private Timer processingTimer;
    
    private CircuitBreaker circuitBreaker;
    private Retry retry;
    
    private final Map<String, ACHReturnProcessingState> processingStates = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        initializeMetrics();
        initializeResilience();
        logger.info("ACHReturnProcessingEventConsumer initialized with compliance and fraud detection");
    }
    
    private void initializeMetrics() {
        returnsProcessedCounter = Counter.builder("ach.returns.processed")
                .description("Total number of ACH returns processed")
                .register(meterRegistry);
                
        returnsReversedCounter = Counter.builder("ach.returns.reversed")
                .description("Total number of ACH returns reversed")
                .register(meterRegistry);
                
        fraudDetectedCounter = Counter.builder("ach.returns.fraud.detected")
                .description("Number of fraudulent ACH returns detected")
                .register(meterRegistry);
                
        complianceViolationsCounter = Counter.builder("ach.returns.compliance.violations")
                .description("Number of compliance violations in ACH returns")
                .register(meterRegistry);
                
        errorCounter = Counter.builder("ach.returns.errors")
                .description("Number of ACH return processing errors")
                .register(meterRegistry);
                
        processingTimer = Timer.builder("ach.returns.processing.time")
                .description("Time taken to process ACH returns")
                .register(meterRegistry);
    }
    
    private void initializeResilience() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(100)
                .permittedNumberOfCallsInHalfOpenState(10)
                .build();
                
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("ach-return-processor");
        
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(maxRetryAttempts)
                .waitDuration(Duration.ofSeconds(2))
                .retryExceptions(Exception.class)
                .build();
                
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
        retry = retryRegistry.retry("ach-return-retry");
    }
    
    @KafkaListener(topics = TOPIC, groupId = CONSUMER_GROUP)
    @Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRED, timeout = 30)
    public void processACHReturnEvent(@Payload String message,
                                    @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                    @Header(KafkaHeaders.OFFSET) long offset,
                                    @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
                                    Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = UUID.randomUUID().toString();
        
        // Step 1: Initialize MDC and logging context
        MDC.put("correlation.id", correlationId);
        MDC.put("topic", topic);
        MDC.put("partition", String.valueOf(partition));
        MDC.put("offset", String.valueOf(offset));
        MDC.put("processor", "ACHReturnProcessingEventConsumer");
        
        try {
            logger.info("Step 1: Processing ACH return event - correlation: {}", correlationId);
            
            // Step 2: Parse and validate message structure
            Map<String, Object> eventData = parseAndValidateMessage(message);
            if (eventData == null) {
                sendToDlq(message, "Invalid message structure", correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            String achReturnId = (String) eventData.get("achReturnId");
            String originalTransactionId = (String) eventData.get("originalTransactionId");
            String returnCode = (String) eventData.get("returnCode");
            
            MDC.put("ach.return.id", achReturnId);
            MDC.put("original.transaction.id", originalTransactionId);
            MDC.put("return.code", returnCode);
            
            logger.info("Step 2: Message validated - ACH Return ID: {}, Return Code: {}", achReturnId, returnCode);
            
            // Step 3: Fraud detection screening
            if (!performFraudDetection(eventData, correlationId)) {
                logger.warn("Step 3: Fraud detected in ACH return: {}", achReturnId);
                fraudDetectedCounter.increment();
                sendToDlq(message, "Fraud detected", correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            logger.info("Step 3: Fraud detection passed");
            
            // Step 4: Compliance validation
            if (!performComplianceValidation(eventData, correlationId)) {
                logger.warn("Step 4: Compliance violation in ACH return: {}", achReturnId);
                complianceViolationsCounter.increment();
                sendToDlq(message, "Compliance violation", correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            logger.info("Step 4: Compliance validation passed");
            
            // Step 5: Business logic processing with circuit breaker
            Supplier<Boolean> processor = () -> processACHReturnBusinessLogic(eventData, correlationId);
            Boolean result = Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(circuitBreaker, processor)).get();
            
            if (!result) {
                logger.error("Step 5: Business logic processing failed for ACH return: {}", achReturnId);
                sendToDlq(message, "Business processing failed", correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            logger.info("Step 5: Business logic processing completed");
            
            // Step 6: Update transaction states
            updateTransactionStates(eventData, correlationId);
            logger.info("Step 6: Transaction states updated");
            
            // Step 7: Reconciliation processing
            performReconciliation(eventData, correlationId);
            logger.info("Step 7: Reconciliation completed");
            
            // Step 8: Regulatory reporting
            generateRegulatoryReports(eventData, correlationId);
            logger.info("Step 8: Regulatory reports generated");
            
            // Step 9: Customer notifications
            sendCustomerNotifications(eventData, correlationId);
            logger.info("Step 9: Customer notifications sent");
            
            // Step 10: Audit trail creation
            createAuditTrail(eventData, correlationId);
            logger.info("Step 10: Audit trail created");
            
            // Step 11: Cache updates
            updateCacheState(eventData, correlationId);
            logger.info("Step 11: Cache state updated");
            
            // Step 12: Final acknowledgment and metrics
            returnsProcessedCounter.increment();
            acknowledgment.acknowledge();
            logger.info("Step 12: ACH return processing completed successfully for: {}", achReturnId);
            
        } catch (Exception e) {
            logger.error("Error processing ACH return event", e);
            errorCounter.increment();
            sendToDlq(message, e.getMessage(), correlationId);
            acknowledgment.acknowledge();
        } finally {
            processingTimer.stop(sample);
            MDC.clear();
        }
    }
    
    private Map<String, Object> parseAndValidateMessage(String message) {
        try {
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            // Validate required fields
            List<String> requiredFields = Arrays.asList(
                "achReturnId", "originalTransactionId", "returnCode", "amount", 
                "accountNumber", "routingNumber", "returnDate", "customerId"
            );
            
            for (String field : requiredFields) {
                if (!eventData.containsKey(field) || eventData.get(field) == null) {
                    logger.error("Missing required field: {}", field);
                    return null;
                }
            }
            
            // Validate return code
            String returnCode = (String) eventData.get("returnCode");
            if (!ACHReturnCode.isValidCode(returnCode)) {
                logger.error("Invalid ACH return code: {}", returnCode);
                return null;
            }
            
            // Validate amount
            Object amountObj = eventData.get("amount");
            if (!(amountObj instanceof Number) || new BigDecimal(amountObj.toString()).compareTo(BigDecimal.ZERO) <= 0) {
                logger.error("Invalid amount: {}", amountObj);
                return null;
            }
            
            return eventData;
        } catch (Exception e) {
            logger.error("Failed to parse message", e);
            return null;
        }
    }
    
    private boolean performFraudDetection(Map<String, Object> eventData, String correlationId) {
        try {
            String achReturnId = (String) eventData.get("achReturnId");
            String accountNumber = (String) eventData.get("accountNumber");
            String customerId = (String) eventData.get("customerId");
            BigDecimal amount = new BigDecimal(eventData.get("amount").toString());
            
            // Check for suspicious return patterns
            if (fraudDetectionEngine.checkSuspiciousReturnPattern(accountNumber, correlationId)) {
                logger.warn("Suspicious return pattern detected for account: {}", accountNumber);
                auditLogger.logSecurityEvent("SUSPICIOUS_ACH_RETURN_PATTERN", correlationId, 
                    Map.of("accountNumber", accountNumber, "achReturnId", achReturnId));
                return false;
            }
            
            // Check for high-risk customer
            if (fraudDetectionEngine.isHighRiskCustomer(customerId, correlationId)) {
                logger.warn("High-risk customer detected: {}", customerId);
                auditLogger.logSecurityEvent("HIGH_RISK_CUSTOMER_ACH_RETURN", correlationId,
                    Map.of("customerId", customerId, "achReturnId", achReturnId));
                return false;
            }
            
            // Check for amount thresholds
            if (fraudDetectionEngine.exceedsReturnAmountThreshold(amount, correlationId)) {
                logger.warn("Return amount exceeds threshold: {}", amount);
                auditLogger.logSecurityEvent("ACH_RETURN_AMOUNT_THRESHOLD_EXCEEDED", correlationId,
                    Map.of("amount", amount.toString(), "achReturnId", achReturnId));
                return false;
            }
            
            return true;
        } catch (Exception e) {
            logger.error("Fraud detection failed", e);
            return false;
        }
    }
    
    private boolean performComplianceValidation(Map<String, Object> eventData, String correlationId) {
        try {
            String customerId = (String) eventData.get("customerId");
            String accountNumber = (String) eventData.get("accountNumber");
            String returnCode = (String) eventData.get("returnCode");
            BigDecimal amount = new BigDecimal(eventData.get("amount").toString());
            
            // BSA/AML compliance check
            if (!complianceEngine.validateBSACompliance(customerId, amount, "ACH_RETURN", correlationId)) {
                logger.warn("BSA/AML compliance violation for customer: {}", customerId);
                auditLogger.logComplianceViolation("BSA_AML_VIOLATION", correlationId,
                    Map.of("customerId", customerId, "violationType", "ACH_RETURN"));
                return false;
            }
            
            // OFAC sanctions screening
            if (!complianceEngine.performOFACScreening(customerId, correlationId)) {
                logger.warn("OFAC sanctions match for customer: {}", customerId);
                auditLogger.logComplianceViolation("OFAC_SANCTIONS_MATCH", correlationId,
                    Map.of("customerId", customerId, "screeningType", "ACH_RETURN"));
                return false;
            }
            
            // Reg E compliance for unauthorized returns
            if (ACHReturnCode.isUnauthorizedReturn(returnCode)) {
                if (!complianceEngine.validateRegECompliance(customerId, correlationId)) {
                    logger.warn("Reg E compliance violation for unauthorized return: {}", customerId);
                    auditLogger.logComplianceViolation("REG_E_VIOLATION", correlationId,
                        Map.of("customerId", customerId, "returnCode", returnCode));
                    return false;
                }
            }
            
            // Check return frequency limits
            if (!complianceEngine.validateReturnFrequencyLimits(accountNumber, correlationId)) {
                logger.warn("Return frequency limits exceeded for account: {}", accountNumber);
                auditLogger.logComplianceViolation("RETURN_FREQUENCY_VIOLATION", correlationId,
                    Map.of("accountNumber", accountNumber));
                return false;
            }
            
            return true;
        } catch (Exception e) {
            logger.error("Compliance validation failed", e);
            return false;
        }
    }
    
    private boolean processACHReturnBusinessLogic(Map<String, Object> eventData, String correlationId) {
        try {
            String achReturnId = (String) eventData.get("achReturnId");
            String originalTransactionId = (String) eventData.get("originalTransactionId");
            String returnCode = (String) eventData.get("returnCode");
            BigDecimal amount = new BigDecimal(eventData.get("amount").toString());
            String accountNumber = (String) eventData.get("accountNumber");
            String routingNumber = (String) eventData.get("routingNumber");
            
            logger.info("Processing ACH return: {} for original transaction: {}", achReturnId, originalTransactionId);
            
            // Create ACH return record
            ACHReturn achReturn = new ACHReturn();
            achReturn.setReturnId(achReturnId);
            achReturn.setOriginalTransactionId(originalTransactionId);
            achReturn.setReturnCode(returnCode);
            achReturn.setAmount(amount);
            achReturn.setAccountNumber(accountNumber);
            achReturn.setRoutingNumber(routingNumber);
            achReturn.setReturnDate(LocalDateTime.now());
            achReturn.setProcessingStatus("PROCESSING");
            achReturn.setCorrelationId(correlationId);
            
            // Save return record
            achProcessingService.saveACHReturn(achReturn);
            
            // Process return based on return code
            switch (returnCode) {
                case "R01": // Insufficient Funds
                    processInsufficientFundsReturn(achReturn, correlationId);
                    break;
                case "R02": // Account Closed
                    processAccountClosedReturn(achReturn, correlationId);
                    break;
                case "R03": // No Account/Unable to Locate Account
                    processNoAccountReturn(achReturn, correlationId);
                    break;
                case "R04": // Invalid Account Number
                    processInvalidAccountReturn(achReturn, correlationId);
                    break;
                case "R05": // Unauthorized Debit to Consumer Account
                    processUnauthorizedDebitReturn(achReturn, correlationId);
                    break;
                case "R10": // Customer Advises Not Authorized
                    processCustomerUnauthorizedReturn(achReturn, correlationId);
                    break;
                case "R11": // Check Truncation Entry Return
                    processCheckTruncationReturn(achReturn, correlationId);
                    break;
                case "R12": // Branch Sold to Another DFI
                    processBranchSoldReturn(achReturn, correlationId);
                    break;
                default:
                    processGeneralReturn(achReturn, correlationId);
                    break;
            }
            
            // Update return status
            achReturn.setProcessingStatus("PROCESSED");
            achProcessingService.updateACHReturn(achReturn);
            
            // Update processing state
            ACHReturnProcessingState state = new ACHReturnProcessingState();
            state.setReturnId(achReturnId);
            state.setStatus("COMPLETED");
            state.setProcessedAt(Instant.now());
            state.setCorrelationId(correlationId);
            processingStates.put(achReturnId, state);
            
            returnsReversedCounter.increment();
            logger.info("ACH return processing completed for: {}", achReturnId);
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to process ACH return business logic", e);
            return false;
        }
    }
    
    private void processInsufficientFundsReturn(ACHReturn achReturn, String correlationId) {
        logger.info("Processing insufficient funds return: {}", achReturn.getReturnId());
        
        // Reverse the original transaction
        achProcessingService.reverseTransaction(achReturn.getOriginalTransactionId(), 
            "INSUFFICIENT_FUNDS", correlationId);
        
        // Apply NSF fees if applicable
        achProcessingService.applyNSFCharges(achReturn.getAccountNumber(), 
            achReturn.getAmount(), correlationId);
        
        // Update account status
        achProcessingService.updateAccountStatus(achReturn.getAccountNumber(), 
            "NSF_RETURN", correlationId);
    }
    
    private void processAccountClosedReturn(ACHReturn achReturn, String correlationId) {
        logger.info("Processing account closed return: {}", achReturn.getReturnId());
        
        // Reverse the transaction
        achProcessingService.reverseTransaction(achReturn.getOriginalTransactionId(),
            "ACCOUNT_CLOSED", correlationId);
        
        // Update customer records
        achProcessingService.markAccountClosed(achReturn.getAccountNumber(), correlationId);
        
        // Initiate account closure procedures
        achProcessingService.initiateAccountClosureProcedures(achReturn.getAccountNumber(), correlationId);
    }
    
    private void processNoAccountReturn(ACHReturn achReturn, String correlationId) {
        logger.info("Processing no account return: {}", achReturn.getReturnId());
        
        // Reverse the transaction
        achProcessingService.reverseTransaction(achReturn.getOriginalTransactionId(),
            "NO_ACCOUNT", correlationId);
        
        // Flag for investigation
        achProcessingService.flagForInvestigation(achReturn.getAccountNumber(), 
            "NO_ACCOUNT_FOUND", correlationId);
    }
    
    private void processInvalidAccountReturn(ACHReturn achReturn, String correlationId) {
        logger.info("Processing invalid account return: {}", achReturn.getReturnId());
        
        // Reverse the transaction
        achProcessingService.reverseTransaction(achReturn.getOriginalTransactionId(),
            "INVALID_ACCOUNT", correlationId);
        
        // Update routing/account validation rules
        achProcessingService.updateValidationRules(achReturn.getRoutingNumber(),
            achReturn.getAccountNumber(), correlationId);
    }
    
    private void processUnauthorizedDebitReturn(ACHReturn achReturn, String correlationId) {
        logger.info("Processing unauthorized debit return: {}", achReturn.getReturnId());
        
        // Immediate reversal
        achProcessingService.reverseTransaction(achReturn.getOriginalTransactionId(),
            "UNAUTHORIZED_DEBIT", correlationId);
        
        // Initiate fraud investigation
        achProcessingService.initiateFraudInvestigation(achReturn.getAccountNumber(),
            achReturn.getReturnId(), correlationId);
        
        // Apply enhanced monitoring
        achProcessingService.applyEnhancedMonitoring(achReturn.getAccountNumber(), correlationId);
    }
    
    private void processCustomerUnauthorizedReturn(ACHReturn achReturn, String correlationId) {
        logger.info("Processing customer unauthorized return: {}", achReturn.getReturnId());
        
        // Reverse transaction
        achProcessingService.reverseTransaction(achReturn.getOriginalTransactionId(),
            "CUSTOMER_UNAUTHORIZED", correlationId);
        
        // Customer dispute handling
        achProcessingService.initiateDisputeProcess(achReturn.getAccountNumber(),
            achReturn.getReturnId(), correlationId);
    }
    
    private void processCheckTruncationReturn(ACHReturn achReturn, String correlationId) {
        logger.info("Processing check truncation return: {}", achReturn.getReturnId());
        
        // Handle check image issues
        achProcessingService.handleCheckImageIssues(achReturn.getOriginalTransactionId(), correlationId);
        
        // Reverse if necessary
        achProcessingService.reverseTransaction(achReturn.getOriginalTransactionId(),
            "CHECK_TRUNCATION", correlationId);
    }
    
    private void processBranchSoldReturn(ACHReturn achReturn, String correlationId) {
        logger.info("Processing branch sold return: {}", achReturn.getReturnId());
        
        // Update routing information
        achProcessingService.updateRoutingInformation(achReturn.getRoutingNumber(), correlationId);
        
        // Re-route transaction if possible
        achProcessingService.rerouteTransaction(achReturn.getOriginalTransactionId(), correlationId);
    }
    
    private void processGeneralReturn(ACHReturn achReturn, String correlationId) {
        logger.info("Processing general return: {}", achReturn.getReturnId());
        
        // Standard reversal process
        achProcessingService.reverseTransaction(achReturn.getOriginalTransactionId(),
            "GENERAL_RETURN", correlationId);
        
        // Standard notification
        achProcessingService.sendStandardNotification(achReturn.getAccountNumber(),
            achReturn.getReturnCode(), correlationId);
    }
    
    private void updateTransactionStates(Map<String, Object> eventData, String correlationId) {
        try {
            String achReturnId = (String) eventData.get("achReturnId");
            String originalTransactionId = (String) eventData.get("originalTransactionId");
            
            // Update transaction status
            achProcessingService.updateTransactionStatus(originalTransactionId, 
                "RETURNED", correlationId);
            
            // Update return processing status
            achProcessingService.updateReturnStatus(achReturnId, "PROCESSED", correlationId);
            
            logger.info("Transaction states updated for return: {}", achReturnId);
        } catch (Exception e) {
            logger.error("Failed to update transaction states", e);
            throw new RuntimeException("Transaction state update failed", e);
        }
    }
    
    private void performReconciliation(Map<String, Object> eventData, String correlationId) {
        try {
            String achReturnId = (String) eventData.get("achReturnId");
            String originalTransactionId = (String) eventData.get("originalTransactionId");
            BigDecimal amount = new BigDecimal(eventData.get("amount").toString());
            
            // Perform balance reconciliation
            reconciliationService.reconcileACHReturn(originalTransactionId, amount, correlationId);
            
            // Update settlement records
            reconciliationService.updateSettlementRecords(achReturnId, "RETURNED", correlationId);
            
            // Generate reconciliation report entry
            reconciliationService.createReconciliationEntry(achReturnId, "ACH_RETURN", 
                amount, correlationId);
                
            logger.info("Reconciliation completed for return: {}", achReturnId);
        } catch (Exception e) {
            logger.error("Reconciliation failed", e);
            throw new RuntimeException("Reconciliation failed", e);
        }
    }
    
    private void generateRegulatoryReports(Map<String, Object> eventData, String correlationId) {
        try {
            String achReturnId = (String) eventData.get("achReturnId");
            String returnCode = (String) eventData.get("returnCode");
            BigDecimal amount = new BigDecimal(eventData.get("amount").toString());
            String customerId = (String) eventData.get("customerId");
            
            // Generate NACHA return report
            complianceEngine.generateNACHAReturnReport(achReturnId, returnCode, 
                amount, correlationId);
            
            // Generate suspicious activity report if needed
            if (ACHReturnCode.isSuspiciousReturn(returnCode)) {
                complianceEngine.generateSARReport(customerId, achReturnId, 
                    "SUSPICIOUS_ACH_RETURN", correlationId);
            }
            
            // Generate CTR if threshold exceeded
            if (amount.compareTo(new BigDecimal("10000")) >= 0) {
                complianceEngine.generateCTRReport(customerId, amount, 
                    "ACH_RETURN", correlationId);
            }
            
            logger.info("Regulatory reports generated for return: {}", achReturnId);
        } catch (Exception e) {
            logger.error("Failed to generate regulatory reports", e);
            throw new RuntimeException("Regulatory reporting failed", e);
        }
    }
    
    private void sendCustomerNotifications(Map<String, Object> eventData, String correlationId) {
        try {
            String customerId = (String) eventData.get("customerId");
            String achReturnId = (String) eventData.get("achReturnId");
            String returnCode = (String) eventData.get("returnCode");
            BigDecimal amount = new BigDecimal(eventData.get("amount").toString());
            
            // Send return notification
            notificationService.sendACHReturnNotification(customerId, achReturnId, 
                returnCode, amount, correlationId);
            
            // Send regulatory notices if required
            if (ACHReturnCode.requiresRegulatoryNotice(returnCode)) {
                notificationService.sendRegulatoryNotice(customerId, returnCode, correlationId);
            }
            
            // Send fee notification if applicable
            if (ACHReturnCode.hasAssociatedFees(returnCode)) {
                notificationService.sendFeeNotification(customerId, returnCode, correlationId);
            }
            
            logger.info("Customer notifications sent for return: {}", achReturnId);
        } catch (Exception e) {
            logger.error("Failed to send customer notifications", e);
            throw new RuntimeException("Customer notification failed", e);
        }
    }
    
    private void createAuditTrail(Map<String, Object> eventData, String correlationId) {
        try {
            String achReturnId = (String) eventData.get("achReturnId");
            String originalTransactionId = (String) eventData.get("originalTransactionId");
            String returnCode = (String) eventData.get("returnCode");
            
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("eventType", "ACH_RETURN_PROCESSED");
            auditData.put("achReturnId", achReturnId);
            auditData.put("originalTransactionId", originalTransactionId);
            auditData.put("returnCode", returnCode);
            auditData.put("processingTimestamp", Instant.now().toString());
            auditData.put("correlationId", correlationId);
            auditData.putAll(eventData);
            
            auditLogger.logBusinessEvent("ACH_RETURN_PROCESSED", correlationId, auditData);
            
            logger.info("Audit trail created for return: {}", achReturnId);
        } catch (Exception e) {
            logger.error("Failed to create audit trail", e);
            throw new RuntimeException("Audit trail creation failed", e);
        }
    }
    
    private void updateCacheState(Map<String, Object> eventData, String correlationId) {
        try {
            String achReturnId = (String) eventData.get("achReturnId");
            String accountNumber = (String) eventData.get("accountNumber");
            String customerId = (String) eventData.get("customerId");
            
            // Update return processing cache
            String returnCacheKey = "ach:return:" + achReturnId;
            redisTemplate.opsForValue().set(returnCacheKey, "PROCESSED", Duration.ofDays(30));
            
            // Update account status cache
            String accountCacheKey = "account:status:" + accountNumber;
            redisTemplate.opsForHash().put(accountCacheKey, "lastReturn", Instant.now().toString());
            redisTemplate.expire(accountCacheKey, Duration.ofDays(90));
            
            // Update customer profile cache
            String customerCacheKey = "customer:profile:" + customerId;
            redisTemplate.opsForHash().put(customerCacheKey, "lastACHReturn", achReturnId);
            redisTemplate.expire(customerCacheKey, Duration.ofDays(365));
            
            logger.info("Cache state updated for return: {}", achReturnId);
        } catch (Exception e) {
            logger.error("Failed to update cache state", e);
            throw new RuntimeException("Cache update failed", e);
        }
    }
    
    private void sendToDlq(String message, String reason, String correlationId) {
        try {
            ProducerRecord<String, String> dlqRecord = new ProducerRecord<>(DLQ_TOPIC, message);
            dlqRecord.headers().add("failure_reason", reason.getBytes(StandardCharsets.UTF_8));
            dlqRecord.headers().add("correlation_id", correlationId.getBytes(StandardCharsets.UTF_8));
            dlqRecord.headers().add("original_topic", TOPIC.getBytes(StandardCharsets.UTF_8));
            dlqRecord.headers().add("failed_at", Instant.now().toString().getBytes(StandardCharsets.UTF_8));
            
            kafkaTemplate.send(dlqRecord);
            logger.warn("Message sent to DLQ with reason: {} - correlation: {}", reason, correlationId);
        } catch (Exception e) {
            logger.error("Failed to send message to DLQ", e);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down ACHReturnProcessingEventConsumer...");
        processingStates.clear();
        logger.info("ACHReturnProcessingEventConsumer shutdown complete");
    }
    
    private static class ACHReturnProcessingState {
        private String returnId;
        private String status;
        private Instant processedAt;
        private String correlationId;
        
        // Getters and setters
        public String getReturnId() { return returnId; }
        public void setReturnId(String returnId) { this.returnId = returnId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Instant getProcessedAt() { return processedAt; }
        public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    }
}