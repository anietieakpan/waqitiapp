package com.waqiti.account.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.account.service.AccountReactivationService;
import com.waqiti.account.service.AccountSecurityService;
import com.waqiti.account.service.AccountNotificationService;
import com.waqiti.account.service.ComplianceValidationService;
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
public class DormantAccountReactivationConsumer {
    
    private final AccountReactivationService accountReactivationService;
    private final AccountSecurityService accountSecurityService;
    private final AccountNotificationService accountNotificationService;
    private final ComplianceValidationService complianceValidationService;
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
        eventProcessedCounter = Counter.builder("dormant_account_reactivation_processed_total")
                .description("Total number of dormant account reactivation events processed")
                .register(meterRegistry);
        
        eventFailedCounter = Counter.builder("dormant_account_reactivation_failed_total")
                .description("Total number of dormant account reactivation events that failed processing")
                .register(meterRegistry);
        
        processingTimer = Timer.builder("dormant_account_reactivation_processing_duration")
                .description("Time taken to process dormant account reactivation events")
                .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = {"dormant-account-reactivation", "account-reactivation-requests", "dormant-account-restore"},
        groupId = "account-service-dormant-reactivation-group",
        containerFactory = "criticalAccountKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @CircuitBreaker(name = "dormant-account-reactivation", fallbackMethod = "handleCircuitBreakerFallback")
    @Retry(name = "dormant-account-reactivation")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleDormantAccountReactivation(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        log.info("ACCOUNT: Processing dormant account reactivation - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        String reactivationId = null;
        UUID customerId = null;
        String accountId = null;
        String reactivationType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            reactivationId = (String) event.get("reactivationId");
            customerId = UUID.fromString((String) event.get("customerId"));
            accountId = (String) event.get("accountId");
            reactivationType = (String) event.get("reactivationType");
            LocalDateTime reactivationTimestamp = LocalDateTime.parse((String) event.get("reactivationTimestamp"));
            String requestSource = (String) event.get("requestSource");
            String requestedBy = (String) event.getOrDefault("requestedBy", "CUSTOMER");
            Integer dormancyDurationDays = (Integer) event.get("dormancyDurationDays");
            LocalDateTime lastActivityDate = LocalDateTime.parse((String) event.get("lastActivityDate"));
            String dormancyReason = (String) event.getOrDefault("dormancyReason", "INACTIVITY");
            @SuppressWarnings("unchecked")
            List<String> verificationMethods = (List<String>) event.get("verificationMethods");
            Boolean identityVerified = (Boolean) event.getOrDefault("identityVerified", false);
            Boolean kycUpdateRequired = (Boolean) event.getOrDefault("kycUpdateRequired", false);
            Boolean complianceCheckRequired = (Boolean) event.getOrDefault("complianceCheckRequired", false);
            String accountType = (String) event.get("accountType");
            String customerTier = (String) event.getOrDefault("customerTier", "STANDARD");
            @SuppressWarnings("unchecked")
            Map<String, Object> verificationEvidence = (Map<String, Object>) event.getOrDefault("verificationEvidence", Map.of());
            @SuppressWarnings("unchecked")
            List<String> requiredActions = (List<String>) event.getOrDefault("requiredActions", List.of());
            Boolean autoReactivationEligible = (Boolean) event.getOrDefault("autoReactivationEligible", false);
            
            log.info("Dormant account reactivation - ReactivationId: {}, CustomerId: {}, AccountId: {}, Type: {}, DormancyDays: {}", 
                    reactivationId, customerId, accountId, reactivationType, dormancyDurationDays);
            
            // Check idempotency
            String eventKey = reactivationId + "_" + accountId + "_" + customerId.toString();
            if (isAlreadyProcessed(eventKey)) {
                log.warn("Dormant account reactivation already processed, skipping: reactivationId={}, accountId={}", 
                        reactivationId, accountId);
                acknowledgment.acknowledge();
                return;
            }
            
            validateReactivationRequest(reactivationId, customerId, accountId, reactivationType, 
                    reactivationTimestamp, dormancyDurationDays, verificationMethods);
            
            // Perform security verification
            performSecurityVerification(reactivationId, customerId, accountId, reactivationType, 
                    verificationMethods, identityVerified, verificationEvidence, requestSource);
            
            // Validate compliance requirements
            if (complianceCheckRequired) {
                validateComplianceRequirements(reactivationId, customerId, accountId, 
                        accountType, customerTier, dormancyDurationDays, kycUpdateRequired);
            }
            
            // Process reactivation based on type
            processReactivationByType(reactivationId, customerId, accountId, reactivationType, 
                    reactivationTimestamp, dormancyDurationDays, lastActivityDate, dormancyReason, 
                    autoReactivationEligible, requiredActions);
            
            // Restore account services
            restoreAccountServices(reactivationId, customerId, accountId, accountType, 
                    customerTier, dormancyDurationDays, requiredActions);
            
            // Update account status
            updateAccountStatus(reactivationId, customerId, accountId, reactivationType, 
                    reactivationTimestamp, identityVerified, kycUpdateRequired);
            
            // Setup enhanced monitoring for reactivated accounts
            setupEnhancedMonitoring(reactivationId, customerId, accountId, dormancyDurationDays, 
                    reactivationType, lastActivityDate);
            
            // Send reactivation notifications
            sendReactivationNotifications(reactivationId, customerId, accountId, reactivationType, 
                    reactivationTimestamp, requiredActions, kycUpdateRequired);
            
            // Update reactivation analytics
            updateReactivationAnalytics(reactivationId, customerId, accountId, reactivationType, 
                    dormancyDurationDays, dormancyReason, autoReactivationEligible, customerTier);
            
            // Mark as processed
            markEventAsProcessed(eventKey);
            
            auditAccountReactivation(reactivationId, customerId, accountId, reactivationType, 
                    dormancyDurationDays, processingStartTime);
            
            eventProcessedCounter.increment();
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Dormant account reactivation processed successfully - ReactivationId: {}, AccountId: {}, Type: {}, ProcessingTime: {}ms", 
                    reactivationId, accountId, reactivationType, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            eventFailedCounter.increment();
            log.error("CRITICAL: Dormant account reactivation processing failed - ReactivationId: {}, CustomerId: {}, AccountId: {}, Type: {}, Error: {}", 
                    reactivationId, customerId, accountId, reactivationType, e.getMessage(), e);
            
            if (reactivationId != null && customerId != null && accountId != null) {
                handleProcessingFailure(reactivationId, customerId, accountId, reactivationType, e);
            }
            
            throw new RuntimeException("Dormant account reactivation processing failed", e);
        } finally {
            sample.stop(processingTimer);
        }
    }
    
    private void validateReactivationRequest(String reactivationId, UUID customerId, String accountId, 
                                           String reactivationType, LocalDateTime reactivationTimestamp, 
                                           Integer dormancyDurationDays, List<String> verificationMethods) {
        if (reactivationId == null || reactivationId.trim().isEmpty()) {
            throw new IllegalArgumentException("Reactivation ID is required");
        }
        
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        
        if (accountId == null || accountId.trim().isEmpty()) {
            throw new IllegalArgumentException("Account ID is required");
        }
        
        if (reactivationType == null || reactivationType.trim().isEmpty()) {
            throw new IllegalArgumentException("Reactivation type is required");
        }
        
        List<String> validReactivationTypes = List.of("CUSTOMER_REQUEST", "AUTOMATIC", 
                "REGULATORY_REQUIRED", "SECURITY_CLEARED", "COMPLIANCE_CLEARED", "MANUAL_OVERRIDE");
        if (!validReactivationTypes.contains(reactivationType)) {
            throw new IllegalArgumentException("Invalid reactivation type: " + reactivationType);
        }
        
        if (reactivationTimestamp == null || reactivationTimestamp.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Invalid reactivation timestamp");
        }
        
        if (dormancyDurationDays == null || dormancyDurationDays <= 0) {
            throw new IllegalArgumentException("Dormancy duration must be positive");
        }
        
        if (verificationMethods == null || verificationMethods.isEmpty()) {
            throw new IllegalArgumentException("Verification methods list cannot be empty");
        }
        
        log.debug("Reactivation request validation passed - ReactivationId: {}, AccountId: {}", 
                reactivationId, accountId);
    }
    
    private void performSecurityVerification(String reactivationId, UUID customerId, String accountId, 
                                           String reactivationType, List<String> verificationMethods, 
                                           Boolean identityVerified, Map<String, Object> verificationEvidence, 
                                           String requestSource) {
        try {
            boolean verificationPassed = accountSecurityService.performSecurityVerification(
                    reactivationId, customerId, accountId, reactivationType, verificationMethods, 
                    identityVerified, verificationEvidence, requestSource);
            
            if (!verificationPassed) {
                throw new RuntimeException("Security verification failed for account reactivation");
            }
            
            log.info("Security verification passed - ReactivationId: {}, AccountId: {}", 
                    reactivationId, accountId);
            
        } catch (Exception e) {
            log.error("Security verification failed - ReactivationId: {}, AccountId: {}", 
                    reactivationId, accountId, e);
            throw new RuntimeException("Security verification failed", e);
        }
    }
    
    private void validateComplianceRequirements(String reactivationId, UUID customerId, String accountId, 
                                               String accountType, String customerTier, 
                                               Integer dormancyDurationDays, Boolean kycUpdateRequired) {
        try {
            boolean complianceValid = complianceValidationService.validateReactivationCompliance(
                    reactivationId, customerId, accountId, accountType, customerTier, 
                    dormancyDurationDays, kycUpdateRequired);
            
            if (!complianceValid) {
                throw new RuntimeException("Compliance validation failed for account reactivation");
            }
            
            log.info("Compliance validation passed - ReactivationId: {}, AccountId: {}", 
                    reactivationId, accountId);
            
        } catch (Exception e) {
            log.error("Compliance validation failed - ReactivationId: {}, AccountId: {}", 
                    reactivationId, accountId, e);
            throw new RuntimeException("Compliance validation failed", e);
        }
    }
    
    private void processReactivationByType(String reactivationId, UUID customerId, String accountId, 
                                         String reactivationType, LocalDateTime reactivationTimestamp, 
                                         Integer dormancyDurationDays, LocalDateTime lastActivityDate, 
                                         String dormancyReason, Boolean autoReactivationEligible, 
                                         List<String> requiredActions) {
        try {
            switch (reactivationType) {
                case "CUSTOMER_REQUEST" -> accountReactivationService.processCustomerRequestReactivation(
                        reactivationId, customerId, accountId, reactivationTimestamp, 
                        dormancyDurationDays, requiredActions);
                
                case "AUTOMATIC" -> accountReactivationService.processAutomaticReactivation(
                        reactivationId, customerId, accountId, reactivationTimestamp, 
                        dormancyDurationDays, autoReactivationEligible);
                
                case "REGULATORY_REQUIRED" -> accountReactivationService.processRegulatoryReactivation(
                        reactivationId, customerId, accountId, reactivationTimestamp, 
                        dormancyDurationDays, requiredActions);
                
                case "SECURITY_CLEARED" -> accountReactivationService.processSecurityClearedReactivation(
                        reactivationId, customerId, accountId, reactivationTimestamp, dormancyReason);
                
                case "COMPLIANCE_CLEARED" -> accountReactivationService.processComplianceClearedReactivation(
                        reactivationId, customerId, accountId, reactivationTimestamp, requiredActions);
                
                case "MANUAL_OVERRIDE" -> accountReactivationService.processManualOverrideReactivation(
                        reactivationId, customerId, accountId, reactivationTimestamp, dormancyReason);
                
                default -> log.warn("Unknown reactivation type: {}", reactivationType);
            }
            
            log.info("Reactivation processed by type - ReactivationId: {}, Type: {}", 
                    reactivationId, reactivationType);
            
        } catch (Exception e) {
            log.error("Failed to process reactivation by type - ReactivationId: {}, Type: {}", 
                    reactivationId, reactivationType, e);
            throw new RuntimeException("Reactivation type processing failed", e);
        }
    }
    
    private void restoreAccountServices(String reactivationId, UUID customerId, String accountId, 
                                      String accountType, String customerTier, Integer dormancyDurationDays, 
                                      List<String> requiredActions) {
        try {
            accountReactivationService.restoreAccountServices(reactivationId, customerId, accountId, 
                    accountType, customerTier, dormancyDurationDays, requiredActions);
            
            log.info("Account services restored - ReactivationId: {}, AccountId: {}, Type: {}", 
                    reactivationId, accountId, accountType);
            
        } catch (Exception e) {
            log.error("Failed to restore account services - ReactivationId: {}, AccountId: {}", 
                    reactivationId, accountId, e);
            throw new RuntimeException("Account services restoration failed", e);
        }
    }
    
    private void updateAccountStatus(String reactivationId, UUID customerId, String accountId, 
                                   String reactivationType, LocalDateTime reactivationTimestamp, 
                                   Boolean identityVerified, Boolean kycUpdateRequired) {
        try {
            String newStatus = determineAccountStatus(reactivationType, identityVerified, kycUpdateRequired);
            
            accountReactivationService.updateAccountStatus(reactivationId, customerId, accountId, 
                    newStatus, reactivationType, reactivationTimestamp);
            
            log.info("Account status updated - ReactivationId: {}, AccountId: {}, NewStatus: {}", 
                    reactivationId, accountId, newStatus);
            
        } catch (Exception e) {
            log.error("Failed to update account status - ReactivationId: {}, AccountId: {}", 
                    reactivationId, accountId, e);
            throw new RuntimeException("Account status update failed", e);
        }
    }
    
    private void setupEnhancedMonitoring(String reactivationId, UUID customerId, String accountId, 
                                       Integer dormancyDurationDays, String reactivationType, 
                                       LocalDateTime lastActivityDate) {
        try {
            accountSecurityService.setupEnhancedMonitoring(reactivationId, customerId, accountId, 
                    dormancyDurationDays, reactivationType, lastActivityDate);
            
            log.info("Enhanced monitoring setup completed - ReactivationId: {}, AccountId: {}", 
                    reactivationId, accountId);
            
        } catch (Exception e) {
            log.error("Failed to setup enhanced monitoring - ReactivationId: {}, AccountId: {}", 
                    reactivationId, accountId, e);
            // Don't throw exception as monitoring setup failure shouldn't block processing
        }
    }
    
    private void sendReactivationNotifications(String reactivationId, UUID customerId, String accountId, 
                                             String reactivationType, LocalDateTime reactivationTimestamp, 
                                             List<String> requiredActions, Boolean kycUpdateRequired) {
        try {
            accountNotificationService.sendReactivationNotification(reactivationId, customerId, 
                    accountId, reactivationType, reactivationTimestamp, requiredActions, kycUpdateRequired);
            
            log.info("Reactivation notifications sent - ReactivationId: {}, AccountId: {}", 
                    reactivationId, accountId);
            
        } catch (Exception e) {
            log.error("Failed to send reactivation notifications - ReactivationId: {}, AccountId: {}", 
                    reactivationId, accountId, e);
            // Don't throw exception as notification failure shouldn't block processing
        }
    }
    
    private void updateReactivationAnalytics(String reactivationId, UUID customerId, String accountId, 
                                           String reactivationType, Integer dormancyDurationDays, 
                                           String dormancyReason, Boolean autoReactivationEligible, 
                                           String customerTier) {
        try {
            accountReactivationService.updateReactivationAnalytics(reactivationId, customerId, 
                    accountId, reactivationType, dormancyDurationDays, dormancyReason, 
                    autoReactivationEligible, customerTier);
            
            log.info("Reactivation analytics updated - ReactivationId: {}, AccountId: {}, Type: {}", 
                    reactivationId, accountId, reactivationType);
            
        } catch (Exception e) {
            log.error("Failed to update reactivation analytics - ReactivationId: {}, AccountId: {}", 
                    reactivationId, accountId, e);
            // Don't throw exception as analytics update failure shouldn't block processing
        }
    }
    
    private String determineAccountStatus(String reactivationType, Boolean identityVerified, 
                                        Boolean kycUpdateRequired) {
        if (!identityVerified || kycUpdateRequired) {
            return "REACTIVATED_PENDING_VERIFICATION";
        }
        
        return switch (reactivationType) {
            case "CUSTOMER_REQUEST", "AUTOMATIC" -> "ACTIVE";
            case "REGULATORY_REQUIRED" -> "ACTIVE_REGULATORY_MONITORED";
            case "SECURITY_CLEARED" -> "ACTIVE_SECURITY_MONITORED";
            case "COMPLIANCE_CLEARED" -> "ACTIVE_COMPLIANCE_MONITORED";
            case "MANUAL_OVERRIDE" -> "ACTIVE_MANUAL_OVERRIDE";
            default -> "ACTIVE";
        };
    }
    
    private void auditAccountReactivation(String reactivationId, UUID customerId, String accountId, 
                                        String reactivationType, Integer dormancyDurationDays, 
                                        LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditAccountEvent(
                    "DORMANT_ACCOUNT_REACTIVATION_PROCESSED",
                    customerId.toString(),
                    String.format("Dormant account reactivation processed - AccountId: %s, Type: %s, DormancyDays: %d", 
                            accountId, reactivationType, dormancyDurationDays),
                    Map.of(
                            "reactivationId", reactivationId,
                            "customerId", customerId.toString(),
                            "accountId", accountId,
                            "reactivationType", reactivationType,
                            "dormancyDurationDays", dormancyDurationDays,
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit account reactivation - ReactivationId: {}, AccountId: {}", 
                    reactivationId, accountId, e);
        }
    }
    
    private void handleProcessingFailure(String reactivationId, UUID customerId, String accountId, 
                                       String reactivationType, Exception error) {
        try {
            accountReactivationService.recordProcessingFailure(reactivationId, customerId, 
                    accountId, reactivationType, error.getMessage());
            
            auditService.auditAccountEvent(
                    "DORMANT_ACCOUNT_REACTIVATION_PROCESSING_FAILED",
                    customerId.toString(),
                    "Failed to process dormant account reactivation: " + error.getMessage(),
                    Map.of(
                            "reactivationId", reactivationId,
                            "customerId", customerId.toString(),
                            "accountId", accountId,
                            "reactivationType", reactivationType != null ? reactivationType : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle processing failure - ReactivationId: {}, AccountId: {}", 
                    reactivationId, accountId, e);
        }
    }
    
    public void handleCircuitBreakerFallback(String eventJson, String topic, int partition, 
                                           long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("CIRCUIT BREAKER: Dormant account reactivation processing circuit breaker activated - Topic: {}, Error: {}", 
                topic, e.getMessage());
        
        try {
            circuitBreakerService.handleFallback("dormant-account-reactivation", eventJson, e);
        } catch (Exception fallbackError) {
            log.error("Fallback handling failed for dormant account reactivation", fallbackError);
        }
    }
    
    @DltHandler
    public void handleDlt(String eventJson, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                         @Header(value = "x-original-topic", required = false) String originalTopic,
                         @Header(value = "x-error-message", required = false) String errorMessage) {
        
        log.error("CRITICAL: Dormant account reactivation sent to DLT - OriginalTopic: {}, Error: {}, Event: {}", 
                originalTopic, errorMessage, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String reactivationId = (String) event.get("reactivationId");
            String customerId = (String) event.get("customerId");
            String accountId = (String) event.get("accountId");
            String reactivationType = (String) event.get("reactivationType");
            
            log.error("DLT: Dormant account reactivation failed permanently - ReactivationId: {}, CustomerId: {}, AccountId: {}, Type: {} - IMMEDIATE MANUAL INTERVENTION REQUIRED", 
                    reactivationId, customerId, accountId, reactivationType);
            
            // Critical: Send emergency notification for failed reactivations
            accountNotificationService.sendEmergencyReactivationNotification(reactivationId, 
                    customerId, accountId, reactivationType, "DLT: " + errorMessage);
            
            accountReactivationService.markForEmergencyReview(reactivationId, customerId, 
                    accountId, reactivationType, "DLT: " + errorMessage);
            
        } catch (Exception e) {
            log.error("Failed to parse dormant account reactivation DLT event: {}", eventJson, e);
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