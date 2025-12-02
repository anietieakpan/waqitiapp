package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.compliance.ComplianceEngine;
import com.waqiti.common.fraud.FraudDetectionEngine;
import com.waqiti.common.security.AuditLogger;
import com.waqiti.payment.model.Bill;
import com.waqiti.payment.model.BillPresentation;
import com.waqiti.payment.service.BillPresentmentService;
import com.waqiti.payment.service.NotificationService;
import com.waqiti.payment.service.PaymentSchedulingService;
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
 * Consumer #143: Bill Presentment Event Consumer (EBPP)
 * Handles electronic bill presentment and payment processing with compliance validation
 * Implements zero-tolerance 12-step processing pattern with SERIALIZABLE transaction isolation
 */
@Component
public class BillPresentmentEventConsumer {
    private static final Logger logger = LoggerFactory.getLogger(BillPresentmentEventConsumer.class);
    
    private static final String TOPIC = "bill-presentment";
    private static final String DLQ_TOPIC = "bill-presentment-dlq";
    private static final String CONSUMER_GROUP = "bill-presentment-consumer-group";
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Autowired
    private BillPresentmentService billPresentmentService;
    
    @Autowired
    private PaymentSchedulingService paymentSchedulingService;
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private ComplianceEngine complianceEngine;
    
    @Autowired
    private FraudDetectionEngine fraudDetectionEngine;
    
    @Autowired
    private AuditLogger auditLogger;
    
    @Value("${payment.bill.presentment.max-amount:100000}")
    private BigDecimal maxBillAmount;
    
    @Value("${payment.bill.presentment.autopay.enabled:true}")
    private boolean autopayEnabled;
    
    @Value("${payment.bill.presentment.early-payment.days:30}")
    private int earlyPaymentDays;
    
    private Counter billsPresentedCounter;
    private Counter autopaymentsScheduledCounter;
    private Counter fraudDetectedCounter;
    private Counter complianceViolationsCounter;
    private Counter errorCounter;
    private Timer processingTimer;
    
    private CircuitBreaker circuitBreaker;
    private Retry retry;
    
    private final Map<String, BillPresentmentProcessingState> processingStates = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        initializeMetrics();
        initializeResilience();
        logger.info("BillPresentmentEventConsumer initialized with EBPP processing capabilities");
    }
    
    private void initializeMetrics() {
        billsPresentedCounter = Counter.builder("bills.presented")
                .description("Total number of bills presented")
                .register(meterRegistry);
                
        autopaymentsScheduledCounter = Counter.builder("autopayments.scheduled")
                .description("Total number of autopayments scheduled")
                .register(meterRegistry);
                
        fraudDetectedCounter = Counter.builder("bill.presentment.fraud.detected")
                .description("Number of fraudulent bill presentments detected")
                .register(meterRegistry);
                
        complianceViolationsCounter = Counter.builder("bill.presentment.compliance.violations")
                .description("Number of compliance violations in bill presentment")
                .register(meterRegistry);
                
        errorCounter = Counter.builder("bill.presentment.errors")
                .description("Number of bill presentment processing errors")
                .register(meterRegistry);
                
        processingTimer = Timer.builder("bill.presentment.processing.time")
                .description("Time taken to process bill presentments")
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
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("bill-presentment-processor");
        
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(2))
                .retryExceptions(Exception.class)
                .build();
                
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
        retry = retryRegistry.retry("bill-presentment-retry");
    }
    
    @KafkaListener(topics = TOPIC, groupId = CONSUMER_GROUP)
    @Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRED, timeout = 30)
    public void processBillPresentmentEvent(@Payload String message,
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
        MDC.put("processor", "BillPresentmentEventConsumer");
        
        try {
            logger.info("Step 1: Processing bill presentment event - correlation: {}", correlationId);
            
            // Step 2: Parse and validate message structure
            Map<String, Object> eventData = parseAndValidateMessage(message);
            if (eventData == null) {
                sendToDlq(message, "Invalid message structure", correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            String billId = (String) eventData.get("billId");
            String billerId = (String) eventData.get("billerId");
            String customerId = (String) eventData.get("customerId");
            
            MDC.put("bill.id", billId);
            MDC.put("biller.id", billerId);
            MDC.put("customer.id", customerId);
            
            logger.info("Step 2: Message validated - Bill ID: {}, Biller: {}", billId, billerId);
            
            // Step 3: Fraud detection screening
            if (!performFraudDetection(eventData, correlationId)) {
                logger.warn("Step 3: Fraud detected in bill presentment: {}", billId);
                fraudDetectedCounter.increment();
                sendToDlq(message, "Fraud detected", correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            logger.info("Step 3: Fraud detection passed");
            
            // Step 4: Compliance validation
            if (!performComplianceValidation(eventData, correlationId)) {
                logger.warn("Step 4: Compliance violation in bill presentment: {}", billId);
                complianceViolationsCounter.increment();
                sendToDlq(message, "Compliance violation", correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            logger.info("Step 4: Compliance validation passed");
            
            // Step 5: Business logic processing with circuit breaker
            Supplier<Boolean> processor = () -> processBillPresentmentBusinessLogic(eventData, correlationId);
            Boolean result = Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(circuitBreaker, processor)).get();
            
            if (!result) {
                logger.error("Step 5: Business logic processing failed for bill: {}", billId);
                sendToDlq(message, "Business processing failed", correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            logger.info("Step 5: Business logic processing completed");
            
            // Step 6: Autopay scheduling
            processAutopayScheduling(eventData, correlationId);
            logger.info("Step 6: Autopay scheduling completed");
            
            // Step 7: Payment reminder scheduling
            schedulePaymentReminders(eventData, correlationId);
            logger.info("Step 7: Payment reminders scheduled");
            
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
            billsPresentedCounter.increment();
            acknowledgment.acknowledge();
            logger.info("Step 12: Bill presentment processing completed successfully for: {}", billId);
            
        } catch (Exception e) {
            logger.error("Error processing bill presentment event", e);
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
                "billId", "billerId", "customerId", "accountNumber", "billAmount", 
                "dueDate", "billPeriod", "billType", "billDescription"
            );
            
            for (String field : requiredFields) {
                if (!eventData.containsKey(field) || eventData.get(field) == null) {
                    logger.error("Missing required field: {}", field);
                    return null;
                }
            }
            
            // Validate bill amount
            Object amountObj = eventData.get("billAmount");
            if (!(amountObj instanceof Number)) {
                logger.error("Invalid bill amount: {}", amountObj);
                return null;
            }
            
            BigDecimal amount = new BigDecimal(amountObj.toString());
            if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(maxBillAmount) > 0) {
                logger.error("Bill amount out of range: {}", amount);
                return null;
            }
            
            // Validate due date
            String dueDateStr = (String) eventData.get("dueDate");
            try {
                LocalDate.parse(dueDateStr);
            } catch (Exception e) {
                logger.error("Invalid due date format: {}", dueDateStr);
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
            String billId = (String) eventData.get("billId");
            String billerId = (String) eventData.get("billerId");
            String customerId = (String) eventData.get("customerId");
            BigDecimal amount = new BigDecimal(eventData.get("billAmount").toString());
            
            // Check for suspicious biller patterns
            if (fraudDetectionEngine.checkSuspiciousBillerPattern(billerId, correlationId)) {
                logger.warn("Suspicious biller pattern detected: {}", billerId);
                auditLogger.logSecurityEvent("SUSPICIOUS_BILLER_PATTERN", correlationId,
                    Map.of("billerId", billerId, "billId", billId));
                return false;
            }
            
            // Check for bill amount anomalies
            if (fraudDetectionEngine.detectBillAmountAnomaly(customerId, billerId, amount, correlationId)) {
                logger.warn("Bill amount anomaly detected for customer: {} biller: {} amount: {}", 
                    customerId, billerId, amount);
                auditLogger.logSecurityEvent("BILL_AMOUNT_ANOMALY", correlationId,
                    Map.of("customerId", customerId, "billerId", billerId, "amount", amount.toString()));
                return false;
            }
            
            // Check for duplicate bill presentment
            if (fraudDetectionEngine.detectDuplicateBillPresentment(billId, billerId, customerId, correlationId)) {
                logger.warn("Duplicate bill presentment detected: {}", billId);
                auditLogger.logSecurityEvent("DUPLICATE_BILL_PRESENTMENT", correlationId,
                    Map.of("billId", billId, "billerId", billerId, "customerId", customerId));
                return false;
            }
            
            // Check for high-risk customer
            if (fraudDetectionEngine.isHighRiskCustomer(customerId, correlationId)) {
                logger.warn("High-risk customer detected: {}", customerId);
                auditLogger.logSecurityEvent("HIGH_RISK_CUSTOMER_BILL_PRESENTMENT", correlationId,
                    Map.of("customerId", customerId, "billId", billId));
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
            String billerId = (String) eventData.get("billerId");
            BigDecimal amount = new BigDecimal(eventData.get("billAmount").toString());
            String billId = (String) eventData.get("billId");
            
            // BSA/AML compliance check
            if (!complianceEngine.validateBSACompliance(customerId, amount, "BILL_PRESENTMENT", correlationId)) {
                logger.warn("BSA/AML compliance violation for customer: {}", customerId);
                auditLogger.logComplianceViolation("BSA_AML_VIOLATION", correlationId,
                    Map.of("customerId", customerId, "violationType", "BILL_PRESENTMENT"));
                return false;
            }
            
            // OFAC sanctions screening
            if (!complianceEngine.performOFACScreening(customerId, correlationId)) {
                logger.warn("OFAC sanctions match for customer: {}", customerId);
                auditLogger.logComplianceViolation("OFAC_SANCTIONS_MATCH", correlationId,
                    Map.of("customerId", customerId, "screeningType", "BILL_PRESENTMENT"));
                return false;
            }
            
            // Validate biller registration and compliance
            if (!complianceEngine.validateBillerCompliance(billerId, correlationId)) {
                logger.warn("Biller compliance validation failed: {}", billerId);
                auditLogger.logComplianceViolation("BILLER_COMPLIANCE_VIOLATION", correlationId,
                    Map.of("billerId", billerId, "billId", billId));
                return false;
            }
            
            // Check consumer protection regulations
            if (!complianceEngine.validateConsumerProtectionCompliance(customerId, billerId, amount, correlationId)) {
                logger.warn("Consumer protection compliance violation for bill: {}", billId);
                auditLogger.logComplianceViolation("CONSUMER_PROTECTION_VIOLATION", correlationId,
                    Map.of("customerId", customerId, "billerId", billerId, "billId", billId));
                return false;
            }
            
            return true;
        } catch (Exception e) {
            logger.error("Compliance validation failed", e);
            return false;
        }
    }
    
    private boolean processBillPresentmentBusinessLogic(Map<String, Object> eventData, String correlationId) {
        try {
            String billId = (String) eventData.get("billId");
            String billerId = (String) eventData.get("billerId");
            String customerId = (String) eventData.get("customerId");
            
            logger.info("Processing bill presentment business logic for: {}", billId);
            
            // Create bill presentation record
            BillPresentation billPresentation = new BillPresentation();
            billPresentation.setBillId(billId);
            billPresentation.setBillerId(billerId);
            billPresentation.setCustomerId(customerId);
            billPresentation.setAccountNumber((String) eventData.get("accountNumber"));
            billPresentation.setBillAmount(new BigDecimal(eventData.get("billAmount").toString()));
            billPresentation.setDueDate(LocalDate.parse((String) eventData.get("dueDate")));
            billPresentation.setBillPeriod((String) eventData.get("billPeriod"));
            billPresentation.setBillType((String) eventData.get("billType"));
            billPresentation.setBillDescription((String) eventData.get("billDescription"));
            billPresentation.setPresentmentDate(LocalDateTime.now());
            billPresentation.setStatus("PRESENTED");
            billPresentation.setCorrelationId(correlationId);
            
            // Add optional fields
            if (eventData.containsKey("minimumPayment")) {
                billPresentation.setMinimumPayment(new BigDecimal(eventData.get("minimumPayment").toString()));
            }
            if (eventData.containsKey("lateFee")) {
                billPresentation.setLateFee(new BigDecimal(eventData.get("lateFee").toString()));
            }
            if (eventData.containsKey("billDetails")) {
                billPresentation.setBillDetails((Map<String, Object>) eventData.get("billDetails"));
            }
            
            // Save bill presentation
            billPresentmentService.saveBillPresentation(billPresentation);
            
            // Create bill record for customer
            Bill bill = new Bill();
            bill.setBillId(billId);
            bill.setCustomerId(customerId);
            bill.setBillerId(billerId);
            bill.setAmount(billPresentation.getBillAmount());
            bill.setDueDate(billPresentation.getDueDate());
            bill.setStatus("UNPAID");
            bill.setBillType(billPresentation.getBillType());
            bill.setDescription(billPresentation.getBillDescription());
            bill.setCreatedAt(LocalDateTime.now());
            
            billPresentmentService.saveBill(bill);
            
            // Update customer billing history
            billPresentmentService.updateCustomerBillingHistory(customerId, billId, correlationId);
            
            // Initialize processing state
            BillPresentmentProcessingState state = new BillPresentmentProcessingState();
            state.setBillId(billId);
            state.setStatus("PROCESSING");
            state.setProcessedAt(Instant.now());
            state.setCorrelationId(correlationId);
            processingStates.put(billId, state);
            
            logger.info("Bill presentment business logic processing completed for: {}", billId);
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to process bill presentment business logic", e);
            return false;
        }
    }
    
    private void processAutopayScheduling(Map<String, Object> eventData, String correlationId) {
        try {
            if (!autopayEnabled) {
                logger.info("Autopay is disabled, skipping autopay scheduling");
                return;
            }
            
            String billId = (String) eventData.get("billId");
            String customerId = (String) eventData.get("customerId");
            String billerId = (String) eventData.get("billerId");
            BigDecimal amount = new BigDecimal(eventData.get("billAmount").toString());
            LocalDate dueDate = LocalDate.parse((String) eventData.get("dueDate"));
            
            // Check if customer has autopay enabled for this biller
            boolean hasAutopayEnabled = billPresentmentService.hasAutopayEnabled(customerId, billerId, correlationId);
            
            if (hasAutopayEnabled) {
                // Get autopay configuration
                Map<String, Object> autopayConfig = billPresentmentService.getAutopayConfiguration(
                    customerId, billerId, correlationId);
                
                String autopayType = (String) autopayConfig.get("autopayType"); // FULL, MINIMUM, FIXED
                BigDecimal autopayAmount = determineAutopayAmount(autopayType, amount, 
                    eventData, autopayConfig);
                
                LocalDate paymentDate = determineAutopayDate(dueDate, autopayConfig);
                
                // Schedule autopayment
                String autopaymentId = paymentSchedulingService.scheduleAutopayment(
                    customerId, billId, billerId, autopayAmount, paymentDate, correlationId);
                
                // Update bill with autopay information
                billPresentmentService.updateBillAutopayStatus(billId, autopaymentId, 
                    autopayAmount, paymentDate, correlationId);
                
                autopaymentsScheduledCounter.increment();
                logger.info("Autopayment scheduled for bill: {} amount: {} date: {}", 
                    billId, autopayAmount, paymentDate);
            }
            
        } catch (Exception e) {
            logger.error("Failed to process autopay scheduling", e);
            throw new RuntimeException("Autopay scheduling failed", e);
        }
    }
    
    private BigDecimal determineAutopayAmount(String autopayType, BigDecimal billAmount, 
                                            Map<String, Object> eventData, Map<String, Object> autopayConfig) {
        switch (autopayType) {
            case "FULL":
                return billAmount;
            case "MINIMUM":
                if (eventData.containsKey("minimumPayment")) {
                    return new BigDecimal(eventData.get("minimumPayment").toString());
                }
                return billAmount.multiply(new BigDecimal("0.1")); // Default 10% minimum
            case "FIXED":
                return new BigDecimal(autopayConfig.get("fixedAmount").toString());
            default:
                return billAmount;
        }
    }
    
    private LocalDate determineAutopayDate(LocalDate dueDate, Map<String, Object> autopayConfig) {
        int daysBefore = (Integer) autopayConfig.getOrDefault("daysBefore", 2);
        return dueDate.minusDays(daysBefore);
    }
    
    private void schedulePaymentReminders(Map<String, Object> eventData, String correlationId) {
        try {
            String billId = (String) eventData.get("billId");
            String customerId = (String) eventData.get("customerId");
            LocalDate dueDate = LocalDate.parse((String) eventData.get("dueDate"));
            
            // Get customer reminder preferences
            Map<String, Object> reminderPreferences = billPresentmentService.getReminderPreferences(
                customerId, correlationId);
            
            if ((Boolean) reminderPreferences.getOrDefault("remindersEnabled", true)) {
                // Schedule first reminder (7 days before due date)
                LocalDate firstReminderDate = dueDate.minusDays(7);
                if (firstReminderDate.isAfter(LocalDate.now())) {
                    notificationService.schedulePaymentReminder(customerId, billId, 
                        firstReminderDate, "FIRST_REMINDER", correlationId);
                }
                
                // Schedule second reminder (3 days before due date)
                LocalDate secondReminderDate = dueDate.minusDays(3);
                if (secondReminderDate.isAfter(LocalDate.now())) {
                    notificationService.schedulePaymentReminder(customerId, billId, 
                        secondReminderDate, "SECOND_REMINDER", correlationId);
                }
                
                // Schedule due date reminder
                if (dueDate.isAfter(LocalDate.now())) {
                    notificationService.schedulePaymentReminder(customerId, billId, 
                        dueDate, "DUE_DATE_REMINDER", correlationId);
                }
                
                // Schedule overdue reminder (1 day after due date)
                LocalDate overdueReminderDate = dueDate.plusDays(1);
                notificationService.schedulePaymentReminder(customerId, billId, 
                    overdueReminderDate, "OVERDUE_REMINDER", correlationId);
            }
            
            logger.info("Payment reminders scheduled for bill: {}", billId);
        } catch (Exception e) {
            logger.error("Failed to schedule payment reminders", e);
            throw new RuntimeException("Payment reminder scheduling failed", e);
        }
    }
    
    private void generateRegulatoryReports(Map<String, Object> eventData, String correlationId) {
        try {
            String billId = (String) eventData.get("billId");
            String customerId = (String) eventData.get("customerId");
            String billerId = (String) eventData.get("billerId");
            BigDecimal amount = new BigDecimal(eventData.get("billAmount").toString());
            
            // Generate consumer protection report
            complianceEngine.generateConsumerProtectionReport(customerId, billerId, 
                billId, amount, correlationId);
            
            // Generate EBPP compliance report
            complianceEngine.generateEBPPComplianceReport(billerId, billId, 
                amount, correlationId);
            
            // Generate CTR if threshold exceeded
            if (amount.compareTo(new BigDecimal("10000")) >= 0) {
                complianceEngine.generateCTRReport(customerId, amount, "BILL_PAYMENT", correlationId);
            }
            
            logger.info("Regulatory reports generated for bill: {}", billId);
        } catch (Exception e) {
            logger.error("Failed to generate regulatory reports", e);
            throw new RuntimeException("Regulatory reporting failed", e);
        }
    }
    
    private void sendCustomerNotifications(Map<String, Object> eventData, String correlationId) {
        try {
            String customerId = (String) eventData.get("customerId");
            String billId = (String) eventData.get("billId");
            String billerId = (String) eventData.get("billerId");
            BigDecimal amount = new BigDecimal(eventData.get("billAmount").toString());
            LocalDate dueDate = LocalDate.parse((String) eventData.get("dueDate"));
            
            // Send bill presentment notification
            notificationService.sendBillPresentmentNotification(customerId, billId, 
                billerId, amount, dueDate, correlationId);
            
            // Send autopay confirmation if applicable
            BillPresentmentProcessingState state = processingStates.get(billId);
            if (state != null && state.hasAutopayScheduled()) {
                notificationService.sendAutopayConfirmationNotification(customerId, 
                    billId, state.getAutopayAmount(), state.getAutopayDate(), correlationId);
            }
            
            // Send early payment discount notification if applicable
            if (eventData.containsKey("earlyPaymentDiscount")) {
                BigDecimal discount = new BigDecimal(eventData.get("earlyPaymentDiscount").toString());
                LocalDate discountDeadline = dueDate.minusDays(earlyPaymentDays);
                notificationService.sendEarlyPaymentDiscountNotification(customerId, 
                    billId, discount, discountDeadline, correlationId);
            }
            
            logger.info("Customer notifications sent for bill: {}", billId);
        } catch (Exception e) {
            logger.error("Failed to send customer notifications", e);
            throw new RuntimeException("Customer notification failed", e);
        }
    }
    
    private void createAuditTrail(Map<String, Object> eventData, String correlationId) {
        try {
            String billId = (String) eventData.get("billId");
            String customerId = (String) eventData.get("customerId");
            String billerId = (String) eventData.get("billerId");
            
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("eventType", "BILL_PRESENTED");
            auditData.put("billId", billId);
            auditData.put("customerId", customerId);
            auditData.put("billerId", billerId);
            auditData.put("processingTimestamp", Instant.now().toString());
            auditData.put("correlationId", correlationId);
            auditData.putAll(eventData);
            
            auditLogger.logBusinessEvent("BILL_PRESENTED", correlationId, auditData);
            
            logger.info("Audit trail created for bill: {}", billId);
        } catch (Exception e) {
            logger.error("Failed to create audit trail", e);
            throw new RuntimeException("Audit trail creation failed", e);
        }
    }
    
    private void updateCacheState(Map<String, Object> eventData, String correlationId) {
        try {
            String billId = (String) eventData.get("billId");
            String customerId = (String) eventData.get("customerId");
            String billerId = (String) eventData.get("billerId");
            
            // Update bill presentation cache
            String billCacheKey = "bill:presentation:" + billId;
            redisTemplate.opsForValue().set(billCacheKey, "PRESENTED", Duration.ofDays(90));
            
            // Update customer bills cache
            String customerBillsKey = "customer:bills:" + customerId;
            redisTemplate.opsForSet().add(customerBillsKey, billId);
            redisTemplate.expire(customerBillsKey, Duration.ofDays(365));
            
            // Update biller activity cache
            String billerActivityKey = "biller:activity:" + billerId;
            redisTemplate.opsForHash().put(billerActivityKey, "lastBillPresented", Instant.now().toString());
            redisTemplate.expire(billerActivityKey, Duration.ofDays(30));
            
            logger.info("Cache state updated for bill: {}", billId);
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
        logger.info("Shutting down BillPresentmentEventConsumer...");
        processingStates.clear();
        logger.info("BillPresentmentEventConsumer shutdown complete");
    }
    
    private static class BillPresentmentProcessingState {
        private String billId;
        private String status;
        private Instant processedAt;
        private String correlationId;
        private boolean autopayScheduled = false;
        private BigDecimal autopayAmount;
        private LocalDate autopayDate;
        
        // Getters and setters
        public String getBillId() { return billId; }
        public void setBillId(String billId) { this.billId = billId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Instant getProcessedAt() { return processedAt; }
        public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
        public boolean hasAutopayScheduled() { return autopayScheduled; }
        public void setAutopayScheduled(boolean autopayScheduled) { this.autopayScheduled = autopayScheduled; }
        public BigDecimal getAutopayAmount() { return autopayAmount; }
        public void setAutopayAmount(BigDecimal autopayAmount) { this.autopayAmount = autopayAmount; }
        public LocalDate getAutopayDate() { return autopayDate; }
        public void setAutopayDate(LocalDate autopayDate) { this.autopayDate = autopayDate; }
    }
}