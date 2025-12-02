package com.waqiti.dispute.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.dispute.service.DisputeEscalationService;
import com.waqiti.dispute.service.DisputeWorkflowService;
import com.waqiti.dispute.service.DisputeNotificationService;
import com.waqiti.dispute.service.DisputeAnalysisService;
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
public class DisputeEscalationsConsumer {
    
    private final DisputeEscalationService disputeEscalationService;
    private final DisputeWorkflowService disputeWorkflowService;
    private final DisputeNotificationService disputeNotificationService;
    private final DisputeAnalysisService disputeAnalysisService;
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
        eventProcessedCounter = Counter.builder("dispute_escalations_processed_total")
                .description("Total number of dispute escalation events processed")
                .register(meterRegistry);
        
        eventFailedCounter = Counter.builder("dispute_escalations_failed_total")
                .description("Total number of dispute escalation events that failed processing")
                .register(meterRegistry);
        
        processingTimer = Timer.builder("dispute_escalations_processing_duration")
                .description("Time taken to process dispute escalation events")
                .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = {"dispute-escalations", "dispute-escalation-workflow", "dispute-tier-escalation"},
        groupId = "dispute-service-escalations-group",
        containerFactory = "criticalDisputeKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @CircuitBreaker(name = "dispute-escalations", fallbackMethod = "handleCircuitBreakerFallback")
    @Retry(name = "dispute-escalations")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleDisputeEscalation(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        log.info("DISPUTE: Processing escalation - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID disputeId = null;
        UUID customerId = null;
        String escalationType = null;
        String escalationReason = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            disputeId = UUID.fromString((String) event.get("disputeId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            escalationType = (String) event.get("escalationType");
            escalationReason = (String) event.get("escalationReason");
            String currentTier = (String) event.get("currentTier");
            String targetTier = (String) event.get("targetTier");
            BigDecimal disputeAmount = new BigDecimal(event.get("disputeAmount").toString());
            String currency = (String) event.get("currency");
            String disputeCategory = (String) event.get("disputeCategory");
            String priority = (String) event.get("priority");
            LocalDateTime escalationTimestamp = LocalDateTime.parse((String) event.get("escalationTimestamp"));
            String escalatedBy = (String) event.get("escalatedBy");
            String escalatedByRole = (String) event.getOrDefault("escalatedByRole", "SYSTEM");
            @SuppressWarnings("unchecked")
            List<String> escalationTriggers = (List<String>) event.get("escalationTriggers");
            @SuppressWarnings("unchecked")
            Map<String, Object> escalationContext = (Map<String, Object>) event.getOrDefault("escalationContext", Map.of());
            String merchantId = (String) event.getOrDefault("merchantId", "");
            String slaViolationType = (String) event.getOrDefault("slaViolationType", "");
            Boolean requiresManagerApproval = (Boolean) event.getOrDefault("requiresManagerApproval", false);
            Boolean requiresLegalReview = (Boolean) event.getOrDefault("requiresLegalReview", false);
            String complexityScore = (String) event.getOrDefault("complexityScore", "MEDIUM");
            @SuppressWarnings("unchecked")
            List<String> attachedDocuments = (List<String>) event.getOrDefault("attachedDocuments", List.of());
            
            log.info("Dispute escalation - DisputeId: {}, CustomerId: {}, Type: {}, From: {} To: {}, Priority: {}, Amount: {} {}", 
                    disputeId, customerId, escalationType, currentTier, targetTier, priority, disputeAmount, currency);
            
            // Check idempotency
            String eventKey = disputeId.toString() + "_" + escalationType + "_" + targetTier;
            if (isAlreadyProcessed(eventKey)) {
                log.warn("Dispute escalation already processed, skipping: disputeId={}, escalationType={}", 
                        disputeId, escalationType);
                acknowledgment.acknowledge();
                return;
            }
            
            validateDisputeEscalation(disputeId, customerId, escalationType, escalationReason, 
                    currentTier, targetTier, disputeAmount, escalationTimestamp);
            
            // Process the escalation
            processEscalation(disputeId, customerId, escalationType, escalationReason, 
                    currentTier, targetTier, disputeAmount, currency, disputeCategory, 
                    priority, escalationTimestamp, escalatedBy, escalatedByRole, escalationTriggers);
            
            // Update workflow
            updateEscalationWorkflow(disputeId, customerId, currentTier, targetTier, 
                    escalationType, escalationContext, requiresManagerApproval, requiresLegalReview);
            
            // Handle SLA violations
            if (slaViolationType != null && !slaViolationType.isEmpty()) {
                handleSLAViolation(disputeId, customerId, slaViolationType, escalationType, 
                        disputeAmount, currency, escalationTimestamp);
            }
            
            // Assign to appropriate team/agent
            assignToTeam(disputeId, customerId, targetTier, escalationType, priority, 
                    complexityScore, disputeCategory, requiresManagerApproval, requiresLegalReview);
            
            // Send escalation notifications
            sendEscalationNotifications(disputeId, customerId, escalationType, escalationReason, 
                    currentTier, targetTier, priority, disputeAmount, currency, merchantId, 
                    escalatedBy, escalatedByRole);
            
            // Update timelines and SLAs
            updateTimelinesAndSLAs(disputeId, customerId, targetTier, escalationType, 
                    priority, complexityScore, escalationTimestamp);
            
            // Attach supporting documents
            if (!attachedDocuments.isEmpty()) {
                attachEscalationDocuments(disputeId, customerId, escalationType, 
                        attachedDocuments, escalatedBy);
            }
            
            // Update analytics
            updateEscalationAnalytics(disputeId, customerId, escalationType, currentTier, 
                    targetTier, priority, disputeCategory, complexityScore, escalationTriggers);
            
            // Mark as processed
            markEventAsProcessed(eventKey);
            
            auditDisputeEscalation(disputeId, customerId, escalationType, escalationReason, 
                    currentTier, targetTier, priority, processingStartTime);
            
            eventProcessedCounter.increment();
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Dispute escalation processed successfully - DisputeId: {}, Type: {}, To: {}, ProcessingTime: {}ms", 
                    disputeId, escalationType, targetTier, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            eventFailedCounter.increment();
            log.error("CRITICAL: Dispute escalation processing failed - DisputeId: {}, CustomerId: {}, Type: {}, Reason: {}, Error: {}", 
                    disputeId, customerId, escalationType, escalationReason, e.getMessage(), e);
            
            if (disputeId != null && customerId != null) {
                handleProcessingFailure(disputeId, customerId, escalationType, escalationReason, e);
            }
            
            throw new RuntimeException("Dispute escalation processing failed", e);
        } finally {
            sample.stop(processingTimer);
        }
    }
    
    private void validateDisputeEscalation(UUID disputeId, UUID customerId, String escalationType, 
                                         String escalationReason, String currentTier, String targetTier, 
                                         BigDecimal disputeAmount, LocalDateTime escalationTimestamp) {
        if (disputeId == null) {
            throw new IllegalArgumentException("Dispute ID is required");
        }
        
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        
        if (escalationType == null || escalationType.trim().isEmpty()) {
            throw new IllegalArgumentException("Escalation type is required");
        }
        
        List<String> validEscalationTypes = List.of("TIER_ESCALATION", "MANAGEMENT_ESCALATION", 
                "LEGAL_ESCALATION", "SPECIALIST_ESCALATION", "SLA_VIOLATION", "PRIORITY_ESCALATION", 
                "COMPLEXITY_ESCALATION", "CUSTOMER_REQUEST", "REGULATORY_ESCALATION");
        if (!validEscalationTypes.contains(escalationType)) {
            throw new IllegalArgumentException("Invalid escalation type: " + escalationType);
        }
        
        if (escalationReason == null || escalationReason.trim().isEmpty()) {
            throw new IllegalArgumentException("Escalation reason is required");
        }
        
        if (currentTier == null || currentTier.trim().isEmpty()) {
            throw new IllegalArgumentException("Current tier is required");
        }
        
        if (targetTier == null || targetTier.trim().isEmpty()) {
            throw new IllegalArgumentException("Target tier is required");
        }
        
        List<String> validTiers = List.of("L1", "L2", "L3", "MANAGEMENT", "LEGAL", "SPECIALIST", 
                "EXECUTIVE", "REGULATORY");
        if (!validTiers.contains(currentTier) || !validTiers.contains(targetTier)) {
            throw new IllegalArgumentException("Invalid tier: " + currentTier + " -> " + targetTier);
        }
        
        if (disputeAmount == null || disputeAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Dispute amount must be positive");
        }
        
        if (escalationTimestamp == null || escalationTimestamp.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Invalid escalation timestamp");
        }
        
        log.debug("Dispute escalation validation passed - DisputeId: {}, Type: {}", 
                disputeId, escalationType);
    }
    
    private void processEscalation(UUID disputeId, UUID customerId, String escalationType, 
                                 String escalationReason, String currentTier, String targetTier, 
                                 BigDecimal disputeAmount, String currency, String disputeCategory, 
                                 String priority, LocalDateTime escalationTimestamp, String escalatedBy, 
                                 String escalatedByRole, List<String> escalationTriggers) {
        try {
            disputeEscalationService.processEscalation(disputeId, customerId, escalationType, 
                    escalationReason, currentTier, targetTier, disputeAmount, currency, 
                    disputeCategory, priority, escalationTimestamp, escalatedBy, escalatedByRole, 
                    escalationTriggers);
            
            log.info("Escalation processed - DisputeId: {}, Type: {}, From: {} To: {}", 
                    disputeId, escalationType, currentTier, targetTier);
            
        } catch (Exception e) {
            log.error("Failed to process escalation - DisputeId: {}, Type: {}", 
                    disputeId, escalationType, e);
            throw new RuntimeException("Escalation processing failed", e);
        }
    }
    
    private void updateEscalationWorkflow(UUID disputeId, UUID customerId, String currentTier, 
                                        String targetTier, String escalationType, 
                                        Map<String, Object> escalationContext, 
                                        Boolean requiresManagerApproval, Boolean requiresLegalReview) {
        try {
            disputeWorkflowService.updateEscalationWorkflow(disputeId, customerId, currentTier, 
                    targetTier, escalationType, escalationContext, requiresManagerApproval, 
                    requiresLegalReview);
            
            log.info("Escalation workflow updated - DisputeId: {}, To: {}, ManagerApproval: {}, LegalReview: {}", 
                    disputeId, targetTier, requiresManagerApproval, requiresLegalReview);
            
        } catch (Exception e) {
            log.error("Failed to update escalation workflow - DisputeId: {}", disputeId, e);
            throw new RuntimeException("Escalation workflow update failed", e);
        }
    }
    
    private void handleSLAViolation(UUID disputeId, UUID customerId, String slaViolationType, 
                                  String escalationType, BigDecimal disputeAmount, String currency, 
                                  LocalDateTime escalationTimestamp) {
        try {
            disputeEscalationService.handleSLAViolation(disputeId, customerId, slaViolationType, 
                    escalationType, disputeAmount, currency, escalationTimestamp);
            
            log.warn("SLA violation handled - DisputeId: {}, ViolationType: {}, EscalationType: {}", 
                    disputeId, slaViolationType, escalationType);
            
        } catch (Exception e) {
            log.error("Failed to handle SLA violation - DisputeId: {}, ViolationType: {}", 
                    disputeId, slaViolationType, e);
            // Don't throw exception as SLA violation handling shouldn't block processing
        }
    }
    
    private void assignToTeam(UUID disputeId, UUID customerId, String targetTier, String escalationType, 
                            String priority, String complexityScore, String disputeCategory, 
                            Boolean requiresManagerApproval, Boolean requiresLegalReview) {
        try {
            String assignedTeam = disputeEscalationService.determineAssignedTeam(targetTier, 
                    escalationType, priority, complexityScore, disputeCategory, requiresManagerApproval, 
                    requiresLegalReview);
            
            disputeEscalationService.assignToTeam(disputeId, customerId, targetTier, 
                    assignedTeam, escalationType, priority);
            
            log.info("Dispute assigned to team - DisputeId: {}, Tier: {}, Team: {}, Priority: {}", 
                    disputeId, targetTier, assignedTeam, priority);
            
        } catch (Exception e) {
            log.error("Failed to assign to team - DisputeId: {}, Tier: {}", disputeId, targetTier, e);
            throw new RuntimeException("Team assignment failed", e);
        }
    }
    
    private void sendEscalationNotifications(UUID disputeId, UUID customerId, String escalationType, 
                                           String escalationReason, String currentTier, String targetTier, 
                                           String priority, BigDecimal disputeAmount, String currency, 
                                           String merchantId, String escalatedBy, String escalatedByRole) {
        try {
            // Notify customer
            disputeNotificationService.notifyCustomerEscalation(disputeId, customerId, 
                    escalationType, escalationReason, targetTier, priority, disputeAmount, currency);
            
            // Notify target tier team
            disputeNotificationService.notifyTargetTierTeam(disputeId, customerId, escalationType, 
                    escalationReason, currentTier, targetTier, priority, disputeAmount, currency);
            
            // Notify escalated by person/team
            disputeNotificationService.notifyEscalatedBy(disputeId, escalatedBy, escalatedByRole, 
                    escalationType, targetTier, priority);
            
            // Notify merchant if applicable
            if (merchantId != null && !merchantId.isEmpty()) {
                disputeNotificationService.notifyMerchantEscalation(disputeId, merchantId, 
                        escalationType, targetTier, disputeAmount, currency);
            }
            
            // Notify management for high priority escalations
            if ("HIGH".equals(priority) || "CRITICAL".equals(priority)) {
                disputeNotificationService.notifyManagement(disputeId, customerId, escalationType, 
                        escalationReason, targetTier, priority, disputeAmount, currency);
            }
            
            log.info("Escalation notifications sent - DisputeId: {}, Type: {}, Tier: {}", 
                    disputeId, escalationType, targetTier);
            
        } catch (Exception e) {
            log.error("Failed to send escalation notifications - DisputeId: {}, Type: {}", 
                    disputeId, escalationType, e);
            // Don't throw exception as notification failure shouldn't block processing
        }
    }
    
    private void updateTimelinesAndSLAs(UUID disputeId, UUID customerId, String targetTier, 
                                      String escalationType, String priority, String complexityScore, 
                                      LocalDateTime escalationTimestamp) {
        try {
            disputeEscalationService.updateTimelinesAndSLAs(disputeId, customerId, targetTier, 
                    escalationType, priority, complexityScore, escalationTimestamp);
            
            log.info("Timelines and SLAs updated - DisputeId: {}, Tier: {}, Priority: {}", 
                    disputeId, targetTier, priority);
            
        } catch (Exception e) {
            log.error("Failed to update timelines and SLAs - DisputeId: {}", disputeId, e);
            // Don't throw exception as timeline update failure shouldn't block processing
        }
    }
    
    private void attachEscalationDocuments(UUID disputeId, UUID customerId, String escalationType, 
                                         List<String> attachedDocuments, String escalatedBy) {
        try {
            disputeEscalationService.attachEscalationDocuments(disputeId, customerId, 
                    escalationType, attachedDocuments, escalatedBy);
            
            log.info("Escalation documents attached - DisputeId: {}, DocumentCount: {}", 
                    disputeId, attachedDocuments.size());
            
        } catch (Exception e) {
            log.error("Failed to attach escalation documents - DisputeId: {}", disputeId, e);
            // Don't throw exception as document attachment failure shouldn't block processing
        }
    }
    
    private void updateEscalationAnalytics(UUID disputeId, UUID customerId, String escalationType, 
                                         String currentTier, String targetTier, String priority, 
                                         String disputeCategory, String complexityScore, 
                                         List<String> escalationTriggers) {
        try {
            disputeAnalysisService.updateEscalationAnalytics(disputeId, customerId, escalationType, 
                    currentTier, targetTier, priority, disputeCategory, complexityScore, 
                    escalationTriggers);
            
            log.info("Escalation analytics updated - DisputeId: {}, Type: {}, From: {} To: {}", 
                    disputeId, escalationType, currentTier, targetTier);
            
        } catch (Exception e) {
            log.error("Failed to update escalation analytics - DisputeId: {}", disputeId, e);
            // Don't throw exception as analytics update failure shouldn't block processing
        }
    }
    
    private void auditDisputeEscalation(UUID disputeId, UUID customerId, String escalationType, 
                                      String escalationReason, String currentTier, String targetTier, 
                                      String priority, LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditFinancialEvent(
                    "DISPUTE_ESCALATION_PROCESSED",
                    customerId.toString(),
                    String.format("Dispute escalation processed - Type: %s, From: %s To: %s, Priority: %s, Reason: %s", 
                            escalationType, currentTier, targetTier, priority, escalationReason),
                    Map.of(
                            "disputeId", disputeId.toString(),
                            "customerId", customerId.toString(),
                            "escalationType", escalationType,
                            "escalationReason", escalationReason,
                            "currentTier", currentTier,
                            "targetTier", targetTier,
                            "priority", priority,
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit dispute escalation - DisputeId: {}", disputeId, e);
        }
    }
    
    private void handleProcessingFailure(UUID disputeId, UUID customerId, String escalationType, 
                                       String escalationReason, Exception error) {
        try {
            disputeEscalationService.recordProcessingFailure(disputeId, customerId, escalationType, 
                    escalationReason, error.getMessage());
            
            auditService.auditFinancialEvent(
                    "DISPUTE_ESCALATION_PROCESSING_FAILED",
                    customerId.toString(),
                    "Failed to process dispute escalation: " + error.getMessage(),
                    Map.of(
                            "disputeId", disputeId.toString(),
                            "customerId", customerId.toString(),
                            "escalationType", escalationType != null ? escalationType : "UNKNOWN",
                            "escalationReason", escalationReason != null ? escalationReason : "UNKNOWN",
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
        log.error("CIRCUIT BREAKER: Dispute escalation processing circuit breaker activated - Topic: {}, Error: {}", 
                topic, e.getMessage());
        
        try {
            circuitBreakerService.handleFallback("dispute-escalations", eventJson, e);
        } catch (Exception fallbackError) {
            log.error("Fallback handling failed for dispute escalation", fallbackError);
        }
    }
    
    @DltHandler
    public void handleDlt(String eventJson, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                         @Header(value = "x-original-topic", required = false) String originalTopic,
                         @Header(value = "x-error-message", required = false) String errorMessage) {
        
        log.error("CRITICAL: Dispute escalation sent to DLT - OriginalTopic: {}, Error: {}, Event: {}", 
                originalTopic, errorMessage, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String disputeId = (String) event.get("disputeId");
            String customerId = (String) event.get("customerId");
            String escalationType = (String) event.get("escalationType");
            String escalationReason = (String) event.get("escalationReason");
            
            log.error("DLT: Dispute escalation failed permanently - DisputeId: {}, CustomerId: {}, Type: {}, Reason: {} - IMMEDIATE MANUAL INTERVENTION REQUIRED", 
                    disputeId, customerId, escalationType, escalationReason);
            
            // Critical: Escalate failed escalations to management
            disputeNotificationService.sendEmergencyEscalationNotification(disputeId, customerId, 
                    escalationType, escalationReason, "DLT: " + errorMessage);
            
            disputeEscalationService.markForEmergencyReview(disputeId, customerId, 
                    escalationType, escalationReason, "DLT: " + errorMessage);
            
        } catch (Exception e) {
            log.error("Failed to parse dispute escalation DLT event: {}", eventJson, e);
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