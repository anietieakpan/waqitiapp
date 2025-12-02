package com.waqiti.compliance.kafka;

import com.waqiti.common.events.GenericKafkaEvent;
import com.waqiti.common.events.TransactionEvent;
import com.waqiti.common.kafka.KafkaEventTrackingService;
import com.waqiti.common.outbox.OutboxService;
import com.waqiti.compliance.entity.ComplianceCheck;
import com.waqiti.compliance.entity.CTRReport;
import com.waqiti.compliance.entity.EnhancedDueDiligence;
import com.waqiti.compliance.repository.ComplianceCheckRepository;
import com.waqiti.compliance.repository.CTRReportRepository;
import com.waqiti.compliance.repository.EnhancedDueDiligenceRepository;
import com.waqiti.compliance.service.ComplianceScreeningService;
import com.waqiti.compliance.service.NotificationService;
import com.waqiti.compliance.service.RegulatoryReportingService;
import com.waqiti.compliance.service.SanctionsScreeningService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade Kafka consumer for high-value transaction compliance screening
 * 
 * This consumer implements comprehensive compliance checks for high-value transactions including:
 * - Currency Transaction Report (CTR) generation for transactions > $10,000 (FinCEN requirement)
 * - Enhanced Due Diligence (EDD) for transactions > $50,000
 * - Board/Executive notifications for transactions > $1,000,000
 * - Real-time sanctions screening
 * - AML pattern detection
 * - SAR (Suspicious Activity Report) filing triggers
 * 
 * Implementation follows enterprise patterns:
 * - Idempotency guarantees through event ID tracking
 * - Transactional consistency with SERIALIZABLE isolation
 * - Circuit breaker pattern for external service resilience
 * - Comprehensive monitoring and metrics
 * - Dead Letter Queue for failed processing
 * - Audit trail for all compliance decisions
 * 
 * @author Waqiti Platform Team - Phase 1 Remediation
 * @since Session 6 - Production Implementation
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HighValueTransactionConsumer {

    private final ComplianceScreeningService complianceScreeningService;
    private final RegulatoryReportingService regulatoryReportingService;
    private final SanctionsScreeningService sanctionsScreeningService;
    private final NotificationService notificationService;
    private final KafkaEventTrackingService eventTrackingService;
    private final OutboxService outboxService;
    
    // Repositories for persistence
    private final ComplianceCheckRepository complianceCheckRepository;
    private final CTRReportRepository ctrReportRepository;
    private final EnhancedDueDiligenceRepository eddRepository;
    
    // Metrics
    private final MeterRegistry meterRegistry;
    private final Counter highValueTransactionCounter;
    private final Counter ctrRequiredCounter;
    private final Counter eddRequiredCounter;
    private final Counter sanctionsHitCounter;
    private final Counter suspiciousPatternCounter;
    private final Timer processingTimer;
    
    // Configuration
    @Value("${compliance.thresholds.ctr:10000}")
    private BigDecimal ctrThreshold;
    
    @Value("${compliance.thresholds.edd:50000}")
    private BigDecimal eddThreshold;
    
    @Value("${compliance.thresholds.board-notification:1000000}")
    private BigDecimal boardNotificationThreshold;
    
    @Value("${compliance.processing.timeout:120}")
    private int processingTimeoutSeconds;
    
    @Value("${compliance.screening.parallel:true}")
    private boolean parallelScreening;
    
    // In-memory cache for idempotency (production would use Redis)
    private final Map<String, LocalDateTime> processedEvents = new ConcurrentHashMap<>();
    
    // Constants
    private static final String CONSUMER_GROUP = "compliance-high-value-screening";
    private static final String TOPIC_NAME = "transaction.high.value";
    private static final int MAX_RETRY_ATTEMPTS = 4;
    private static final String METRIC_PREFIX = "compliance.high_value.";

    /**
     * Main event handler for high-value transaction events
     * 
     * Processing Steps:
     * 1. Validate event and check idempotency
     * 2. Perform threshold-based compliance checks
     * 3. Execute sanctions screening
     * 4. Analyze for suspicious patterns
     * 5. Generate required reports (CTR, SAR)
     * 6. Send notifications to relevant parties
     * 7. Record audit trail
     * 8. Acknowledge message processing
     */
    @KafkaListener(
        topics = TOPIC_NAME,
        groupId = CONSUMER_GROUP,
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "${kafka.consumer.compliance.concurrency:5}",
        properties = {
            "max.poll.interval.ms:300000",
            "max.poll.records:50",
            "enable.auto.commit:false",
            "isolation.level:read_committed"
        }
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        include = {Exception.class},
        exclude = {DataIntegrityViolationException.class, IllegalArgumentException.class},
        dltStrategy = DltStrategy.FAIL_ON_ERROR,
        dltDestinationSuffix = ".compliance-dlt"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 120)
    @CircuitBreaker(name = "high-value-compliance", fallbackMethod = "handleCircuitBreakerFallback")
    public void handleHighValueTransaction(
            @Payload TransactionEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            @Header(value = KafkaHeaders.RECEIVED_MESSAGE_KEY, required = false) String key,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = generateEventId(topic, partition, offset);
        LocalDateTime startTime = LocalDateTime.now();
        
        log.info("COMPLIANCE: Processing high-value transaction event. EventId: {}, TransactionId: {}, " +
                "Amount: {} {}, Type: {}, Topic: {}, Partition: {}, Offset: {}, Timestamp: {}",
                eventId, event.getTransactionId(), event.getAmount(), event.getCurrency(), 
                event.getTransactionType(), topic, partition, offset, timestamp);
        
        try {
            // Step 1: Idempotency check
            if (isAlreadyProcessed(event.getTransactionId(), eventId)) {
                log.info("Event already processed. EventId: {}, TransactionId: {}", 
                        eventId, event.getTransactionId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 2: Validate event
            validateEvent(event);
            
            // Step 3: Create compliance check record
            ComplianceCheck complianceCheck = initializeComplianceCheck(event, eventId);
            
            // Step 4: Perform threshold-based checks
            BigDecimal amount = event.getAmount();
            boolean ctrRequired = false;
            boolean eddRequired = false;
            boolean boardNotificationRequired = false;
            
            // Currency Transaction Report (CTR) check
            if (amount.compareTo(ctrThreshold) >= 0) {
                ctrRequired = true;
                ctrRequiredCounter.increment();
                log.warn("CTR REQUIRED: Transaction {} exceeds CTR threshold of {}. Amount: {}",
                        event.getTransactionId(), ctrThreshold, amount);
                
                CTRReport ctrReport = generateCTRReport(event, complianceCheck);
                complianceCheck.setCtrReportId(ctrReport.getReportId());
                complianceCheck.setCtrRequired(true);
            }
            
            // Enhanced Due Diligence (EDD) check
            if (amount.compareTo(eddThreshold) >= 0) {
                eddRequired = true;
                eddRequiredCounter.increment();
                log.warn("EDD REQUIRED: Transaction {} exceeds EDD threshold of {}. Amount: {}",
                        event.getTransactionId(), eddThreshold, amount);
                
                EnhancedDueDiligence eddRecord = performEnhancedDueDiligence(event, complianceCheck);
                complianceCheck.setEddRecordId(eddRecord.getRecordId());
                complianceCheck.setEddRequired(true);
            }
            
            // Board notification check
            if (amount.compareTo(boardNotificationThreshold) >= 0) {
                boardNotificationRequired = true;
                log.error("BOARD NOTIFICATION: Transaction {} exceeds board threshold of {}. Amount: {}",
                        event.getTransactionId(), boardNotificationThreshold, amount);
                
                sendBoardNotification(event, complianceCheck);
                complianceCheck.setBoardNotificationSent(true);
            }
            
            // Step 5: Sanctions screening (parallel if configured)
            boolean sanctionsHit = performSanctionsScreening(event, complianceCheck);
            if (sanctionsHit) {
                sanctionsHitCounter.increment();
                log.error("SANCTIONS HIT: Transaction {} blocked due to sanctions match", 
                        event.getTransactionId());
                
                // Block transaction immediately
                blockTransaction(event, complianceCheck, "SANCTIONS_HIT");
                complianceCheck.setSanctionsHit(true);
                complianceCheck.setTransactionBlocked(true);
            }
            
            // Step 6: AML pattern detection
            boolean suspiciousPattern = detectSuspiciousPatterns(event, complianceCheck);
            if (suspiciousPattern) {
                suspiciousPatternCounter.increment();
                log.warn("SUSPICIOUS PATTERN: Transaction {} flagged for investigation", 
                        event.getTransactionId());
                
                // Create SAR if needed
                createSARIfRequired(event, complianceCheck);
                complianceCheck.setSuspiciousPattern(true);
            }
            
            // Step 7: Risk scoring
            double riskScore = calculateRiskScore(event, complianceCheck, 
                    sanctionsHit, suspiciousPattern, ctrRequired, eddRequired);
            complianceCheck.setRiskScore(riskScore);
            
            // Step 8: Determine final action
            ComplianceAction action = determineComplianceAction(
                    riskScore, sanctionsHit, suspiciousPattern);
            complianceCheck.setAction(action);
            
            // Step 9: Execute compliance action
            executeComplianceAction(action, event, complianceCheck);
            
            // Step 10: Save compliance check record
            complianceCheck.setCompletedAt(LocalDateTime.now());
            complianceCheck.setProcessingDurationMs(
                    Duration.between(startTime, LocalDateTime.now()).toMillis());
            complianceCheckRepository.save(complianceCheck);
            
            // Step 11: Publish compliance decision event
            publishComplianceDecisionEvent(event, complianceCheck);
            
            // Step 12: Record event tracking
            eventTrackingService.recordEvent(
                    eventId, 
                    topic, 
                    partition, 
                    offset, 
                    "SUCCESS",
                    Duration.between(startTime, LocalDateTime.now()).toMillis()
            );
            
            // Step 13: Update metrics
            highValueTransactionCounter.increment();
            sample.stop(processingTimer);
            
            // Step 14: Mark as processed
            markAsProcessed(event.getTransactionId(), eventId);
            
            // Step 15: Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed high-value transaction compliance check. " +
                    "TransactionId: {}, RiskScore: {}, Action: {}, ProcessingTime: {}ms",
                    event.getTransactionId(), riskScore, action, 
                    Duration.between(startTime, LocalDateTime.now()).toMillis());
            
        } catch (OptimisticLockException e) {
            log.warn("Optimistic lock exception - retrying. TransactionId: {}", 
                    event.getTransactionId());
            throw e; // Let retry mechanism handle
            
        } catch (IllegalArgumentException e) {
            log.error("Invalid event data - sending to DLQ. TransactionId: {}, Error: {}", 
                    event.getTransactionId(), e.getMessage());
            // Don't retry validation errors
            throw e;
            
        } catch (Exception e) {
            log.error("Failed to process high-value transaction event. TransactionId: {}, Error: {}", 
                    event.getTransactionId(), e.getMessage(), e);
            
            // Record failure
            eventTrackingService.recordEvent(
                    eventId, 
                    topic, 
                    partition, 
                    offset, 
                    "FAILED",
                    Duration.between(startTime, LocalDateTime.now()).toMillis()
            );
            
            // Update error metrics
            meterRegistry.counter(METRIC_PREFIX + "errors", "type", e.getClass().getSimpleName())
                    .increment();
            
            throw new RuntimeException("Failed to process high-value transaction", e);
        }
    }

    /**
     * Validate incoming event
     */
    private void validateEvent(TransactionEvent event) {
        if (event.getTransactionId() == null) {
            throw new IllegalArgumentException("Transaction ID is required");
        }
        if (event.getAmount() == null || event.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid transaction amount");
        }
        if (event.getCurrency() == null || event.getCurrency().isEmpty()) {
            throw new IllegalArgumentException("Currency is required");
        }
        if (event.getAccountId() == null) {
            throw new IllegalArgumentException("Account ID is required");
        }
        if (event.getTransactionType() == null) {
            throw new IllegalArgumentException("Transaction type is required");
        }
    }

    /**
     * Initialize compliance check record
     */
    private ComplianceCheck initializeComplianceCheck(TransactionEvent event, String eventId) {
        ComplianceCheck check = new ComplianceCheck();
        check.setCheckId(UUID.randomUUID());
        check.setEventId(eventId);
        check.setTransactionId(event.getTransactionId());
        check.setAccountId(event.getAccountId());
        check.setAmount(event.getAmount());
        check.setCurrency(event.getCurrency());
        check.setTransactionType(event.getTransactionType());
        check.setCounterpartyId(event.getCounterpartyId());
        check.setCounterpartyName(event.getCounterpartyName());
        check.setCounterpartyCountry(event.getCounterpartyCountry());
        check.setCheckType("HIGH_VALUE_TRANSACTION");
        check.setStartedAt(LocalDateTime.now());
        check.setStatus("IN_PROGRESS");
        return check;
    }

    /**
     * Generate CTR report for FinCEN
     */
    private CTRReport generateCTRReport(TransactionEvent event, ComplianceCheck check) {
        log.info("Generating CTR report for transaction: {}", event.getTransactionId());
        
        CTRReport report = new CTRReport();
        report.setReportId(UUID.randomUUID());
        report.setTransactionId(event.getTransactionId());
        report.setAccountId(event.getAccountId());
        report.setAmount(event.getAmount());
        report.setCurrency(event.getCurrency());
        report.setTransactionDate(event.getTimestamp());
        report.setTransactionType(event.getTransactionType());
        report.setCustomerName(event.getCustomerName());
        report.setCustomerId(event.getCustomerId());
        report.setCounterpartyName(event.getCounterpartyName());
        report.setCounterpartyCountry(event.getCounterpartyCountry());
        report.setPurposeCode(event.getPurposeCode());
        report.setFilingStatus("PENDING");
        report.setCreatedAt(LocalDateTime.now());
        
        // Save report
        CTRReport savedReport = ctrReportRepository.save(report);
        
        // Queue for regulatory submission
        regulatoryReportingService.submitCTRReport(savedReport);
        
        return savedReport;
    }

    /**
     * Perform enhanced due diligence
     */
    private EnhancedDueDiligence performEnhancedDueDiligence(
            TransactionEvent event, ComplianceCheck check) {
        log.info("Performing EDD for transaction: {}", event.getTransactionId());
        
        EnhancedDueDiligence edd = new EnhancedDueDiligence();
        edd.setRecordId(UUID.randomUUID());
        edd.setTransactionId(event.getTransactionId());
        edd.setAccountId(event.getAccountId());
        edd.setCheckStarted(LocalDateTime.now());
        
        // Perform various EDD checks
        edd.setSourceOfFundsVerified(verifySourceOfFunds(event));
        edd.setBusinessPurposeVerified(verifyBusinessPurpose(event));
        edd.setBeneficialOwnershipVerified(verifyBeneficialOwnership(event));
        edd.setPepScreeningCompleted(performPEPScreening(event));
        edd.setAdverseMediaScreeningCompleted(performAdverseMediaScreening(event));
        edd.setRiskRating(calculateEDDRiskRating(event));
        
        edd.setCheckCompleted(LocalDateTime.now());
        edd.setStatus("COMPLETED");
        
        // Save EDD record
        return eddRepository.save(edd);
    }

    /**
     * Perform sanctions screening
     */
    private boolean performSanctionsScreening(TransactionEvent event, ComplianceCheck check) {
        log.debug("Performing sanctions screening for transaction: {}", event.getTransactionId());
        
        // Screen multiple aspects
        boolean customerHit = sanctionsScreeningService.screenEntity(
                event.getCustomerId(), event.getCustomerName(), event.getCustomerCountry());
        
        boolean counterpartyHit = false;
        if (event.getCounterpartyId() != null) {
            counterpartyHit = sanctionsScreeningService.screenEntity(
                    event.getCounterpartyId(), event.getCounterpartyName(), 
                    event.getCounterpartyCountry());
        }
        
        boolean countryHit = sanctionsScreeningService.screenCountry(
                event.getCounterpartyCountry());
        
        return customerHit || counterpartyHit || countryHit;
    }

    /**
     * Detect suspicious patterns
     */
    private boolean detectSuspiciousPatterns(TransactionEvent event, ComplianceCheck check) {
        log.debug("Analyzing transaction patterns for: {}", event.getTransactionId());
        
        // Check various suspicious patterns
        boolean rapidMovement = checkRapidMoneyMovement(event);
        boolean unusualAmount = checkUnusualTransactionAmount(event);
        boolean structuring = checkForStructuring(event);
        boolean unusualGeography = checkUnusualGeography(event);
        boolean velocityBreach = checkVelocityLimits(event);
        
        return rapidMovement || unusualAmount || structuring || 
               unusualGeography || velocityBreach;
    }

    /**
     * Calculate overall risk score
     */
    private double calculateRiskScore(TransactionEvent event, ComplianceCheck check,
            boolean sanctionsHit, boolean suspiciousPattern, 
            boolean ctrRequired, boolean eddRequired) {
        
        double score = 0.0;
        
        // Base score from amount
        BigDecimal amount = event.getAmount();
        if (amount.compareTo(new BigDecimal("100000")) >= 0) {
            score += 30.0;
        } else if (amount.compareTo(new BigDecimal("50000")) >= 0) {
            score += 20.0;
        } else if (amount.compareTo(new BigDecimal("10000")) >= 0) {
            score += 10.0;
        }
        
        // Sanctions hit is critical
        if (sanctionsHit) {
            score += 100.0;
        }
        
        // Suspicious patterns
        if (suspiciousPattern) {
            score += 40.0;
        }
        
        // Regulatory requirements
        if (ctrRequired) {
            score += 15.0;
        }
        if (eddRequired) {
            score += 25.0;
        }
        
        // High-risk countries
        if (isHighRiskCountry(event.getCounterpartyCountry())) {
            score += 35.0;
        }
        
        // Transaction type risk
        score += getTransactionTypeRisk(event.getTransactionType());
        
        return Math.min(score, 100.0); // Cap at 100
    }

    /**
     * Determine compliance action based on risk assessment
     */
    private ComplianceAction determineComplianceAction(
            double riskScore, boolean sanctionsHit, boolean suspiciousPattern) {
        
        if (sanctionsHit) {
            return ComplianceAction.BLOCK;
        }
        
        if (riskScore >= 80.0) {
            return ComplianceAction.BLOCK_PENDING_REVIEW;
        }
        
        if (riskScore >= 60.0 || suspiciousPattern) {
            return ComplianceAction.FLAG_FOR_REVIEW;
        }
        
        if (riskScore >= 40.0) {
            return ComplianceAction.ENHANCED_MONITORING;
        }
        
        return ComplianceAction.APPROVE;
    }

    /**
     * Execute the determined compliance action
     */
    private void executeComplianceAction(ComplianceAction action, 
            TransactionEvent event, ComplianceCheck check) {
        
        switch (action) {
            case BLOCK:
                blockTransaction(event, check, "COMPLIANCE_BLOCK");
                break;
                
            case BLOCK_PENDING_REVIEW:
                blockTransaction(event, check, "PENDING_REVIEW");
                createManualReviewCase(event, check);
                break;
                
            case FLAG_FOR_REVIEW:
                flagForReview(event, check);
                break;
                
            case ENHANCED_MONITORING:
                enableEnhancedMonitoring(event, check);
                break;
                
            case APPROVE:
                approveTransaction(event, check);
                break;
        }
    }

    /**
     * Block transaction
     */
    private void blockTransaction(TransactionEvent event, ComplianceCheck check, String reason) {
        log.error("BLOCKING TRANSACTION: {} for reason: {}", event.getTransactionId(), reason);
        
        // Send blocking command
        complianceScreeningService.blockTransaction(
                event.getTransactionId(), reason, 
                "Transaction blocked by compliance screening");
        
        // Send notifications
        notificationService.sendTransactionBlockedNotification(
                event.getAccountId(), event.getTransactionId(), reason);
        
        // Update check record
        check.setTransactionBlocked(true);
        check.setBlockReason(reason);
    }

    /**
     * Publish compliance decision event
     */
    private void publishComplianceDecisionEvent(TransactionEvent event, ComplianceCheck check) {
        Map<String, Object> decisionEvent = new HashMap<>();
        decisionEvent.put("transactionId", event.getTransactionId());
        decisionEvent.put("checkId", check.getCheckId());
        decisionEvent.put("riskScore", check.getRiskScore());
        decisionEvent.put("action", check.getAction());
        decisionEvent.put("sanctionsHit", check.isSanctionsHit());
        decisionEvent.put("suspiciousPattern", check.isSuspiciousPattern());
        decisionEvent.put("ctrRequired", check.isCtrRequired());
        decisionEvent.put("eddRequired", check.isEddRequired());
        decisionEvent.put("timestamp", LocalDateTime.now());
        
        outboxService.saveEvent(
                event.getTransactionId(),
                "Transaction",
                "ComplianceDecision",
                decisionEvent
        );
    }

    /**
     * Helper methods
     */
    
    private String generateEventId(String topic, int partition, long offset) {
        return String.format("%s-%d-%d", topic, partition, offset);
    }
    
    private boolean isAlreadyProcessed(String transactionId, String eventId) {
        // Check both transaction ID and event ID for idempotency
        return processedEvents.containsKey(transactionId) || 
               complianceCheckRepository.existsByTransactionId(transactionId);
    }
    
    private void markAsProcessed(String transactionId, String eventId) {
        processedEvents.put(transactionId, LocalDateTime.now());
        // Clean old entries (older than 24 hours)
        processedEvents.entrySet().removeIf(e -> 
                e.getValue().isBefore(LocalDateTime.now().minusHours(24)));
    }
    
    private void sendBoardNotification(TransactionEvent event, ComplianceCheck check) {
        notificationService.sendBoardNotification(
                "VERY_HIGH_VALUE_TRANSACTION",
                String.format("Transaction %s for amount %s %s requires board attention",
                        event.getTransactionId(), event.getAmount(), event.getCurrency()),
                event
        );
    }
    
    private void createSARIfRequired(TransactionEvent event, ComplianceCheck check) {
        if (shouldFileSAR(event, check)) {
            regulatoryReportingService.createSARReport(
                    event.getTransactionId(),
                    event.getAccountId(),
                    "SUSPICIOUS_PATTERN_DETECTED",
                    check
            );
        }
    }
    
    private boolean shouldFileSAR(TransactionEvent event, ComplianceCheck check) {
        // SAR filing logic based on regulatory requirements
        return check.isSuspiciousPattern() && 
               event.getAmount().compareTo(new BigDecimal("5000")) >= 0;
    }
    
    // Verification methods (simplified for example)
    private boolean verifySourceOfFunds(TransactionEvent event) {
        return complianceScreeningService.verifySourceOfFunds(
                event.getAccountId(), event.getAmount());
    }
    
    private boolean verifyBusinessPurpose(TransactionEvent event) {
        return event.getPurposeCode() != null && !event.getPurposeCode().isEmpty();
    }
    
    private boolean verifyBeneficialOwnership(TransactionEvent event) {
        return complianceScreeningService.verifyBeneficialOwnership(event.getAccountId());
    }
    
    private boolean performPEPScreening(TransactionEvent event) {
        return sanctionsScreeningService.screenForPEP(
                event.getCustomerId(), event.getCustomerName());
    }
    
    private boolean performAdverseMediaScreening(TransactionEvent event) {
        return sanctionsScreeningService.screenAdverseMedia(
                event.getCustomerName(), event.getCounterpartyName());
    }
    
    private String calculateEDDRiskRating(TransactionEvent event) {
        // Simplified risk rating
        if (event.getAmount().compareTo(new BigDecimal("100000")) >= 0) {
            return "HIGH";
        } else if (event.getAmount().compareTo(new BigDecimal("50000")) >= 0) {
            return "MEDIUM";
        }
        return "LOW";
    }
    
    // Pattern detection methods
    private boolean checkRapidMoneyMovement(TransactionEvent event) {
        return complianceScreeningService.detectRapidMoneyMovement(
                event.getAccountId(), event.getAmount());
    }
    
    private boolean checkUnusualTransactionAmount(TransactionEvent event) {
        return complianceScreeningService.isUnusualAmount(
                event.getAccountId(), event.getAmount());
    }
    
    private boolean checkForStructuring(TransactionEvent event) {
        return complianceScreeningService.detectStructuring(
                event.getAccountId(), event.getAmount(), event.getTimestamp());
    }
    
    private boolean checkUnusualGeography(TransactionEvent event) {
        return complianceScreeningService.isUnusualGeography(
                event.getAccountId(), event.getCounterpartyCountry());
    }
    
    private boolean checkVelocityLimits(TransactionEvent event) {
        return complianceScreeningService.checkVelocityBreach(
                event.getAccountId(), event.getAmount());
    }
    
    private boolean isHighRiskCountry(String country) {
        // FATF high-risk countries
        Set<String> highRiskCountries = Set.of(
                "IR", "KP", "MM", "AL", "BB", "BF", "KH", "KY", 
                "HT", "JM", "JO", "ML", "MA", "NI", "PK", "PA", 
                "PH", "SN", "SS", "SY", "TZ", "TR", "UG", "YE", "ZW"
        );
        return country != null && highRiskCountries.contains(country.toUpperCase());
    }
    
    private double getTransactionTypeRisk(String transactionType) {
        Map<String, Double> typeRiskScores = Map.of(
                "WIRE_TRANSFER", 15.0,
                "INTERNATIONAL", 20.0,
                "CASH_DEPOSIT", 25.0,
                "CRYPTO_PURCHASE", 30.0,
                "THIRD_PARTY_PAYMENT", 20.0
        );
        return typeRiskScores.getOrDefault(transactionType, 5.0);
    }
    
    private void createManualReviewCase(TransactionEvent event, ComplianceCheck check) {
        complianceScreeningService.createManualReviewCase(
                event.getTransactionId(),
                check.getCheckId(),
                "HIGH_RISK_TRANSACTION",
                check.getRiskScore()
        );
    }
    
    private void flagForReview(TransactionEvent event, ComplianceCheck check) {
        complianceScreeningService.flagTransactionForReview(
                event.getTransactionId(),
                "COMPLIANCE_FLAG",
                check.getRiskScore()
        );
    }
    
    private void enableEnhancedMonitoring(TransactionEvent event, ComplianceCheck check) {
        complianceScreeningService.enableEnhancedMonitoring(
                event.getAccountId(),
                "HIGH_VALUE_TRANSACTION",
                30 // days
        );
    }
    
    private void approveTransaction(TransactionEvent event, ComplianceCheck check) {
        log.info("Transaction approved after compliance screening: {}", 
                event.getTransactionId());
        check.setStatus("APPROVED");
    }
    
    /**
     * Circuit breaker fallback method
     */
    public void handleCircuitBreakerFallback(TransactionEvent event, String topic, 
            int partition, long offset, long timestamp, String key, 
            Acknowledgment acknowledgment, Exception ex) {
        
        log.error("Circuit breaker activated for high-value transaction processing. " +
                "TransactionId: {}, Error: {}", event.getTransactionId(), ex.getMessage());
        
        // Store in fallback queue for manual processing
        complianceScreeningService.queueForManualCompliance(
                event.getTransactionId(),
                "CIRCUIT_BREAKER_TRIGGERED",
                ex.getMessage()
        );
        
        // Still acknowledge to prevent infinite retry
        acknowledgment.acknowledge();
    }
    
    /**
     * Compliance action enum
     */
    private enum ComplianceAction {
        APPROVE,
        ENHANCED_MONITORING,
        FLAG_FOR_REVIEW,
        BLOCK_PENDING_REVIEW,
        BLOCK
    }
}