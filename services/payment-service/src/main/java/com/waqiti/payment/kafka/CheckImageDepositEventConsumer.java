package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.compliance.ComplianceEngine;
import com.waqiti.common.fraud.FraudDetectionEngine;
import com.waqiti.common.security.AuditLogger;
import com.waqiti.payment.model.CheckDeposit;
import com.waqiti.payment.model.CheckImage;
import com.waqiti.payment.service.CheckProcessingService;
import com.waqiti.payment.service.OCRService;
import com.waqiti.payment.service.NotificationService;
import com.waqiti.payment.service.FundsAvailabilityService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Consumer #142: Check Image Deposit Event Consumer
 * Handles mobile check deposits with OCR processing, compliance validation, and fraud detection
 * Implements zero-tolerance 12-step processing pattern with SERIALIZABLE transaction isolation
 */
@Component
public class CheckImageDepositEventConsumer {
    private static final Logger logger = LoggerFactory.getLogger(CheckImageDepositEventConsumer.class);
    
    private static final String TOPIC = "check-image-deposit";
    private static final String DLQ_TOPIC = "check-image-deposit-dlq";
    private static final String CONSUMER_GROUP = "check-image-deposit-consumer-group";
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Autowired
    private CheckProcessingService checkProcessingService;
    
    @Autowired
    private OCRService ocrService;
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private FundsAvailabilityService fundsAvailabilityService;
    
    @Autowired
    private ComplianceEngine complianceEngine;
    
    @Autowired
    private FraudDetectionEngine fraudDetectionEngine;
    
    @Autowired
    private AuditLogger auditLogger;
    
    @Value("${payment.check.deposit.max-amount:50000}")
    private BigDecimal maxDepositAmount;
    
    @Value("${payment.check.deposit.hold-days:2}")
    private int standardHoldDays;
    
    @Value("${payment.check.ocr.confidence.threshold:0.85}")
    private double ocrConfidenceThreshold;
    
    @Value("${payment.check.duplicate.window.hours:72}")
    private int duplicateDetectionWindowHours;
    
    private Counter depositsProcessedCounter;
    private Counter ocrProcessedCounter;
    private Counter fraudDetectedCounter;
    private Counter complianceViolationsCounter;
    private Counter duplicateChecksCounter;
    private Counter errorCounter;
    private Timer processingTimer;
    private Timer ocrTimer;
    
    private CircuitBreaker circuitBreaker;
    private Retry retry;
    
    private final Map<String, CheckDepositProcessingState> processingStates = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        initializeMetrics();
        initializeResilience();
        logger.info("CheckImageDepositEventConsumer initialized with OCR and fraud detection");
    }
    
    private void initializeMetrics() {
        depositsProcessedCounter = Counter.builder("check.deposits.processed")
                .description("Total number of check deposits processed")
                .register(meterRegistry);
                
        ocrProcessedCounter = Counter.builder("check.ocr.processed")
                .description("Total number of check images processed with OCR")
                .register(meterRegistry);
                
        fraudDetectedCounter = Counter.builder("check.deposits.fraud.detected")
                .description("Number of fraudulent check deposits detected")
                .register(meterRegistry);
                
        complianceViolationsCounter = Counter.builder("check.deposits.compliance.violations")
                .description("Number of compliance violations in check deposits")
                .register(meterRegistry);
                
        duplicateChecksCounter = Counter.builder("check.deposits.duplicates")
                .description("Number of duplicate check deposits detected")
                .register(meterRegistry);
                
        errorCounter = Counter.builder("check.deposits.errors")
                .description("Number of check deposit processing errors")
                .register(meterRegistry);
                
        processingTimer = Timer.builder("check.deposits.processing.time")
                .description("Time taken to process check deposits")
                .register(meterRegistry);
                
        ocrTimer = Timer.builder("check.ocr.processing.time")
                .description("Time taken for OCR processing")
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
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("check-deposit-processor");
        
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(2))
                .retryExceptions(Exception.class)
                .build();
                
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
        retry = retryRegistry.retry("check-deposit-retry");
    }
    
    @KafkaListener(topics = TOPIC, groupId = CONSUMER_GROUP)
    @Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRED, timeout = 45)
    public void processCheckImageDepositEvent(@Payload String message,
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
        MDC.put("processor", "CheckImageDepositEventConsumer");
        
        try {
            logger.info("Step 1: Processing check image deposit event - correlation: {}", correlationId);
            
            // Step 2: Parse and validate message structure
            Map<String, Object> eventData = parseAndValidateMessage(message);
            if (eventData == null) {
                sendToDlq(message, "Invalid message structure", correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            String depositId = (String) eventData.get("depositId");
            String customerId = (String) eventData.get("customerId");
            String accountId = (String) eventData.get("accountId");
            
            MDC.put("deposit.id", depositId);
            MDC.put("customer.id", customerId);
            MDC.put("account.id", accountId);
            
            logger.info("Step 2: Message validated - Deposit ID: {}", depositId);
            
            // Step 3: OCR processing and data extraction
            if (!performOCRProcessing(eventData, correlationId)) {
                logger.warn("Step 3: OCR processing failed for deposit: {}", depositId);
                sendToDlq(message, "OCR processing failed", correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            logger.info("Step 3: OCR processing completed");
            
            // Step 4: Duplicate check detection
            if (!performDuplicateDetection(eventData, correlationId)) {
                logger.warn("Step 4: Duplicate check detected for deposit: {}", depositId);
                duplicateChecksCounter.increment();
                sendToDlq(message, "Duplicate check detected", correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            logger.info("Step 4: Duplicate detection passed");
            
            // Step 5: Fraud detection screening
            if (!performFraudDetection(eventData, correlationId)) {
                logger.warn("Step 5: Fraud detected in check deposit: {}", depositId);
                fraudDetectedCounter.increment();
                sendToDlq(message, "Fraud detected", correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            logger.info("Step 5: Fraud detection passed");
            
            // Step 6: Compliance validation
            if (!performComplianceValidation(eventData, correlationId)) {
                logger.warn("Step 6: Compliance violation in check deposit: {}", depositId);
                complianceViolationsCounter.increment();
                sendToDlq(message, "Compliance violation", correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            logger.info("Step 6: Compliance validation passed");
            
            // Step 7: Business logic processing with circuit breaker
            Supplier<Boolean> processor = () -> processCheckDepositBusinessLogic(eventData, correlationId);
            Boolean result = Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(circuitBreaker, processor)).get();
            
            if (!result) {
                logger.error("Step 7: Business logic processing failed for deposit: {}", depositId);
                sendToDlq(message, "Business processing failed", correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            logger.info("Step 7: Business logic processing completed");
            
            // Step 8: Funds availability determination
            determineFundsAvailability(eventData, correlationId);
            logger.info("Step 8: Funds availability determined");
            
            // Step 9: Regulatory reporting
            generateRegulatoryReports(eventData, correlationId);
            logger.info("Step 9: Regulatory reports generated");
            
            // Step 10: Customer notifications
            sendCustomerNotifications(eventData, correlationId);
            logger.info("Step 10: Customer notifications sent");
            
            // Step 11: Audit trail creation
            createAuditTrail(eventData, correlationId);
            logger.info("Step 11: Audit trail created");
            
            // Step 12: Final acknowledgment and metrics
            depositsProcessedCounter.increment();
            acknowledgment.acknowledge();
            logger.info("Step 12: Check image deposit processing completed successfully for: {}", depositId);
            
        } catch (Exception e) {
            logger.error("Error processing check image deposit event", e);
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
                "depositId", "customerId", "accountId", "frontImageUrl", "backImageUrl",
                "depositAmount", "checkNumber", "routingNumber", "accountNumber", "memo"
            );
            
            for (String field : requiredFields) {
                if (!eventData.containsKey(field) || eventData.get(field) == null) {
                    logger.error("Missing required field: {}", field);
                    return null;
                }
            }
            
            // Validate deposit amount
            Object amountObj = eventData.get("depositAmount");
            if (!(amountObj instanceof Number)) {
                logger.error("Invalid deposit amount: {}", amountObj);
                return null;
            }
            
            BigDecimal amount = new BigDecimal(amountObj.toString());
            if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(maxDepositAmount) > 0) {
                logger.error("Deposit amount out of range: {}", amount);
                return null;
            }
            
            // Validate image URLs
            String frontImageUrl = (String) eventData.get("frontImageUrl");
            String backImageUrl = (String) eventData.get("backImageUrl");
            if (!isValidImageUrl(frontImageUrl) || !isValidImageUrl(backImageUrl)) {
                logger.error("Invalid image URLs provided");
                return null;
            }
            
            return eventData;
        } catch (Exception e) {
            logger.error("Failed to parse message", e);
            return null;
        }
    }
    
    private boolean performOCRProcessing(Map<String, Object> eventData, String correlationId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String depositId = (String) eventData.get("depositId");
            String frontImageUrl = (String) eventData.get("frontImageUrl");
            String backImageUrl = (String) eventData.get("backImageUrl");
            
            logger.info("Performing OCR processing for deposit: {}", depositId);
            
            // Process front image
            CheckImage frontImage = ocrService.processCheckImage(frontImageUrl, "FRONT", correlationId);
            if (frontImage.getConfidenceScore() < ocrConfidenceThreshold) {
                logger.warn("OCR confidence too low for front image: {}", frontImage.getConfidenceScore());
                auditLogger.logBusinessEvent("OCR_LOW_CONFIDENCE", correlationId,
                    Map.of("depositId", depositId, "imageType", "FRONT", "confidence", frontImage.getConfidenceScore()));
                return false;
            }
            
            // Process back image
            CheckImage backImage = ocrService.processCheckImage(backImageUrl, "BACK", correlationId);
            if (backImage.getConfidenceScore() < ocrConfidenceThreshold) {
                logger.warn("OCR confidence too low for back image: {}", backImage.getConfidenceScore());
                auditLogger.logBusinessEvent("OCR_LOW_CONFIDENCE", correlationId,
                    Map.of("depositId", depositId, "imageType", "BACK", "confidence", backImage.getConfidenceScore()));
                return false;
            }
            
            // Extract and validate check data
            Map<String, Object> extractedData = ocrService.extractCheckData(frontImage, backImage, correlationId);
            
            // Validate extracted data against provided data
            if (!validateExtractedData(eventData, extractedData, correlationId)) {
                logger.warn("Extracted data validation failed for deposit: {}", depositId);
                return false;
            }
            
            // Store OCR results
            eventData.put("ocrFrontImage", frontImage);
            eventData.put("ocrBackImage", backImage);
            eventData.put("extractedData", extractedData);
            
            ocrProcessedCounter.increment();
            logger.info("OCR processing completed successfully for deposit: {}", depositId);
            return true;
            
        } catch (Exception e) {
            logger.error("OCR processing failed", e);
            return false;
        } finally {
            ocrTimer.stop(sample);
        }
    }
    
    private boolean validateExtractedData(Map<String, Object> eventData, Map<String, Object> extractedData, String correlationId) {
        try {
            String depositId = (String) eventData.get("depositId");
            
            // Validate routing number
            String providedRouting = (String) eventData.get("routingNumber");
            String extractedRouting = (String) extractedData.get("routingNumber");
            if (!providedRouting.equals(extractedRouting)) {
                logger.warn("Routing number mismatch for deposit: {} - provided: {}, extracted: {}", 
                    depositId, providedRouting, extractedRouting);
                auditLogger.logSecurityEvent("ROUTING_NUMBER_MISMATCH", correlationId,
                    Map.of("depositId", depositId, "provided", providedRouting, "extracted", extractedRouting));
                return false;
            }
            
            // Validate account number
            String providedAccount = (String) eventData.get("accountNumber");
            String extractedAccount = (String) extractedData.get("accountNumber");
            if (!providedAccount.equals(extractedAccount)) {
                logger.warn("Account number mismatch for deposit: {} - provided: {}, extracted: {}", 
                    depositId, providedAccount, extractedAccount);
                auditLogger.logSecurityEvent("ACCOUNT_NUMBER_MISMATCH", correlationId,
                    Map.of("depositId", depositId, "provided", providedAccount, "extracted", extractedAccount));
                return false;
            }
            
            // Validate amount
            BigDecimal providedAmount = new BigDecimal(eventData.get("depositAmount").toString());
            BigDecimal extractedAmount = new BigDecimal(extractedData.get("amount").toString());
            if (providedAmount.compareTo(extractedAmount) != 0) {
                logger.warn("Amount mismatch for deposit: {} - provided: {}, extracted: {}", 
                    depositId, providedAmount, extractedAmount);
                auditLogger.logSecurityEvent("AMOUNT_MISMATCH", correlationId,
                    Map.of("depositId", depositId, "provided", providedAmount.toString(), "extracted", extractedAmount.toString()));
                return false;
            }
            
            // Validate check number
            String providedCheckNumber = (String) eventData.get("checkNumber");
            String extractedCheckNumber = (String) extractedData.get("checkNumber");
            if (!providedCheckNumber.equals(extractedCheckNumber)) {
                logger.warn("Check number mismatch for deposit: {} - provided: {}, extracted: {}", 
                    depositId, providedCheckNumber, extractedCheckNumber);
                auditLogger.logSecurityEvent("CHECK_NUMBER_MISMATCH", correlationId,
                    Map.of("depositId", depositId, "provided", providedCheckNumber, "extracted", extractedCheckNumber));
                return false;
            }
            
            return true;
        } catch (Exception e) {
            logger.error("Failed to validate extracted data", e);
            return false;
        }
    }
    
    private boolean performDuplicateDetection(Map<String, Object> eventData, String correlationId) {
        try {
            String depositId = (String) eventData.get("depositId");
            String customerId = (String) eventData.get("customerId");
            String checkNumber = (String) eventData.get("checkNumber");
            String routingNumber = (String) eventData.get("routingNumber");
            String accountNumber = (String) eventData.get("accountNumber");
            BigDecimal amount = new BigDecimal(eventData.get("depositAmount").toString());
            
            // Check for duplicate by check number, routing, account, and amount
            boolean isDuplicate = checkProcessingService.isDuplicateCheck(
                checkNumber, routingNumber, accountNumber, amount, 
                Duration.ofHours(duplicateDetectionWindowHours), correlationId);
            
            if (isDuplicate) {
                logger.warn("Duplicate check detected for deposit: {} - check: {}", depositId, checkNumber);
                auditLogger.logSecurityEvent("DUPLICATE_CHECK_DETECTED", correlationId,
                    Map.of("depositId", depositId, "checkNumber", checkNumber, "customerId", customerId));
                return false;
            }
            
            // Check for image similarity
            String frontImageUrl = (String) eventData.get("frontImageUrl");
            boolean isSimilarImage = checkProcessingService.detectSimilarImages(
                frontImageUrl, customerId, Duration.ofHours(duplicateDetectionWindowHours), correlationId);
            
            if (isSimilarImage) {
                logger.warn("Similar check image detected for deposit: {}", depositId);
                auditLogger.logSecurityEvent("SIMILAR_CHECK_IMAGE_DETECTED", correlationId,
                    Map.of("depositId", depositId, "customerId", customerId));
                return false;
            }
            
            // Register check to prevent future duplicates
            checkProcessingService.registerCheckDeposit(depositId, checkNumber, routingNumber, 
                accountNumber, amount, frontImageUrl, correlationId);
            
            logger.info("Duplicate detection passed for deposit: {}", depositId);
            return true;
            
        } catch (Exception e) {
            logger.error("Duplicate detection failed", e);
            return false;
        }
    }
    
    private boolean performFraudDetection(Map<String, Object> eventData, String correlationId) {
        try {
            String depositId = (String) eventData.get("depositId");
            String customerId = (String) eventData.get("customerId");
            String accountId = (String) eventData.get("accountId");
            BigDecimal amount = new BigDecimal(eventData.get("depositAmount").toString());
            String checkNumber = (String) eventData.get("checkNumber");
            
            // Check for suspicious deposit patterns
            if (fraudDetectionEngine.checkSuspiciousDepositPattern(customerId, amount, correlationId)) {
                logger.warn("Suspicious deposit pattern detected for customer: {}", customerId);
                auditLogger.logSecurityEvent("SUSPICIOUS_DEPOSIT_PATTERN", correlationId,
                    Map.of("customerId", customerId, "depositId", depositId, "amount", amount.toString()));
                return false;
            }
            
            // Check for high-risk customer
            if (fraudDetectionEngine.isHighRiskCustomer(customerId, correlationId)) {
                logger.warn("High-risk customer detected: {}", customerId);
                auditLogger.logSecurityEvent("HIGH_RISK_CUSTOMER_CHECK_DEPOSIT", correlationId,
                    Map.of("customerId", customerId, "depositId", depositId));
                return false;
            }
            
            // Check for velocity violations
            if (fraudDetectionEngine.exceedsDepositVelocity(customerId, amount, correlationId)) {
                logger.warn("Deposit velocity exceeded for customer: {}", customerId);
                auditLogger.logSecurityEvent("DEPOSIT_VELOCITY_EXCEEDED", correlationId,
                    Map.of("customerId", customerId, "depositId", depositId, "amount", amount.toString()));
                return false;
            }
            
            // Check for check washing indicators
            if (fraudDetectionEngine.detectCheckWashing(checkNumber, amount, correlationId)) {
                logger.warn("Check washing detected for check: {}", checkNumber);
                auditLogger.logSecurityEvent("CHECK_WASHING_DETECTED", correlationId,
                    Map.of("checkNumber", checkNumber, "depositId", depositId));
                return false;
            }
            
            // Validate check image quality and authenticity
            CheckImage frontImage = (CheckImage) eventData.get("ocrFrontImage");
            if (fraudDetectionEngine.detectImageManipulation(frontImage, correlationId)) {
                logger.warn("Image manipulation detected for deposit: {}", depositId);
                auditLogger.logSecurityEvent("CHECK_IMAGE_MANIPULATION", correlationId,
                    Map.of("depositId", depositId, "customerId", customerId));
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
            String accountId = (String) eventData.get("accountId");
            BigDecimal amount = new BigDecimal(eventData.get("depositAmount").toString());
            String depositId = (String) eventData.get("depositId");
            
            // BSA/AML compliance check
            if (!complianceEngine.validateBSACompliance(customerId, amount, "CHECK_DEPOSIT", correlationId)) {
                logger.warn("BSA/AML compliance violation for customer: {}", customerId);
                auditLogger.logComplianceViolation("BSA_AML_VIOLATION", correlationId,
                    Map.of("customerId", customerId, "violationType", "CHECK_DEPOSIT", "amount", amount.toString()));
                return false;
            }
            
            // OFAC sanctions screening
            if (!complianceEngine.performOFACScreening(customerId, correlationId)) {
                logger.warn("OFAC sanctions match for customer: {}", customerId);
                auditLogger.logComplianceViolation("OFAC_SANCTIONS_MATCH", correlationId,
                    Map.of("customerId", customerId, "screeningType", "CHECK_DEPOSIT"));
                return false;
            }
            
            // Reg CC compliance for funds availability
            if (!complianceEngine.validateRegCCCompliance(customerId, amount, "CHECK_DEPOSIT", correlationId)) {
                logger.warn("Reg CC compliance violation for deposit: {}", depositId);
                auditLogger.logComplianceViolation("REG_CC_VIOLATION", correlationId,
                    Map.of("customerId", customerId, "depositId", depositId, "amount", amount.toString()));
                return false;
            }
            
            // Check for daily deposit limits
            if (!complianceEngine.validateDailyDepositLimits(customerId, amount, correlationId)) {
                logger.warn("Daily deposit limits exceeded for customer: {}", customerId);
                auditLogger.logComplianceViolation("DAILY_DEPOSIT_LIMIT_EXCEEDED", correlationId,
                    Map.of("customerId", customerId, "amount", amount.toString()));
                return false;
            }
            
            return true;
        } catch (Exception e) {
            logger.error("Compliance validation failed", e);
            return false;
        }
    }
    
    private boolean processCheckDepositBusinessLogic(Map<String, Object> eventData, String correlationId) {
        try {
            String depositId = (String) eventData.get("depositId");
            String customerId = (String) eventData.get("customerId");
            String accountId = (String) eventData.get("accountId");
            BigDecimal amount = new BigDecimal(eventData.get("depositAmount").toString());
            
            logger.info("Processing check deposit business logic for: {}", depositId);
            
            // Create check deposit record
            CheckDeposit checkDeposit = new CheckDeposit();
            checkDeposit.setDepositId(depositId);
            checkDeposit.setCustomerId(customerId);
            checkDeposit.setAccountId(accountId);
            checkDeposit.setAmount(amount);
            checkDeposit.setCheckNumber((String) eventData.get("checkNumber"));
            checkDeposit.setRoutingNumber((String) eventData.get("routingNumber"));
            checkDeposit.setAccountNumber((String) eventData.get("accountNumber"));
            checkDeposit.setMemo((String) eventData.get("memo"));
            checkDeposit.setFrontImageUrl((String) eventData.get("frontImageUrl"));
            checkDeposit.setBackImageUrl((String) eventData.get("backImageUrl"));
            checkDeposit.setDepositDate(LocalDateTime.now());
            checkDeposit.setStatus("PENDING");
            checkDeposit.setCorrelationId(correlationId);
            
            // Store OCR results
            CheckImage frontImage = (CheckImage) eventData.get("ocrFrontImage");
            CheckImage backImage = (CheckImage) eventData.get("ocrBackImage");
            checkDeposit.setOcrFrontImage(frontImage);
            checkDeposit.setOcrBackImage(backImage);
            
            // Save deposit record
            checkProcessingService.saveCheckDeposit(checkDeposit);
            
            // Update account pending balance
            checkProcessingService.updatePendingBalance(accountId, amount, "ADD", correlationId);
            
            // Initialize processing state
            CheckDepositProcessingState state = new CheckDepositProcessingState();
            state.setDepositId(depositId);
            state.setStatus("PROCESSING");
            state.setProcessedAt(Instant.now());
            state.setCorrelationId(correlationId);
            processingStates.put(depositId, state);
            
            logger.info("Check deposit business logic processing completed for: {}", depositId);
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to process check deposit business logic", e);
            return false;
        }
    }
    
    private void determineFundsAvailability(Map<String, Object> eventData, String correlationId) {
        try {
            String depositId = (String) eventData.get("depositId");
            String customerId = (String) eventData.get("customerId");
            String accountId = (String) eventData.get("accountId");
            BigDecimal amount = new BigDecimal(eventData.get("depositAmount").toString());
            
            // Determine funds availability based on Reg CC
            Map<String, Object> availabilitySchedule = fundsAvailabilityService.determineFundsAvailability(
                customerId, accountId, amount, "CHECK_DEPOSIT", correlationId);
            
            BigDecimal immediateAvailable = (BigDecimal) availabilitySchedule.get("immediateAvailable");
            BigDecimal nextBusinessDay = (BigDecimal) availabilitySchedule.get("nextBusinessDay");
            BigDecimal secondBusinessDay = (BigDecimal) availabilitySchedule.get("secondBusinessDay");
            LocalDate fullAvailabilityDate = (LocalDate) availabilitySchedule.get("fullAvailabilityDate");
            
            // Update deposit with availability schedule
            checkProcessingService.updateFundsAvailability(depositId, availabilitySchedule, correlationId);
            
            // Make immediate funds available
            if (immediateAvailable.compareTo(BigDecimal.ZERO) > 0) {
                checkProcessingService.makeAvailable(accountId, immediateAvailable, correlationId);
            }
            
            // Schedule future availability
            if (nextBusinessDay.compareTo(BigDecimal.ZERO) > 0) {
                fundsAvailabilityService.scheduleAvailability(accountId, nextBusinessDay, 
                    LocalDate.now().plusDays(1), correlationId);
            }
            
            if (secondBusinessDay.compareTo(BigDecimal.ZERO) > 0) {
                fundsAvailabilityService.scheduleAvailability(accountId, secondBusinessDay, 
                    LocalDate.now().plusDays(2), correlationId);
            }
            
            // Store availability information
            eventData.put("availabilitySchedule", availabilitySchedule);
            
            logger.info("Funds availability determined for deposit: {} - immediate: {}, full date: {}", 
                depositId, immediateAvailable, fullAvailabilityDate);
                
        } catch (Exception e) {
            logger.error("Failed to determine funds availability", e);
            throw new RuntimeException("Funds availability determination failed", e);
        }
    }
    
    private void generateRegulatoryReports(Map<String, Object> eventData, String correlationId) {
        try {
            String depositId = (String) eventData.get("depositId");
            String customerId = (String) eventData.get("customerId");
            BigDecimal amount = new BigDecimal(eventData.get("depositAmount").toString());
            
            // Generate CTR if threshold exceeded
            if (amount.compareTo(new BigDecimal("10000")) >= 0) {
                complianceEngine.generateCTRReport(customerId, amount, "CHECK_DEPOSIT", correlationId);
            }
            
            // Generate suspicious activity report if needed
            if (processingStates.get(depositId) != null && processingStates.get(depositId).isSuspicious()) {
                complianceEngine.generateSARReport(customerId, depositId, "SUSPICIOUS_CHECK_DEPOSIT", correlationId);
            }
            
            // Generate Check 21 report
            complianceEngine.generateCheck21Report(depositId, amount, correlationId);
            
            logger.info("Regulatory reports generated for deposit: {}", depositId);
        } catch (Exception e) {
            logger.error("Failed to generate regulatory reports", e);
            throw new RuntimeException("Regulatory reporting failed", e);
        }
    }
    
    private void sendCustomerNotifications(Map<String, Object> eventData, String correlationId) {
        try {
            String customerId = (String) eventData.get("customerId");
            String depositId = (String) eventData.get("depositId");
            BigDecimal amount = new BigDecimal(eventData.get("depositAmount").toString());
            Map<String, Object> availabilitySchedule = (Map<String, Object>) eventData.get("availabilitySchedule");
            
            // Send deposit receipt notification
            notificationService.sendCheckDepositReceipt(customerId, depositId, amount, correlationId);
            
            // Send funds availability notification
            notificationService.sendFundsAvailabilityNotification(customerId, depositId, 
                availabilitySchedule, correlationId);
            
            // Send hold notification if applicable
            BigDecimal immediateAvailable = (BigDecimal) availabilitySchedule.get("immediateAvailable");
            if (immediateAvailable.compareTo(amount) < 0) {
                notificationService.sendDepositHoldNotification(customerId, depositId, 
                    amount.subtract(immediateAvailable), correlationId);
            }
            
            logger.info("Customer notifications sent for deposit: {}", depositId);
        } catch (Exception e) {
            logger.error("Failed to send customer notifications", e);
            throw new RuntimeException("Customer notification failed", e);
        }
    }
    
    private void createAuditTrail(Map<String, Object> eventData, String correlationId) {
        try {
            String depositId = (String) eventData.get("depositId");
            String customerId = (String) eventData.get("customerId");
            
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("eventType", "CHECK_DEPOSIT_PROCESSED");
            auditData.put("depositId", depositId);
            auditData.put("customerId", customerId);
            auditData.put("processingTimestamp", Instant.now().toString());
            auditData.put("correlationId", correlationId);
            auditData.putAll(eventData);
            
            // Remove sensitive image data from audit
            auditData.remove("ocrFrontImage");
            auditData.remove("ocrBackImage");
            
            auditLogger.logBusinessEvent("CHECK_DEPOSIT_PROCESSED", correlationId, auditData);
            
            logger.info("Audit trail created for deposit: {}", depositId);
        } catch (Exception e) {
            logger.error("Failed to create audit trail", e);
            throw new RuntimeException("Audit trail creation failed", e);
        }
    }
    
    private boolean isValidImageUrl(String imageUrl) {
        return imageUrl != null && 
               (imageUrl.startsWith("https://") || imageUrl.startsWith("http://")) &&
               imageUrl.length() > 10;
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
        logger.info("Shutting down CheckImageDepositEventConsumer...");
        processingStates.clear();
        logger.info("CheckImageDepositEventConsumer shutdown complete");
    }
    
    private static class CheckDepositProcessingState {
        private String depositId;
        private String status;
        private Instant processedAt;
        private String correlationId;
        private boolean suspicious = false;
        
        // Getters and setters
        public String getDepositId() { return depositId; }
        public void setDepositId(String depositId) { this.depositId = depositId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Instant getProcessedAt() { return processedAt; }
        public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
        public boolean isSuspicious() { return suspicious; }
        public void setSuspicious(boolean suspicious) { this.suspicious = suspicious; }
    }
}