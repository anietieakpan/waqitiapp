package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.payment.service.SettlementRecoveryService;
import com.waqiti.payment.service.PaymentReconciliationService;
import com.waqiti.payment.service.TreasuryManagementService;
import com.waqiti.payment.service.PaymentNotificationService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.resilience.CircuitBreakerService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class FailedSettlementsConsumer {
    
    private final SettlementRecoveryService settlementRecoveryService;
    private final PaymentReconciliationService paymentReconciliationService;
    private final TreasuryManagementService treasuryManagementService;
    private final PaymentNotificationService paymentNotificationService;
    private final AuditService auditService;
    private final CircuitBreakerService circuitBreakerService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final UniversalDLQHandler dlqHandler;
    
    // Idempotency tracking with 24-hour TTL
    private final ConcurrentHashMap<String, LocalDateTime> processedEvents = new ConcurrentHashMap<>();
    
    private Counter eventProcessedCounter;
    private Counter eventFailedCounter;
    private Timer processingTimer;
    
    @PostConstruct
    public void initMetrics() {
        eventProcessedCounter = Counter.builder("failed_settlements_processed_total")
                .description("Total number of failed settlement events processed")
                .register(meterRegistry);
        
        eventFailedCounter = Counter.builder("failed_settlements_failed_total")
                .description("Total number of failed settlement events that failed processing")
                .register(meterRegistry);
        
        processingTimer = Timer.builder("failed_settlements_processing_duration")
                .description("Time taken to process failed settlement events")
                .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = {"failed-settlements", "settlement-failures", "payment-settlement-errors"},
        groupId = "payment-service-failed-settlements-group",
        containerFactory = "criticalPaymentKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @CircuitBreaker(name = "failed-settlements", fallbackMethod = "handleCircuitBreakerFallback")
    @Retry(name = "failed-settlements")
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void handleFailedSettlement(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        log.info("SETTLEMENT: Processing failed settlement - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        String failureId = null;
        String settlementId = null;
        String failureReason = null;
        BigDecimal settlementAmount = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            failureId = (String) event.get("failureId");
            settlementId = (String) event.get("settlementId");
            failureReason = (String) event.get("failureReason");
            settlementAmount = new BigDecimal(event.get("settlementAmount").toString());
            String currency = (String) event.get("currency");
            LocalDateTime failureTimestamp = LocalDateTime.parse((String) event.get("failureTimestamp"));
            String settlementType = (String) event.get("settlementType");
            String failureCode = (String) event.get("failureCode");
            String bankCode = (String) event.getOrDefault("bankCode", "");
            String merchantId = (String) event.getOrDefault("merchantId", "");
            String processingNetwork = (String) event.get("processingNetwork");
            @SuppressWarnings("unchecked")
            List<String> affectedTransactions = (List<String>) event.get("affectedTransactions");
            Boolean isRetryable = (Boolean) event.getOrDefault("isRetryable", true);
            Integer attemptNumber = (Integer) event.getOrDefault("attemptNumber", 1);
            String urgencyLevel = (String) event.get("urgencyLevel");
            LocalDateTime originalSettlementDate = LocalDateTime.parse((String) event.get("originalSettlementDate"));
            @SuppressWarnings("unchecked")
            Map<String, Object> bankResponse = (Map<String, Object>) event.getOrDefault("bankResponse", Map.of());
            String settlementChannel = (String) event.getOrDefault("settlementChannel", "ACH");
            BigDecimal feeAmount = new BigDecimal(event.getOrDefault("feeAmount", "0.00").toString());
            Boolean requiresManualIntervention = (Boolean) event.getOrDefault("requiresManualIntervention", false);
            String riskCategory = (String) event.getOrDefault("riskCategory", "LOW");
            @SuppressWarnings("unchecked")
            List<String> impactedCustomers = (List<String>) event.getOrDefault("impactedCustomers", List.of());
            String reconciliationStatus = (String) event.getOrDefault("reconciliationStatus", "PENDING");
            
            log.info("Failed settlement - FailureId: {}, SettlementId: {}, Amount: {} {}, Type: {}, Network: {}, Attempts: {}", 
                    failureId, settlementId, settlementAmount, currency, settlementType, processingNetwork, attemptNumber);
            
            // Check idempotency
            String eventKey = failureId + "_" + settlementId + "_" + attemptNumber;
            if (isAlreadyProcessed(eventKey)) {
                log.warn("Failed settlement already processed, skipping: failureId={}, settlementId={}", 
                        failureId, settlementId);
                acknowledgment.acknowledge();
                return;
            }
            
            validateFailedSettlement(failureId, settlementId, failureReason, settlementAmount, 
                    currency, failureTimestamp, settlementType, urgencyLevel);
            
            // Analyze failure and determine recovery strategy
            analyzeFailureAndStrategy(failureId, settlementId, failureReason, failureCode, 
                    settlementType, settlementAmount, currency, bankCode, processingNetwork, 
                    bankResponse, isRetryable, attemptNumber, riskCategory);
            
            // Handle immediate financial impact
            handleImmediateFinancialImpact(failureId, settlementId, settlementAmount, currency, 
                    settlementType, merchantId, affectedTransactions, feeAmount, urgencyLevel);
            
            // Attempt automatic recovery if applicable
            if (isRetryable && attemptNumber < 5 && !requiresManualIntervention) {
                attemptAutomaticRecovery(failureId, settlementId, settlementAmount, currency, 
                        settlementType, settlementChannel, bankCode, processingNetwork, 
                        originalSettlementDate, attemptNumber);
            }
            
            // Update reconciliation status
            updateReconciliationStatus(failureId, settlementId, settlementAmount, currency, 
                    affectedTransactions, reconciliationStatus, failureReason);
            
            // Handle treasury management implications
            handleTreasuryImpact(failureId, settlementId, settlementAmount, currency, 
                    settlementType, failureTimestamp, merchantId, urgencyLevel, riskCategory);
            
            // Send failure notifications
            sendFailureNotifications(failureId, settlementId, settlementAmount, currency, 
                    settlementType, failureReason, merchantId, impactedCustomers, 
                    urgencyLevel, requiresManualIntervention);
            
            // Escalate if manual intervention required
            if (requiresManualIntervention || attemptNumber >= 5) {
                escalateForManualIntervention(failureId, settlementId, settlementAmount, 
                        currency, settlementType, failureReason, urgencyLevel, riskCategory, 
                        affectedTransactions, impactedCustomers);
            }
            
            // Update settlement analytics and reporting
            updateSettlementAnalytics(failureId, settlementId, settlementAmount, currency, 
                    settlementType, failureReason, processingNetwork, bankCode, attemptNumber, 
                    riskCategory, urgencyLevel);
            
            // Mark as processed
            markEventAsProcessed(eventKey);
            
            auditFailedSettlement(failureId, settlementId, settlementAmount, currency, 
                    settlementType, failureReason, affectedTransactions, processingStartTime);
            
            eventProcessedCounter.increment();
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Failed settlement processed successfully - FailureId: {}, SettlementId: {}, Amount: {} {}, ProcessingTime: {}ms", 
                    failureId, settlementId, settlementAmount, currency, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            eventFailedCounter.increment();
            log.error("CRITICAL: Failed settlement processing failed - FailureId: {}, SettlementId: {}, Amount: {} {}, Reason: {}, Error: {}",
                    failureId, settlementId, settlementAmount, failureReason, e.getMessage(), e);

            // Send to DLQ with context
            dlqHandler.handleFailedMessage(
                topic,
                eventJson,
                e,
                Map.of(
                    "failureId", failureId != null ? failureId : "unknown",
                    "settlementId", settlementId != null ? settlementId : "unknown",
                    "failureReason", failureReason != null ? failureReason : "unknown",
                    "settlementAmount", settlementAmount != null ? settlementAmount.toString() : "unknown",
                    "partition", String.valueOf(partition),
                    "offset", String.valueOf(offset)
                )
            );

            if (failureId != null && settlementId != null) {
                handleProcessingFailure(failureId, settlementId, failureReason, settlementAmount, e);
            }

            throw new RuntimeException("Failed settlement processing failed", e);
        } finally {
            sample.stop(processingTimer);
        }
    }
    
    private void validateFailedSettlement(String failureId, String settlementId, String failureReason, 
                                        BigDecimal settlementAmount, String currency, 
                                        LocalDateTime failureTimestamp, String settlementType, 
                                        String urgencyLevel) {
        if (failureId == null || failureId.trim().isEmpty()) {
            throw new IllegalArgumentException("Failure ID is required");
        }
        
        if (settlementId == null || settlementId.trim().isEmpty()) {
            throw new IllegalArgumentException("Settlement ID is required");
        }
        
        if (failureReason == null || failureReason.trim().isEmpty()) {
            throw new IllegalArgumentException("Failure reason is required");
        }
        
        if (settlementAmount == null || settlementAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Settlement amount must be positive");
        }
        
        if (currency == null || currency.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency is required");
        }
        
        if (failureTimestamp == null || failureTimestamp.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Invalid failure timestamp");
        }
        
        if (settlementType == null || settlementType.trim().isEmpty()) {
            throw new IllegalArgumentException("Settlement type is required");
        }
        
        List<String> validSettlementTypes = List.of("MERCHANT_PAYOUT", "CUSTOMER_REFUND", 
                "INTERCHANGE_SETTLEMENT", "REGULATORY_SETTLEMENT", "FEE_COLLECTION", 
                "CHARGEBACK_SETTLEMENT", "REVERSAL_SETTLEMENT");
        if (!validSettlementTypes.contains(settlementType)) {
            throw new IllegalArgumentException("Invalid settlement type: " + settlementType);
        }
        
        if (urgencyLevel == null || urgencyLevel.trim().isEmpty()) {
            throw new IllegalArgumentException("Urgency level is required");
        }
        
        List<String> validUrgencyLevels = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
        if (!validUrgencyLevels.contains(urgencyLevel)) {
            throw new IllegalArgumentException("Invalid urgency level: " + urgencyLevel);
        }
        
        log.debug("Failed settlement validation passed - FailureId: {}, SettlementId: {}", 
                failureId, settlementId);
    }
    
    private void analyzeFailureAndStrategy(String failureId, String settlementId, String failureReason, 
                                         String failureCode, String settlementType, 
                                         BigDecimal settlementAmount, String currency, String bankCode, 
                                         String processingNetwork, Map<String, Object> bankResponse, 
                                         Boolean isRetryable, Integer attemptNumber, String riskCategory) {
        try {
            settlementRecoveryService.analyzeFailureAndStrategy(failureId, settlementId, 
                    failureReason, failureCode, settlementType, settlementAmount, currency, 
                    bankCode, processingNetwork, bankResponse, isRetryable, attemptNumber, riskCategory);
            
            log.info("Failure analysis completed - FailureId: {}, SettlementId: {}, Type: {}, Retryable: {}", 
                    failureId, settlementId, settlementType, isRetryable);
            
        } catch (Exception e) {
            log.error("Failed to analyze failure and strategy - FailureId: {}, SettlementId: {}", 
                    failureId, settlementId, e);
            throw new RuntimeException("Failure analysis failed", e);
        }
    }
    
    private void handleImmediateFinancialImpact(String failureId, String settlementId, 
                                              BigDecimal settlementAmount, String currency, 
                                              String settlementType, String merchantId, 
                                              List<String> affectedTransactions, BigDecimal feeAmount, 
                                              String urgencyLevel) {
        try {
            treasuryManagementService.handleImmediateFinancialImpact(failureId, settlementId, 
                    settlementAmount, currency, settlementType, merchantId, affectedTransactions, 
                    feeAmount, urgencyLevel);
            
            log.info("Immediate financial impact handled - FailureId: {}, Amount: {} {}, Type: {}", 
                    failureId, settlementAmount, currency, settlementType);
            
        } catch (Exception e) {
            log.error("Failed to handle immediate financial impact - FailureId: {}, SettlementId: {}", 
                    failureId, settlementId, e);
            throw new RuntimeException("Financial impact handling failed", e);
        }
    }
    
    private void attemptAutomaticRecovery(String failureId, String settlementId, 
                                        BigDecimal settlementAmount, String currency, 
                                        String settlementType, String settlementChannel, 
                                        String bankCode, String processingNetwork, 
                                        LocalDateTime originalSettlementDate, Integer attemptNumber) {
        try {
            boolean recoveryAttempted = settlementRecoveryService.attemptAutomaticRecovery(
                    failureId, settlementId, settlementAmount, currency, settlementType, 
                    settlementChannel, bankCode, processingNetwork, originalSettlementDate, attemptNumber);
            
            if (recoveryAttempted) {
                log.info("Automatic recovery attempted - FailureId: {}, SettlementId: {}, Attempt: {}", 
                        failureId, settlementId, attemptNumber + 1);
            } else {
                log.warn("Automatic recovery not possible - FailureId: {}, SettlementId: {} - Manual intervention required", 
                        failureId, settlementId);
            }
            
        } catch (Exception e) {
            log.error("Failed to attempt automatic recovery - FailureId: {}, SettlementId: {}", 
                    failureId, settlementId, e);
            // Don't throw exception as recovery failure shouldn't block processing
        }
    }
    
    private void updateReconciliationStatus(String failureId, String settlementId, 
                                          BigDecimal settlementAmount, String currency, 
                                          List<String> affectedTransactions, String reconciliationStatus, 
                                          String failureReason) {
        try {
            paymentReconciliationService.updateReconciliationStatus(failureId, settlementId, 
                    settlementAmount, currency, affectedTransactions, reconciliationStatus, failureReason);
            
            log.info("Reconciliation status updated - FailureId: {}, SettlementId: {}, Status: {}", 
                    failureId, settlementId, reconciliationStatus);
            
        } catch (Exception e) {
            log.error("Failed to update reconciliation status - FailureId: {}, SettlementId: {}", 
                    failureId, settlementId, e);
            throw new RuntimeException("Reconciliation status update failed", e);
        }
    }
    
    private void handleTreasuryImpact(String failureId, String settlementId, BigDecimal settlementAmount, 
                                    String currency, String settlementType, LocalDateTime failureTimestamp, 
                                    String merchantId, String urgencyLevel, String riskCategory) {
        try {
            treasuryManagementService.handleTreasuryImpact(failureId, settlementId, settlementAmount, 
                    currency, settlementType, failureTimestamp, merchantId, urgencyLevel, riskCategory);
            
            log.info("Treasury impact handled - FailureId: {}, Amount: {} {}, Risk: {}", 
                    failureId, settlementAmount, currency, riskCategory);
            
        } catch (Exception e) {
            log.error("Failed to handle treasury impact - FailureId: {}, SettlementId: {}", 
                    failureId, settlementId, e);
            // Don't throw exception as treasury impact handling failure shouldn't block processing
        }
    }
    
    private void sendFailureNotifications(String failureId, String settlementId, 
                                        BigDecimal settlementAmount, String currency, 
                                        String settlementType, String failureReason, String merchantId, 
                                        List<String> impactedCustomers, String urgencyLevel, 
                                        Boolean requiresManualIntervention) {
        try {
            // Notify treasury team
            paymentNotificationService.notifyTreasuryTeam(failureId, settlementId, settlementAmount, 
                    currency, settlementType, failureReason, urgencyLevel);
            
            // Notify operations team
            paymentNotificationService.notifyOperationsTeam(failureId, settlementId, settlementAmount, 
                    currency, failureReason, requiresManualIntervention);
            
            // Notify merchant if applicable
            if (merchantId != null && !merchantId.isEmpty()) {
                paymentNotificationService.notifyMerchant(failureId, merchantId, settlementId, 
                        settlementAmount, currency, settlementType, failureReason);
            }
            
            // Notify risk team for high-value failures
            if ("CRITICAL".equals(urgencyLevel) || "HIGH".equals(urgencyLevel)) {
                paymentNotificationService.notifyRiskTeam(failureId, settlementId, settlementAmount, 
                        currency, settlementType, failureReason, urgencyLevel);
            }
            
            // Notify impacted customers if applicable
            if (!impactedCustomers.isEmpty()) {
                paymentNotificationService.notifyImpactedCustomers(failureId, settlementId, 
                        impactedCustomers, settlementAmount, currency, failureReason);
            }
            
            log.info("Failure notifications sent - FailureId: {}, SettlementId: {}, Recipients: multiple", 
                    failureId, settlementId);
            
        } catch (Exception e) {
            log.error("Failed to send failure notifications - FailureId: {}, SettlementId: {}", 
                    failureId, settlementId, e);
            // Don't throw exception as notification failure shouldn't block processing
        }
    }
    
    private void escalateForManualIntervention(String failureId, String settlementId, 
                                             BigDecimal settlementAmount, String currency, 
                                             String settlementType, String failureReason, 
                                             String urgencyLevel, String riskCategory, 
                                             List<String> affectedTransactions, List<String> impactedCustomers) {
        try {
            settlementRecoveryService.escalateForManualIntervention(failureId, settlementId, 
                    settlementAmount, currency, settlementType, failureReason, urgencyLevel, 
                    riskCategory, affectedTransactions, impactedCustomers);
            
            log.info("Escalated for manual intervention - FailureId: {}, SettlementId: {}, Urgency: {}", 
                    failureId, settlementId, urgencyLevel);
            
        } catch (Exception e) {
            log.error("Failed to escalate for manual intervention - FailureId: {}, SettlementId: {}", 
                    failureId, settlementId, e);
            // Don't throw exception as escalation failure shouldn't block processing
        }
    }
    
    private void updateSettlementAnalytics(String failureId, String settlementId, 
                                         BigDecimal settlementAmount, String currency, 
                                         String settlementType, String failureReason, 
                                         String processingNetwork, String bankCode, Integer attemptNumber, 
                                         String riskCategory, String urgencyLevel) {
        try {
            settlementRecoveryService.updateSettlementAnalytics(failureId, settlementId, 
                    settlementAmount, currency, settlementType, failureReason, processingNetwork, 
                    bankCode, attemptNumber, riskCategory, urgencyLevel);
            
            log.info("Settlement analytics updated - FailureId: {}, SettlementId: {}, Type: {}", 
                    failureId, settlementId, settlementType);
            
        } catch (Exception e) {
            log.error("Failed to update settlement analytics - FailureId: {}, SettlementId: {}", 
                    failureId, settlementId, e);
            // Don't throw exception as analytics update failure shouldn't block processing
        }
    }
    
    private void auditFailedSettlement(String failureId, String settlementId, BigDecimal settlementAmount, 
                                     String currency, String settlementType, String failureReason, 
                                     List<String> affectedTransactions, LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditFinancialEvent(
                    "FAILED_SETTLEMENT_PROCESSED",
                    "SYSTEM",
                    String.format("Failed settlement processed - Type: %s, Amount: %s %s, Reason: %s, Transactions: %d", 
                            settlementType, settlementAmount, currency, failureReason, affectedTransactions.size()),
                    Map.of(
                            "failureId", failureId,
                            "settlementId", settlementId,
                            "settlementAmount", settlementAmount.toString(),
                            "currency", currency,
                            "settlementType", settlementType,
                            "failureReason", failureReason,
                            "affectedTransactionsCount", affectedTransactions.size(),
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit failed settlement - FailureId: {}, SettlementId: {}", 
                    failureId, settlementId, e);
        }
    }
    
    private void handleProcessingFailure(String failureId, String settlementId, String failureReason, 
                                       BigDecimal settlementAmount, Exception error) {
        try {
            settlementRecoveryService.recordProcessingFailure(failureId, settlementId, failureReason, 
                    settlementAmount, error.getMessage());
            
            auditService.auditFinancialEvent(
                    "FAILED_SETTLEMENT_PROCESSING_FAILED",
                    "SYSTEM",
                    "Failed to process failed settlement: " + error.getMessage(),
                    Map.of(
                            "failureId", failureId,
                            "settlementId", settlementId,
                            "failureReason", failureReason != null ? failureReason : "UNKNOWN",
                            "settlementAmount", settlementAmount != null ? settlementAmount.toString() : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle processing failure - FailureId: {}, SettlementId: {}", 
                    failureId, settlementId, e);
        }
    }
    
    public void handleCircuitBreakerFallback(String eventJson, String topic, int partition, 
                                           long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("CIRCUIT BREAKER: Failed settlement processing circuit breaker activated - Topic: {}, Error: {}", 
                topic, e.getMessage());
        
        try {
            circuitBreakerService.handleFallback("failed-settlements", eventJson, e);
        } catch (Exception fallbackError) {
            log.error("Fallback handling failed for failed settlement", fallbackError);
        }
    }
    
    @DltHandler
    public void handleDlt(String eventJson, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                         @Header(value = "x-original-topic", required = false) String originalTopic,
                         @Header(value = "x-error-message", required = false) String errorMessage) {
        
        log.error("CRITICAL: Failed settlement sent to DLT - OriginalTopic: {}, Error: {}, Event: {}", 
                originalTopic, errorMessage, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String failureId = (String) event.get("failureId");
            String settlementId = (String) event.get("settlementId");
            String settlementAmount = event.get("settlementAmount").toString();
            String currency = (String) event.get("currency");
            String settlementType = (String) event.get("settlementType");
            
            log.error("DLT: Failed settlement failed permanently - FailureId: {}, SettlementId: {}, Amount: {} {}, Type: {} - IMMEDIATE MANUAL INTERVENTION REQUIRED", 
                    failureId, settlementId, settlementAmount, currency, settlementType);
            
            // Critical: Send emergency notification for failed settlement processing
            paymentNotificationService.sendEmergencySettlementNotification(failureId, settlementId, 
                    settlementAmount, currency, settlementType, "DLT: " + errorMessage);
            
            settlementRecoveryService.markForEmergencyReview(failureId, settlementId, 
                    settlementAmount, currency, settlementType, "DLT: " + errorMessage);
            
        } catch (Exception e) {
            log.error("Failed to parse failed settlement DLT event: {}", eventJson, e);
        }
    }
    
    private boolean isAlreadyProcessed(String eventKey) {
        LocalDateTime processedTime = processedEvents.get(eventKey);
        if (processedTime != null) {
            // Check if processed within last 24 hours
            if (processedTime.isAfter(LocalDateTime.now().minusHours(24))) {
                return true;
            } else {
                // Remove expired entry
                processedEvents.remove(eventKey);
            }
        }
        return false;
    }
    
    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, LocalDateTime.now());
        
        // Clean up old entries periodically
        if (processedEvents.size() > 10000) {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
            processedEvents.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        }
    }
}