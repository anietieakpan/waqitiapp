package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.service.EmergencyFreezeService;
import com.waqiti.security.service.AccountSecurityService;
import com.waqiti.security.service.SecurityNotificationService;
import com.waqiti.security.service.FraudPreventionService;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmergencyFreezeRequestsConsumer {
    
    private final EmergencyFreezeService emergencyFreezeService;
    private final AccountSecurityService accountSecurityService;
    private final SecurityNotificationService securityNotificationService;
    private final FraudPreventionService fraudPreventionService;
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
        eventProcessedCounter = Counter.builder("emergency_freeze_requests_processed_total")
                .description("Total number of emergency freeze requests processed")
                .register(meterRegistry);
        
        eventFailedCounter = Counter.builder("emergency_freeze_requests_failed_total")
                .description("Total number of emergency freeze requests that failed processing")
                .register(meterRegistry);
        
        processingTimer = Timer.builder("emergency_freeze_requests_processing_duration")
                .description("Time taken to process emergency freeze requests")
                .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = {"emergency-freeze-requests", "account-emergency-freeze", "card-emergency-freeze"},
        groupId = "security-service-emergency-freeze-group",
        containerFactory = "criticalSecurityKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @CircuitBreaker(name = "emergency-freeze-requests", fallbackMethod = "handleCircuitBreakerFallback")
    @Retry(name = "emergency-freeze-requests")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleEmergencyFreezeRequest(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        log.info("EMERGENCY: Processing freeze request - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        String freezeRequestId = null;
        UUID customerId = null;
        String freezeType = null;
        String urgencyLevel = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            freezeRequestId = (String) event.get("freezeRequestId");
            customerId = UUID.fromString((String) event.get("customerId"));
            freezeType = (String) event.get("freezeType");
            urgencyLevel = (String) event.get("urgencyLevel");
            LocalDateTime requestTimestamp = LocalDateTime.parse((String) event.get("requestTimestamp"));
            String requestSource = (String) event.get("requestSource");
            String requestedBy = (String) event.get("requestedBy");
            String freezeReason = (String) event.get("freezeReason");
            @SuppressWarnings("unchecked")
            List<String> targetAccounts = (List<String>) event.get("targetAccounts");
            @SuppressWarnings("unchecked")
            List<String> targetCards = (List<String>) event.getOrDefault("targetCards", List.of());
            String suspectedFraudType = (String) event.getOrDefault("suspectedFraudType", "");
            Boolean lawEnforcementRequest = (Boolean) event.getOrDefault("lawEnforcementRequest", false);
            String caseNumber = (String) event.getOrDefault("caseNumber", "");
            String requestingOfficer = (String) event.getOrDefault("requestingOfficer", "");
            @SuppressWarnings("unchecked")
            Map<String, Object> evidenceData = (Map<String, Object>) event.getOrDefault("evidenceData", Map.of());
            Boolean temporaryFreeze = (Boolean) event.getOrDefault("temporaryFreeze", true);
            Integer freezeDurationHours = (Integer) event.getOrDefault("freezeDurationHours", 24);
            Boolean requiresManagerApproval = (Boolean) event.getOrDefault("requiresManagerApproval", false);
            String ipAddress = (String) event.getOrDefault("ipAddress", "");
            String deviceId = (String) event.getOrDefault("deviceId", "");
            
            log.info("Emergency freeze request - RequestId: {}, CustomerId: {}, Type: {}, Urgency: {}, Accounts: {}, Cards: {}", 
                    freezeRequestId, customerId, freezeType, urgencyLevel, targetAccounts.size(), targetCards.size());
            
            // Check idempotency
            String eventKey = freezeRequestId + "_" + customerId.toString() + "_" + freezeType;
            if (isAlreadyProcessed(eventKey)) {
                log.warn("Emergency freeze request already processed, skipping: requestId={}, type={}", 
                        freezeRequestId, freezeType);
                acknowledgment.acknowledge();
                return;
            }
            
            validateFreezeRequest(freezeRequestId, customerId, freezeType, urgencyLevel, 
                    requestTimestamp, targetAccounts);
            
            // Execute immediate freeze actions
            executeImmediateFreeze(freezeRequestId, customerId, freezeType, urgencyLevel, 
                    targetAccounts, targetCards, freezeReason, temporaryFreeze, freezeDurationHours);
            
            // Handle law enforcement requests
            if (lawEnforcementRequest) {
                handleLawEnforcementFreeze(freezeRequestId, customerId, caseNumber, 
                        requestingOfficer, targetAccounts, targetCards, evidenceData);
            }
            
            // Perform fraud analysis
            if (suspectedFraudType != null && !suspectedFraudType.isEmpty()) {
                performFraudAnalysis(freezeRequestId, customerId, suspectedFraudType, 
                        targetAccounts, targetCards, evidenceData, ipAddress, deviceId);
            }
            
            // Handle manager approval if required
            if (requiresManagerApproval) {
                requestManagerApproval(freezeRequestId, customerId, freezeType, freezeReason, 
                        targetAccounts, targetCards, urgencyLevel);
            }
            
            // Send emergency notifications
            sendEmergencyNotifications(freezeRequestId, customerId, freezeType, urgencyLevel, 
                    freezeReason, targetAccounts, targetCards, lawEnforcementRequest, 
                    requestedBy, requestSource);
            
            // Setup monitoring and alerts
            setupEmergencyMonitoring(freezeRequestId, customerId, freezeType, targetAccounts, 
                    targetCards, suspectedFraudType, ipAddress, deviceId);
            
            // Update security posture
            updateSecurityPosture(freezeRequestId, customerId, freezeType, urgencyLevel, 
                    suspectedFraudType, evidenceData);
            
            // Mark as processed
            markEventAsProcessed(eventKey);
            
            auditEmergencyFreeze(freezeRequestId, customerId, freezeType, urgencyLevel, 
                    targetAccounts, targetCards, processingStartTime);
            
            eventProcessedCounter.increment();
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Emergency freeze request processed successfully - RequestId: {}, Type: {}, Urgency: {}, ProcessingTime: {}ms", 
                    freezeRequestId, freezeType, urgencyLevel, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            eventFailedCounter.increment();
            log.error("CRITICAL: Emergency freeze request processing failed - RequestId: {}, CustomerId: {}, Type: {}, Urgency: {}, Error: {}", 
                    freezeRequestId, customerId, freezeType, urgencyLevel, e.getMessage(), e);
            
            if (freezeRequestId != null && customerId != null) {
                handleProcessingFailure(freezeRequestId, customerId, freezeType, urgencyLevel, e);
            }
            
            throw new RuntimeException("Emergency freeze request processing failed", e);
        } finally {
            sample.stop(processingTimer);
        }
    }
    
    private void validateFreezeRequest(String freezeRequestId, UUID customerId, String freezeType, 
                                     String urgencyLevel, LocalDateTime requestTimestamp, 
                                     List<String> targetAccounts) {
        if (freezeRequestId == null || freezeRequestId.trim().isEmpty()) {
            throw new IllegalArgumentException("Freeze request ID is required");
        }
        
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        
        if (freezeType == null || freezeType.trim().isEmpty()) {
            throw new IllegalArgumentException("Freeze type is required");
        }
        
        List<String> validFreezeTypes = List.of("ACCOUNT_FREEZE", "CARD_FREEZE", "FULL_FREEZE", 
                "TRANSACTION_FREEZE", "WITHDRAWAL_FREEZE", "PAYMENT_FREEZE");
        if (!validFreezeTypes.contains(freezeType)) {
            throw new IllegalArgumentException("Invalid freeze type: " + freezeType);
        }
        
        if (urgencyLevel == null || urgencyLevel.trim().isEmpty()) {
            throw new IllegalArgumentException("Urgency level is required");
        }
        
        List<String> validUrgencyLevels = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
        if (!validUrgencyLevels.contains(urgencyLevel)) {
            throw new IllegalArgumentException("Invalid urgency level: " + urgencyLevel);
        }
        
        if (requestTimestamp == null || requestTimestamp.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Invalid request timestamp");
        }
        
        if (targetAccounts == null || targetAccounts.isEmpty()) {
            throw new IllegalArgumentException("Target accounts list cannot be empty");
        }
        
        log.debug("Emergency freeze request validation passed - RequestId: {}, Type: {}", 
                freezeRequestId, freezeType);
    }
    
    private void executeImmediateFreeze(String freezeRequestId, UUID customerId, String freezeType, 
                                      String urgencyLevel, List<String> targetAccounts, 
                                      List<String> targetCards, String freezeReason, 
                                      Boolean temporaryFreeze, Integer freezeDurationHours) {
        try {
            emergencyFreezeService.executeImmediateFreeze(freezeRequestId, customerId, freezeType, 
                    urgencyLevel, targetAccounts, targetCards, freezeReason, temporaryFreeze, 
                    freezeDurationHours);
            
            log.info("Immediate freeze executed - RequestId: {}, Type: {}, Accounts: {}, Cards: {}", 
                    freezeRequestId, freezeType, targetAccounts.size(), targetCards.size());
            
        } catch (Exception e) {
            log.error("Failed to execute immediate freeze - RequestId: {}, Type: {}", 
                    freezeRequestId, freezeType, e);
            throw new RuntimeException("Immediate freeze execution failed", e);
        }
    }
    
    private void handleLawEnforcementFreeze(String freezeRequestId, UUID customerId, String caseNumber, 
                                          String requestingOfficer, List<String> targetAccounts, 
                                          List<String> targetCards, Map<String, Object> evidenceData) {
        try {
            emergencyFreezeService.handleLawEnforcementFreeze(freezeRequestId, customerId, 
                    caseNumber, requestingOfficer, targetAccounts, targetCards, evidenceData);
            
            log.info("Law enforcement freeze processed - RequestId: {}, CaseNumber: {}, Officer: {}", 
                    freezeRequestId, caseNumber, requestingOfficer);
            
        } catch (Exception e) {
            log.error("Failed to handle law enforcement freeze - RequestId: {}, CaseNumber: {}", 
                    freezeRequestId, caseNumber, e);
            throw new RuntimeException("Law enforcement freeze handling failed", e);
        }
    }
    
    private void performFraudAnalysis(String freezeRequestId, UUID customerId, String suspectedFraudType, 
                                    List<String> targetAccounts, List<String> targetCards, 
                                    Map<String, Object> evidenceData, String ipAddress, String deviceId) {
        try {
            fraudPreventionService.performEmergencyFraudAnalysis(freezeRequestId, customerId, 
                    suspectedFraudType, targetAccounts, targetCards, evidenceData, ipAddress, deviceId);
            
            log.info("Fraud analysis completed - RequestId: {}, FraudType: {}", 
                    freezeRequestId, suspectedFraudType);
            
        } catch (Exception e) {
            log.error("Failed to perform fraud analysis - RequestId: {}, FraudType: {}", 
                    freezeRequestId, suspectedFraudType, e);
            // Don't throw exception as fraud analysis failure shouldn't block processing
        }
    }
    
    private void requestManagerApproval(String freezeRequestId, UUID customerId, String freezeType, 
                                      String freezeReason, List<String> targetAccounts, 
                                      List<String> targetCards, String urgencyLevel) {
        try {
            emergencyFreezeService.requestManagerApproval(freezeRequestId, customerId, freezeType, 
                    freezeReason, targetAccounts, targetCards, urgencyLevel);
            
            log.info("Manager approval requested - RequestId: {}, Type: {}, Urgency: {}", 
                    freezeRequestId, freezeType, urgencyLevel);
            
        } catch (Exception e) {
            log.error("Failed to request manager approval - RequestId: {}, Type: {}", 
                    freezeRequestId, freezeType, e);
            // Don't throw exception as approval request failure shouldn't block emergency freeze
        }
    }
    
    private void sendEmergencyNotifications(String freezeRequestId, UUID customerId, String freezeType, 
                                          String urgencyLevel, String freezeReason, 
                                          List<String> targetAccounts, List<String> targetCards, 
                                          Boolean lawEnforcementRequest, String requestedBy, String requestSource) {
        try {
            // Notify customer
            securityNotificationService.notifyCustomerEmergencyFreeze(freezeRequestId, customerId, 
                    freezeType, urgencyLevel, freezeReason, targetAccounts, targetCards);
            
            // Notify security team
            securityNotificationService.notifySecurityTeamEmergencyFreeze(freezeRequestId, customerId, 
                    freezeType, urgencyLevel, freezeReason, targetAccounts, targetCards, 
                    lawEnforcementRequest, requestedBy);
            
            // Notify fraud team
            securityNotificationService.notifyFraudTeamEmergencyFreeze(freezeRequestId, customerId, 
                    freezeType, freezeReason, targetAccounts, targetCards);
            
            // Notify operations team
            securityNotificationService.notifyOperationsTeamEmergencyFreeze(freezeRequestId, 
                    customerId, freezeType, urgencyLevel, targetAccounts, targetCards);
            
            // Notify compliance team for law enforcement requests
            if (lawEnforcementRequest) {
                securityNotificationService.notifyComplianceTeamLawEnforcement(freezeRequestId, 
                        customerId, freezeReason, targetAccounts, targetCards, requestedBy);
            }
            
            log.info("Emergency notifications sent - RequestId: {}, Type: {}, LawEnforcement: {}", 
                    freezeRequestId, freezeType, lawEnforcementRequest);
            
        } catch (Exception e) {
            log.error("Failed to send emergency notifications - RequestId: {}, Type: {}", 
                    freezeRequestId, freezeType, e);
            // Don't throw exception as notification failure shouldn't block processing
        }
    }
    
    private void setupEmergencyMonitoring(String freezeRequestId, UUID customerId, String freezeType, 
                                        List<String> targetAccounts, List<String> targetCards, 
                                        String suspectedFraudType, String ipAddress, String deviceId) {
        try {
            accountSecurityService.setupEmergencyMonitoring(freezeRequestId, customerId, freezeType, 
                    targetAccounts, targetCards, suspectedFraudType, ipAddress, deviceId);
            
            log.info("Emergency monitoring setup completed - RequestId: {}, Type: {}", 
                    freezeRequestId, freezeType);
            
        } catch (Exception e) {
            log.error("Failed to setup emergency monitoring - RequestId: {}, Type: {}", 
                    freezeRequestId, freezeType, e);
            // Don't throw exception as monitoring setup failure shouldn't block processing
        }
    }
    
    private void updateSecurityPosture(String freezeRequestId, UUID customerId, String freezeType, 
                                     String urgencyLevel, String suspectedFraudType, 
                                     Map<String, Object> evidenceData) {
        try {
            accountSecurityService.updateSecurityPostureForEmergencyFreeze(freezeRequestId, 
                    customerId, freezeType, urgencyLevel, suspectedFraudType, evidenceData);
            
            log.info("Security posture updated - RequestId: {}, Type: {}, Urgency: {}", 
                    freezeRequestId, freezeType, urgencyLevel);
            
        } catch (Exception e) {
            log.error("Failed to update security posture - RequestId: {}, Type: {}", 
                    freezeRequestId, freezeType, e);
            // Don't throw exception as posture update failure shouldn't block processing
        }
    }
    
    private void auditEmergencyFreeze(String freezeRequestId, UUID customerId, String freezeType, 
                                    String urgencyLevel, List<String> targetAccounts, 
                                    List<String> targetCards, LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditSecurityEvent(
                    "EMERGENCY_FREEZE_REQUEST_PROCESSED",
                    customerId.toString(),
                    String.format("Emergency freeze request processed - Type: %s, Urgency: %s, Accounts: %d, Cards: %d", 
                            freezeType, urgencyLevel, targetAccounts.size(), targetCards.size()),
                    Map.of(
                            "freezeRequestId", freezeRequestId,
                            "customerId", customerId.toString(),
                            "freezeType", freezeType,
                            "urgencyLevel", urgencyLevel,
                            "targetAccountsCount", targetAccounts.size(),
                            "targetCardsCount", targetCards.size(),
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit emergency freeze - RequestId: {}, Type: {}", 
                    freezeRequestId, freezeType, e);
        }
    }
    
    private void handleProcessingFailure(String freezeRequestId, UUID customerId, String freezeType, 
                                       String urgencyLevel, Exception error) {
        try {
            emergencyFreezeService.recordProcessingFailure(freezeRequestId, customerId, freezeType, 
                    urgencyLevel, error.getMessage());
            
            auditService.auditSecurityEvent(
                    "EMERGENCY_FREEZE_REQUEST_PROCESSING_FAILED",
                    customerId.toString(),
                    "Failed to process emergency freeze request: " + error.getMessage(),
                    Map.of(
                            "freezeRequestId", freezeRequestId,
                            "customerId", customerId.toString(),
                            "freezeType", freezeType != null ? freezeType : "UNKNOWN",
                            "urgencyLevel", urgencyLevel != null ? urgencyLevel : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle processing failure - RequestId: {}, Type: {}", 
                    freezeRequestId, freezeType, e);
        }
    }
    
    public void handleCircuitBreakerFallback(String eventJson, String topic, int partition, 
                                           long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("CIRCUIT BREAKER: Emergency freeze request processing circuit breaker activated - Topic: {}, Error: {}", 
                topic, e.getMessage());
        
        try {
            circuitBreakerService.handleFallback("emergency-freeze-requests", eventJson, e);
        } catch (Exception fallbackError) {
            log.error("Fallback handling failed for emergency freeze request", fallbackError);
        }
    }
    
    @DltHandler
    public void handleDlt(String eventJson, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                         @Header(value = "x-original-topic", required = false) String originalTopic,
                         @Header(value = "x-error-message", required = false) String errorMessage) {
        
        log.error("CRITICAL: Emergency freeze request sent to DLT - OriginalTopic: {}, Error: {}, Event: {}", 
                originalTopic, errorMessage, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String freezeRequestId = (String) event.get("freezeRequestId");
            String customerId = (String) event.get("customerId");
            String freezeType = (String) event.get("freezeType");
            String urgencyLevel = (String) event.get("urgencyLevel");
            
            log.error("DLT: Emergency freeze request failed permanently - RequestId: {}, CustomerId: {}, Type: {}, Urgency: {} - IMMEDIATE MANUAL INTERVENTION REQUIRED", 
                    freezeRequestId, customerId, freezeType, urgencyLevel);
            
            // Critical: Send emergency notification for failed freeze requests
            securityNotificationService.sendEmergencyFreezeFailureNotification(freezeRequestId, 
                    customerId, freezeType, urgencyLevel, "DLT: " + errorMessage);
            
            emergencyFreezeService.markForEmergencyReview(freezeRequestId, customerId, 
                    freezeType, urgencyLevel, "DLT: " + errorMessage);
            
        } catch (Exception e) {
            log.error("Failed to parse emergency freeze request DLT event: {}", eventJson, e);
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