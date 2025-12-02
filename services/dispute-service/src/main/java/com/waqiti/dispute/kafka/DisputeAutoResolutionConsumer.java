package com.waqiti.dispute.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.dispute.service.DisputeResolutionService;
import com.waqiti.dispute.service.DisputeAnalysisService;
import com.waqiti.dispute.service.DisputeNotificationService;
import com.waqiti.dispute.service.ChargebackService;
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

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class DisputeAutoResolutionConsumer {
    
    private final DisputeResolutionService disputeResolutionService;
    private final DisputeAnalysisService disputeAnalysisService;
    private final DisputeNotificationService disputeNotificationService;
    private final ChargebackService chargebackService;
    private final AuditService auditService;
    private final CircuitBreakerService circuitBreakerService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    // Idempotency tracking with 24-hour TTL
    private final ConcurrentHashMap<String, LocalDateTime> processedEvents = new ConcurrentHashMap<>();
    
    private Counter eventProcessedCounter;
    private Counter eventFailedCounter;
    private Timer processingTimer;
    
    @PostConstruct
    public void initMetrics() {
        eventProcessedCounter = Counter.builder("dispute_auto_resolution_processed_total")
                .description("Total number of dispute auto resolution events processed")
                .register(meterRegistry);
        
        eventFailedCounter = Counter.builder("dispute_auto_resolution_failed_total")
                .description("Total number of dispute auto resolution events that failed processing")
                .register(meterRegistry);
        
        processingTimer = Timer.builder("dispute_auto_resolution_processing_duration")
                .description("Time taken to process dispute auto resolution events")
                .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = {"dispute-auto-resolution", "automated-dispute-resolution", "dispute-ai-resolution"},
        groupId = "dispute-service-auto-resolution-group",
        containerFactory = "criticalDisputeKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @CircuitBreaker(name = "dispute-auto-resolution", fallbackMethod = "handleCircuitBreakerFallback")
    @Retry(name = "dispute-auto-resolution")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleDisputeAutoResolution(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        log.info("DISPUTE: Processing auto resolution - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID disputeId = null;
        UUID customerId = null;
        String resolutionType = null;
        String resolutionDecision = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            disputeId = UUID.fromString((String) event.get("disputeId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            resolutionType = (String) event.get("resolutionType");
            resolutionDecision = (String) event.get("resolutionDecision");
            BigDecimal disputeAmount = new BigDecimal(event.get("disputeAmount").toString());
            String currency = (String) event.get("currency");
            String disputeReason = (String) event.get("disputeReason");
            String disputeCategory = (String) event.get("disputeCategory");
            LocalDateTime resolutionTimestamp = LocalDateTime.parse((String) event.get("resolutionTimestamp"));
            String aiConfidenceScore = (String) event.getOrDefault("aiConfidenceScore", "0.0");
            @SuppressWarnings("unchecked")
            Map<String, Object> resolutionEvidence = (Map<String, Object>) event.getOrDefault("resolutionEvidence", Map.of());
            @SuppressWarnings("unchecked")
            List<String> supportingDocuments = (List<String>) event.getOrDefault("supportingDocuments", List.of());
            String merchantId = (String) event.getOrDefault("merchantId", "");
            String transactionId = (String) event.getOrDefault("transactionId", "");
            Boolean requiresManualReview = (Boolean) event.getOrDefault("requiresManualReview", false);
            String resolutionExplanation = (String) event.getOrDefault("resolutionExplanation", "");
            @SuppressWarnings("unchecked")
            Map<String, Object> fraudIndicators = (Map<String, Object>) event.getOrDefault("fraudIndicators", Map.of());
            String riskScore = (String) event.getOrDefault("riskScore", "LOW");
            
            log.info("Dispute auto resolution - DisputeId: {}, CustomerId: {}, ResolutionType: {}, Decision: {}, Amount: {} {}", 
                    disputeId, customerId, resolutionType, resolutionDecision, disputeAmount, currency);
            
            // Check idempotency
            String eventKey = disputeId.toString() + "_" + resolutionType + "_" + resolutionDecision;
            if (isAlreadyProcessed(eventKey)) {
                log.warn("Dispute auto resolution already processed, skipping: disputeId={}, resolutionType={}", 
                        disputeId, resolutionType);
                acknowledgment.acknowledge();
                return;
            }
            
            validateDisputeAutoResolution(disputeId, customerId, resolutionType, resolutionDecision, 
                    disputeAmount, currency, resolutionTimestamp);
            
            // Process the auto resolution
            processAutoResolution(disputeId, customerId, resolutionType, resolutionDecision, 
                    disputeAmount, currency, disputeReason, disputeCategory, resolutionTimestamp, 
                    aiConfidenceScore, resolutionEvidence, resolutionExplanation);
            
            // Handle resolution decision
            handleResolutionDecision(disputeId, customerId, resolutionDecision, disputeAmount, 
                    currency, merchantId, transactionId, supportingDocuments, fraudIndicators);
            
            // Update dispute status
            updateDisputeStatus(disputeId, customerId, resolutionType, resolutionDecision, 
                    resolutionTimestamp, aiConfidenceScore, requiresManualReview);
            
            // Execute financial adjustments
            executeFinancialAdjustments(disputeId, customerId, resolutionDecision, disputeAmount, 
                    currency, merchantId, transactionId, disputeReason);
            
            // Handle chargeback if applicable
            if ("CHARGEBACK_ISSUED".equals(resolutionDecision)) {
                processChargeback(disputeId, customerId, disputeAmount, currency, merchantId, 
                        transactionId, disputeReason, supportingDocuments);
            }
            
            // Send notifications
            sendResolutionNotifications(disputeId, customerId, resolutionType, resolutionDecision, 
                    disputeAmount, currency, merchantId, resolutionExplanation, requiresManualReview);
            
            // Update analytics and reporting
            updateDisputeAnalytics(disputeId, customerId, resolutionType, resolutionDecision, 
                    disputeAmount, disputeCategory, aiConfidenceScore, riskScore);
            
            // Mark as processed
            markEventAsProcessed(eventKey);
            
            auditDisputeAutoResolution(disputeId, customerId, resolutionType, resolutionDecision, 
                    disputeAmount, currency, processingStartTime);
            
            eventProcessedCounter.increment();
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Dispute auto resolution processed successfully - DisputeId: {}, Decision: {}, ProcessingTime: {}ms", 
                    disputeId, resolutionDecision, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            eventFailedCounter.increment();
            log.error("CRITICAL: Dispute auto resolution processing failed - DisputeId: {}, CustomerId: {}, ResolutionType: {}, Decision: {}, Error: {}", 
                    disputeId, customerId, resolutionType, resolutionDecision, e.getMessage(), e);
            
            if (disputeId != null && customerId != null) {
                handleProcessingFailure(disputeId, customerId, resolutionType, resolutionDecision, e);
            }
            
            throw new RuntimeException("Dispute auto resolution processing failed", e);
        } finally {
            sample.stop(processingTimer);
        }
    }
    
    private void validateDisputeAutoResolution(UUID disputeId, UUID customerId, String resolutionType, 
                                             String resolutionDecision, BigDecimal disputeAmount, 
                                             String currency, LocalDateTime resolutionTimestamp) {
        if (disputeId == null) {
            throw new IllegalArgumentException("Dispute ID is required");
        }
        
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        
        if (resolutionType == null || resolutionType.trim().isEmpty()) {
            throw new IllegalArgumentException("Resolution type is required");
        }
        
        List<String> validResolutionTypes = List.of("AUTOMATED_AI", "RULE_BASED", "PATTERN_MATCHING", 
                "FRAUD_DETECTION", "MERCHANT_RESPONSE", "EVIDENCE_ANALYSIS");
        if (!validResolutionTypes.contains(resolutionType)) {
            throw new IllegalArgumentException("Invalid resolution type: " + resolutionType);
        }
        
        if (resolutionDecision == null || resolutionDecision.trim().isEmpty()) {
            throw new IllegalArgumentException("Resolution decision is required");
        }
        
        List<String> validDecisions = List.of("APPROVED", "DENIED", "PARTIAL_APPROVAL", 
                "CHARGEBACK_ISSUED", "MERCHANT_LIABILITY", "CUSTOMER_LIABILITY", "PENDING_REVIEW");
        if (!validDecisions.contains(resolutionDecision)) {
            throw new IllegalArgumentException("Invalid resolution decision: " + resolutionDecision);
        }
        
        if (disputeAmount == null || disputeAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Dispute amount must be positive");
        }
        
        if (currency == null || currency.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency is required");
        }
        
        if (resolutionTimestamp == null || resolutionTimestamp.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Invalid resolution timestamp");
        }
        
        log.debug("Dispute auto resolution validation passed - DisputeId: {}, Decision: {}", 
                disputeId, resolutionDecision);
    }
    
    private void processAutoResolution(UUID disputeId, UUID customerId, String resolutionType, 
                                     String resolutionDecision, BigDecimal disputeAmount, String currency, 
                                     String disputeReason, String disputeCategory, LocalDateTime resolutionTimestamp, 
                                     String aiConfidenceScore, Map<String, Object> resolutionEvidence, 
                                     String resolutionExplanation) {
        try {
            disputeResolutionService.processAutoResolution(disputeId, customerId, resolutionType, 
                    resolutionDecision, disputeAmount, currency, disputeReason, disputeCategory, 
                    resolutionTimestamp, aiConfidenceScore, resolutionEvidence, resolutionExplanation);
            
            log.info("Auto resolution processed - DisputeId: {}, Type: {}, Decision: {}", 
                    disputeId, resolutionType, resolutionDecision);
            
        } catch (Exception e) {
            log.error("Failed to process auto resolution - DisputeId: {}, Type: {}", 
                    disputeId, resolutionType, e);
            throw new RuntimeException("Auto resolution processing failed", e);
        }
    }
    
    private void handleResolutionDecision(UUID disputeId, UUID customerId, String resolutionDecision, 
                                        BigDecimal disputeAmount, String currency, String merchantId, 
                                        String transactionId, List<String> supportingDocuments, 
                                        Map<String, Object> fraudIndicators) {
        try {
            switch (resolutionDecision) {
                case "APPROVED" -> disputeResolutionService.approveDispute(disputeId, customerId, 
                        disputeAmount, currency, supportingDocuments);
                
                case "DENIED" -> disputeResolutionService.denyDispute(disputeId, customerId, 
                        disputeAmount, currency, supportingDocuments);
                
                case "PARTIAL_APPROVAL" -> disputeResolutionService.partiallyApproveDispute(disputeId, 
                        customerId, disputeAmount, currency, supportingDocuments);
                
                case "CHARGEBACK_ISSUED" -> disputeResolutionService.issueChargeback(disputeId, 
                        customerId, disputeAmount, currency, merchantId, transactionId);
                
                case "MERCHANT_LIABILITY" -> disputeResolutionService.assignMerchantLiability(disputeId, 
                        customerId, merchantId, disputeAmount, currency);
                
                case "CUSTOMER_LIABILITY" -> disputeResolutionService.assignCustomerLiability(disputeId, 
                        customerId, disputeAmount, currency, fraudIndicators);
                
                case "PENDING_REVIEW" -> disputeResolutionService.escalateForManualReview(disputeId, 
                        customerId, disputeAmount, currency, supportingDocuments);
                
                default -> log.warn("Unknown resolution decision: {}", resolutionDecision);
            }
            
            log.info("Resolution decision handled - DisputeId: {}, Decision: {}", 
                    disputeId, resolutionDecision);
            
        } catch (Exception e) {
            log.error("Failed to handle resolution decision - DisputeId: {}, Decision: {}", 
                    disputeId, resolutionDecision, e);
            throw new RuntimeException("Resolution decision handling failed", e);
        }
    }
    
    private void updateDisputeStatus(UUID disputeId, UUID customerId, String resolutionType, 
                                   String resolutionDecision, LocalDateTime resolutionTimestamp, 
                                   String aiConfidenceScore, Boolean requiresManualReview) {
        try {
            String newStatus = determineDisputeStatus(resolutionDecision, requiresManualReview);
            
            disputeResolutionService.updateDisputeStatus(disputeId, customerId, newStatus, 
                    resolutionType, resolutionTimestamp, aiConfidenceScore, requiresManualReview);
            
            log.info("Dispute status updated - DisputeId: {}, NewStatus: {}, RequiresReview: {}", 
                    disputeId, newStatus, requiresManualReview);
            
        } catch (Exception e) {
            log.error("Failed to update dispute status - DisputeId: {}", disputeId, e);
            throw new RuntimeException("Dispute status update failed", e);
        }
    }
    
    private void executeFinancialAdjustments(UUID disputeId, UUID customerId, String resolutionDecision, 
                                           BigDecimal disputeAmount, String currency, String merchantId, 
                                           String transactionId, String disputeReason) {
        try {
            if ("APPROVED".equals(resolutionDecision) || "PARTIAL_APPROVAL".equals(resolutionDecision)) {
                disputeResolutionService.processRefund(disputeId, customerId, disputeAmount, 
                        currency, transactionId, disputeReason);
            }
            
            if ("CHARGEBACK_ISSUED".equals(resolutionDecision)) {
                disputeResolutionService.processChargebackAdjustment(disputeId, customerId, 
                        merchantId, disputeAmount, currency, transactionId);
            }
            
            if ("MERCHANT_LIABILITY".equals(resolutionDecision)) {
                disputeResolutionService.processMerchantLiabilityAdjustment(disputeId, 
                        merchantId, disputeAmount, currency, disputeReason);
            }
            
            log.info("Financial adjustments executed - DisputeId: {}, Decision: {}, Amount: {} {}", 
                    disputeId, resolutionDecision, disputeAmount, currency);
            
        } catch (Exception e) {
            log.error("Failed to execute financial adjustments - DisputeId: {}, Decision: {}", 
                    disputeId, resolutionDecision, e);
            // Don't throw exception as financial adjustment failure shouldn't block processing
        }
    }
    
    private void processChargeback(UUID disputeId, UUID customerId, BigDecimal disputeAmount, 
                                 String currency, String merchantId, String transactionId, 
                                 String disputeReason, List<String> supportingDocuments) {
        try {
            chargebackService.processChargeback(disputeId, customerId, disputeAmount, currency, 
                    merchantId, transactionId, disputeReason, supportingDocuments);
            
            log.info("Chargeback processed - DisputeId: {}, Amount: {} {}, MerchantId: {}", 
                    disputeId, disputeAmount, currency, merchantId);
            
        } catch (Exception e) {
            log.error("Failed to process chargeback - DisputeId: {}, MerchantId: {}", 
                    disputeId, merchantId, e);
            // Don't throw exception as chargeback failure shouldn't block processing
        }
    }
    
    private void sendResolutionNotifications(UUID disputeId, UUID customerId, String resolutionType, 
                                           String resolutionDecision, BigDecimal disputeAmount, 
                                           String currency, String merchantId, String resolutionExplanation, 
                                           Boolean requiresManualReview) {
        try {
            // Notify customer
            disputeNotificationService.notifyCustomer(disputeId, customerId, resolutionDecision, 
                    disputeAmount, currency, resolutionExplanation);
            
            // Notify merchant if applicable
            if (merchantId != null && !merchantId.isEmpty()) {
                disputeNotificationService.notifyMerchant(disputeId, merchantId, resolutionDecision, 
                        disputeAmount, currency, resolutionExplanation);
            }
            
            // Notify dispute team if manual review required
            if (requiresManualReview) {
                disputeNotificationService.notifyDisputeTeam(disputeId, customerId, resolutionType, 
                        resolutionDecision, disputeAmount, currency, "MANUAL_REVIEW_REQUIRED");
            }
            
            // Notify operations team for chargebacks
            if ("CHARGEBACK_ISSUED".equals(resolutionDecision)) {
                disputeNotificationService.notifyOperationsTeam(disputeId, customerId, merchantId, 
                        disputeAmount, currency, "CHARGEBACK_ISSUED");
            }
            
            log.info("Resolution notifications sent - DisputeId: {}, Decision: {}", 
                    disputeId, resolutionDecision);
            
        } catch (Exception e) {
            log.error("Failed to send resolution notifications - DisputeId: {}, Decision: {}", 
                    disputeId, resolutionDecision, e);
            // Don't throw exception as notification failure shouldn't block processing
        }
    }
    
    private void updateDisputeAnalytics(UUID disputeId, UUID customerId, String resolutionType, 
                                      String resolutionDecision, BigDecimal disputeAmount, 
                                      String disputeCategory, String aiConfidenceScore, String riskScore) {
        try {
            disputeAnalysisService.updateResolutionAnalytics(disputeId, customerId, resolutionType, 
                    resolutionDecision, disputeAmount, disputeCategory, aiConfidenceScore, riskScore);
            
            log.info("Dispute analytics updated - DisputeId: {}, Decision: {}, Category: {}", 
                    disputeId, resolutionDecision, disputeCategory);
            
        } catch (Exception e) {
            log.error("Failed to update dispute analytics - DisputeId: {}", disputeId, e);
            // Don't throw exception as analytics update failure shouldn't block processing
        }
    }
    
    private String determineDisputeStatus(String resolutionDecision, Boolean requiresManualReview) {
        if (requiresManualReview) {
            return "PENDING_MANUAL_REVIEW";
        }
        
        return switch (resolutionDecision) {
            case "APPROVED" -> "RESOLVED_APPROVED";
            case "DENIED" -> "RESOLVED_DENIED";
            case "PARTIAL_APPROVAL" -> "RESOLVED_PARTIAL";
            case "CHARGEBACK_ISSUED" -> "CHARGEBACK_PROCESSED";
            case "MERCHANT_LIABILITY" -> "RESOLVED_MERCHANT_LIABLE";
            case "CUSTOMER_LIABILITY" -> "RESOLVED_CUSTOMER_LIABLE";
            case "PENDING_REVIEW" -> "PENDING_MANUAL_REVIEW";
            default -> "RESOLVED_UNKNOWN";
        };
    }
    
    private void auditDisputeAutoResolution(UUID disputeId, UUID customerId, String resolutionType, 
                                          String resolutionDecision, BigDecimal disputeAmount, 
                                          String currency, LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditFinancialEvent(
                    "DISPUTE_AUTO_RESOLUTION_PROCESSED",
                    customerId.toString(),
                    String.format("Dispute auto resolution processed - Type: %s, Decision: %s, Amount: %s %s", 
                            resolutionType, resolutionDecision, disputeAmount, currency),
                    Map.of(
                            "disputeId", disputeId.toString(),
                            "customerId", customerId.toString(),
                            "resolutionType", resolutionType,
                            "resolutionDecision", resolutionDecision,
                            "disputeAmount", disputeAmount.toString(),
                            "currency", currency,
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit dispute auto resolution - DisputeId: {}", disputeId, e);
        }
    }
    
    private void handleProcessingFailure(UUID disputeId, UUID customerId, String resolutionType, 
                                       String resolutionDecision, Exception error) {
        try {
            disputeResolutionService.recordProcessingFailure(disputeId, customerId, resolutionType, 
                    resolutionDecision, error.getMessage());
            
            auditService.auditFinancialEvent(
                    "DISPUTE_AUTO_RESOLUTION_PROCESSING_FAILED",
                    customerId.toString(),
                    "Failed to process dispute auto resolution: " + error.getMessage(),
                    Map.of(
                            "disputeId", disputeId.toString(),
                            "customerId", customerId.toString(),
                            "resolutionType", resolutionType != null ? resolutionType : "UNKNOWN",
                            "resolutionDecision", resolutionDecision != null ? resolutionDecision : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle processing failure - DisputeId: {}", disputeId, e);
        }
    }
    
    public void handleCircuitBreakerFallback(String eventJson, String topic, int partition, 
                                           long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("CIRCUIT BREAKER: Dispute auto resolution processing circuit breaker activated - Topic: {}, Error: {}", 
                topic, e.getMessage());
        
        try {
            circuitBreakerService.handleFallback("dispute-auto-resolution", eventJson, e);
        } catch (Exception fallbackError) {
            log.error("Fallback handling failed for dispute auto resolution", fallbackError);
        }
    }
    
    @DltHandler
    public void handleDlt(String eventJson, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                         @Header(value = "x-original-topic", required = false) String originalTopic,
                         @Header(value = "x-error-message", required = false) String errorMessage) {
        
        log.error("CRITICAL: Dispute auto resolution sent to DLT - OriginalTopic: {}, Error: {}, Event: {}", 
                originalTopic, errorMessage, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String disputeId = (String) event.get("disputeId");
            String customerId = (String) event.get("customerId");
            String resolutionType = (String) event.get("resolutionType");
            String resolutionDecision = (String) event.get("resolutionDecision");
            
            log.error("DLT: Dispute auto resolution failed permanently - DisputeId: {}, CustomerId: {}, Type: {}, Decision: {} - IMMEDIATE MANUAL INTERVENTION REQUIRED", 
                    disputeId, customerId, resolutionType, resolutionDecision);
            
            // Critical: Escalate failed dispute resolutions
            disputeNotificationService.sendEmergencyNotification(disputeId, customerId, 
                    resolutionType, resolutionDecision, "DLT: " + errorMessage);
            
            disputeResolutionService.markForEmergencyReview(disputeId, customerId, 
                    resolutionType, resolutionDecision, "DLT: " + errorMessage);
            
        } catch (Exception e) {
            log.error("Failed to parse dispute auto resolution DLT event: {}", eventJson, e);
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