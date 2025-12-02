package com.waqiti.kyc.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.kyc.service.DocumentRenewalService;
import com.waqiti.kyc.service.ComplianceMonitoringService;
import com.waqiti.kyc.service.CustomerNotificationService;
import com.waqiti.kyc.service.DocumentVerificationService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiredDocumentsConsumer {
    
    private final DocumentRenewalService documentRenewalService;
    private final ComplianceMonitoringService complianceMonitoringService;
    private final CustomerNotificationService customerNotificationService;
    private final DocumentVerificationService documentVerificationService;
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
        eventProcessedCounter = Counter.builder("expired_documents_processed_total")
                .description("Total number of expired document events processed")
                .register(meterRegistry);
        
        eventFailedCounter = Counter.builder("expired_documents_failed_total")
                .description("Total number of expired document events that failed processing")
                .register(meterRegistry);
        
        processingTimer = Timer.builder("expired_documents_processing_duration")
                .description("Time taken to process expired document events")
                .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = {"expired-documents", "document-expiration-alerts", "kyc-document-expired"},
        groupId = "kyc-service-expired-documents-group",
        containerFactory = "criticalKycKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @CircuitBreaker(name = "expired-documents", fallbackMethod = "handleCircuitBreakerFallback")
    @Retry(name = "expired-documents")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleExpiredDocument(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        log.info("KYC: Processing expired document - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        String expirationId = null;
        UUID customerId = null;
        String documentType = null;
        String documentId = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            expirationId = (String) event.get("expirationId");
            customerId = UUID.fromString((String) event.get("customerId"));
            documentType = (String) event.get("documentType");
            documentId = (String) event.get("documentId");
            LocalDate expirationDate = LocalDate.parse((String) event.get("expirationDate"));
            LocalDateTime detectionTimestamp = LocalDateTime.parse((String) event.get("detectionTimestamp"));
            String urgencyLevel = (String) event.get("urgencyLevel");
            Integer daysSinceExpiration = (Integer) event.get("daysSinceExpiration");
            Boolean criticalForCompliance = (Boolean) event.getOrDefault("criticalForCompliance", false);
            Boolean accountRestrictionRequired = (Boolean) event.getOrDefault("accountRestrictionRequired", false);
            String customerTier = (String) event.getOrDefault("customerTier", "STANDARD");
            String accountStatus = (String) event.getOrDefault("accountStatus", "ACTIVE");
            @SuppressWarnings("unchecked")
            List<String> affectedServices = (List<String>) event.getOrDefault("affectedServices", List.of());
            String regulatoryRequirement = (String) event.getOrDefault("regulatoryRequirement", "");
            Integer gracePeriodDays = (Integer) event.getOrDefault("gracePeriodDays", 30);
            String lastNotificationSent = (String) event.getOrDefault("lastNotificationSent", "");
            Boolean autoRenewalEligible = (Boolean) event.getOrDefault("autoRenewalEligible", false);
            String documentSource = (String) event.getOrDefault("documentSource", "MANUAL_UPLOAD");
            @SuppressWarnings("unchecked")
            Map<String, Object> documentMetadata = (Map<String, Object>) event.getOrDefault("documentMetadata", Map.of());
            String complianceImpact = (String) event.getOrDefault("complianceImpact", "LOW");
            
            log.info("Expired document - ExpirationId: {}, CustomerId: {}, DocumentType: {}, DaysExpired: {}, Urgency: {}", 
                    expirationId, customerId, documentType, daysSinceExpiration, urgencyLevel);
            
            // Check idempotency
            String eventKey = expirationId + "_" + documentId + "_" + customerId.toString();
            if (isAlreadyProcessed(eventKey)) {
                log.warn("Expired document already processed, skipping: expirationId={}, documentId={}", 
                        expirationId, documentId);
                acknowledgment.acknowledge();
                return;
            }
            
            validateExpiredDocument(expirationId, customerId, documentType, documentId, 
                    expirationDate, detectionTimestamp, urgencyLevel);
            
            // Assess compliance impact
            assessComplianceImpact(expirationId, customerId, documentType, documentId, 
                    expirationDate, daysSinceExpiration, criticalForCompliance, 
                    regulatoryRequirement, complianceImpact);
            
            // Apply account restrictions if required
            if (accountRestrictionRequired || criticalForCompliance) {
                applyAccountRestrictions(expirationId, customerId, documentType, documentId, 
                        daysSinceExpiration, affectedServices, urgencyLevel);
            }
            
            // Initiate renewal process
            initiateRenewalProcess(expirationId, customerId, documentType, documentId, 
                    expirationDate, daysSinceExpiration, autoRenewalEligible, gracePeriodDays, 
                    documentSource, documentMetadata);
            
            // Send notifications based on urgency and days expired
            sendRenewalNotifications(expirationId, customerId, documentType, documentId, 
                    expirationDate, daysSinceExpiration, urgencyLevel, lastNotificationSent, 
                    gracePeriodDays, criticalForCompliance);
            
            // Update compliance monitoring
            updateComplianceMonitoring(expirationId, customerId, documentType, documentId, 
                    expirationDate, regulatoryRequirement, complianceImpact, customerTier, 
                    accountStatus);
            
            // Schedule follow-up actions
            scheduleFollowUpActions(expirationId, customerId, documentType, documentId, 
                    daysSinceExpiration, urgencyLevel, gracePeriodDays, autoRenewalEligible);
            
            // Update document tracking
            updateDocumentTracking(expirationId, customerId, documentType, documentId, 
                    expirationDate, daysSinceExpiration, urgencyLevel, detectionTimestamp);
            
            // Mark as processed
            markEventAsProcessed(eventKey);
            
            auditExpiredDocument(expirationId, customerId, documentType, documentId, 
                    expirationDate, daysSinceExpiration, urgencyLevel, processingStartTime);
            
            eventProcessedCounter.increment();
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Expired document processed successfully - ExpirationId: {}, DocumentType: {}, DaysExpired: {}, ProcessingTime: {}ms", 
                    expirationId, documentType, daysSinceExpiration, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            eventFailedCounter.increment();
            log.error("CRITICAL: Expired document processing failed - ExpirationId: {}, CustomerId: {}, DocumentType: {}, DocumentId: {}, Error: {}", 
                    expirationId, customerId, documentType, documentId, e.getMessage(), e);
            
            if (expirationId != null && customerId != null && documentId != null) {
                handleProcessingFailure(expirationId, customerId, documentType, documentId, e);
            }
            
            throw new RuntimeException("Expired document processing failed", e);
        } finally {
            sample.stop(processingTimer);
        }
    }
    
    private void validateExpiredDocument(String expirationId, UUID customerId, String documentType, 
                                       String documentId, LocalDate expirationDate, 
                                       LocalDateTime detectionTimestamp, String urgencyLevel) {
        if (expirationId == null || expirationId.trim().isEmpty()) {
            throw new IllegalArgumentException("Expiration ID is required");
        }
        
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        
        if (documentType == null || documentType.trim().isEmpty()) {
            throw new IllegalArgumentException("Document type is required");
        }
        
        List<String> validDocumentTypes = List.of("PASSPORT", "DRIVERS_LICENSE", "NATIONAL_ID", 
                "UTILITY_BILL", "BANK_STATEMENT", "PROOF_OF_ADDRESS", "BIRTH_CERTIFICATE", 
                "SOCIAL_SECURITY_CARD", "TAX_DOCUMENT", "INCOME_VERIFICATION");
        if (!validDocumentTypes.contains(documentType)) {
            throw new IllegalArgumentException("Invalid document type: " + documentType);
        }
        
        if (documentId == null || documentId.trim().isEmpty()) {
            throw new IllegalArgumentException("Document ID is required");
        }
        
        if (expirationDate == null || expirationDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Invalid expiration date - must be in the past");
        }
        
        if (detectionTimestamp == null || detectionTimestamp.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Invalid detection timestamp");
        }
        
        if (urgencyLevel == null || urgencyLevel.trim().isEmpty()) {
            throw new IllegalArgumentException("Urgency level is required");
        }
        
        List<String> validUrgencyLevels = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
        if (!validUrgencyLevels.contains(urgencyLevel)) {
            throw new IllegalArgumentException("Invalid urgency level: " + urgencyLevel);
        }
        
        log.debug("Expired document validation passed - ExpirationId: {}, DocumentType: {}", 
                expirationId, documentType);
    }
    
    private void assessComplianceImpact(String expirationId, UUID customerId, String documentType, 
                                      String documentId, LocalDate expirationDate, 
                                      Integer daysSinceExpiration, Boolean criticalForCompliance, 
                                      String regulatoryRequirement, String complianceImpact) {
        try {
            complianceMonitoringService.assessComplianceImpact(expirationId, customerId, 
                    documentType, documentId, expirationDate, daysSinceExpiration, 
                    criticalForCompliance, regulatoryRequirement, complianceImpact);
            
            log.info("Compliance impact assessed - ExpirationId: {}, DocumentType: {}, Impact: {}", 
                    expirationId, documentType, complianceImpact);
            
        } catch (Exception e) {
            log.error("Failed to assess compliance impact - ExpirationId: {}, DocumentType: {}", 
                    expirationId, documentType, e);
            throw new RuntimeException("Compliance impact assessment failed", e);
        }
    }
    
    private void applyAccountRestrictions(String expirationId, UUID customerId, String documentType, 
                                        String documentId, Integer daysSinceExpiration, 
                                        List<String> affectedServices, String urgencyLevel) {
        try {
            documentRenewalService.applyAccountRestrictions(expirationId, customerId, documentType, 
                    documentId, daysSinceExpiration, affectedServices, urgencyLevel);
            
            log.info("Account restrictions applied - ExpirationId: {}, DocumentType: {}, Services: {}", 
                    expirationId, documentType, affectedServices.size());
            
        } catch (Exception e) {
            log.error("Failed to apply account restrictions - ExpirationId: {}, DocumentType: {}", 
                    expirationId, documentType, e);
            throw new RuntimeException("Account restrictions application failed", e);
        }
    }
    
    private void initiateRenewalProcess(String expirationId, UUID customerId, String documentType, 
                                      String documentId, LocalDate expirationDate, 
                                      Integer daysSinceExpiration, Boolean autoRenewalEligible, 
                                      Integer gracePeriodDays, String documentSource, 
                                      Map<String, Object> documentMetadata) {
        try {
            documentRenewalService.initiateRenewalProcess(expirationId, customerId, documentType, 
                    documentId, expirationDate, daysSinceExpiration, autoRenewalEligible, 
                    gracePeriodDays, documentSource, documentMetadata);
            
            log.info("Renewal process initiated - ExpirationId: {}, DocumentType: {}, AutoRenewal: {}", 
                    expirationId, documentType, autoRenewalEligible);
            
        } catch (Exception e) {
            log.error("Failed to initiate renewal process - ExpirationId: {}, DocumentType: {}", 
                    expirationId, documentType, e);
            throw new RuntimeException("Renewal process initiation failed", e);
        }
    }
    
    private void sendRenewalNotifications(String expirationId, UUID customerId, String documentType, 
                                        String documentId, LocalDate expirationDate, 
                                        Integer daysSinceExpiration, String urgencyLevel, 
                                        String lastNotificationSent, Integer gracePeriodDays, 
                                        Boolean criticalForCompliance) {
        try {
            customerNotificationService.sendRenewalNotifications(expirationId, customerId, 
                    documentType, documentId, expirationDate, daysSinceExpiration, urgencyLevel, 
                    lastNotificationSent, gracePeriodDays, criticalForCompliance);
            
            log.info("Renewal notifications sent - ExpirationId: {}, DocumentType: {}, Urgency: {}", 
                    expirationId, documentType, urgencyLevel);
            
        } catch (Exception e) {
            log.error("Failed to send renewal notifications - ExpirationId: {}, DocumentType: {}", 
                    expirationId, documentType, e);
            // Don't throw exception as notification failure shouldn't block processing
        }
    }
    
    private void updateComplianceMonitoring(String expirationId, UUID customerId, String documentType, 
                                          String documentId, LocalDate expirationDate, 
                                          String regulatoryRequirement, String complianceImpact, 
                                          String customerTier, String accountStatus) {
        try {
            complianceMonitoringService.updateComplianceMonitoring(expirationId, customerId, 
                    documentType, documentId, expirationDate, regulatoryRequirement, 
                    complianceImpact, customerTier, accountStatus);
            
            log.info("Compliance monitoring updated - ExpirationId: {}, DocumentType: {}, Impact: {}", 
                    expirationId, documentType, complianceImpact);
            
        } catch (Exception e) {
            log.error("Failed to update compliance monitoring - ExpirationId: {}, DocumentType: {}", 
                    expirationId, documentType, e);
            // Don't throw exception as monitoring update failure shouldn't block processing
        }
    }
    
    private void scheduleFollowUpActions(String expirationId, UUID customerId, String documentType, 
                                       String documentId, Integer daysSinceExpiration, 
                                       String urgencyLevel, Integer gracePeriodDays, 
                                       Boolean autoRenewalEligible) {
        try {
            documentRenewalService.scheduleFollowUpActions(expirationId, customerId, documentType, 
                    documentId, daysSinceExpiration, urgencyLevel, gracePeriodDays, autoRenewalEligible);
            
            log.info("Follow-up actions scheduled - ExpirationId: {}, DocumentType: {}, DaysExpired: {}", 
                    expirationId, documentType, daysSinceExpiration);
            
        } catch (Exception e) {
            log.error("Failed to schedule follow-up actions - ExpirationId: {}, DocumentType: {}", 
                    expirationId, documentType, e);
            // Don't throw exception as scheduling failure shouldn't block processing
        }
    }
    
    private void updateDocumentTracking(String expirationId, UUID customerId, String documentType, 
                                      String documentId, LocalDate expirationDate, 
                                      Integer daysSinceExpiration, String urgencyLevel, 
                                      LocalDateTime detectionTimestamp) {
        try {
            documentVerificationService.updateDocumentTracking(expirationId, customerId, 
                    documentType, documentId, expirationDate, daysSinceExpiration, urgencyLevel, 
                    detectionTimestamp);
            
            log.info("Document tracking updated - ExpirationId: {}, DocumentType: {}, Status: EXPIRED", 
                    expirationId, documentType);
            
        } catch (Exception e) {
            log.error("Failed to update document tracking - ExpirationId: {}, DocumentType: {}", 
                    expirationId, documentType, e);
            // Don't throw exception as tracking update failure shouldn't block processing
        }
    }
    
    private void auditExpiredDocument(String expirationId, UUID customerId, String documentType, 
                                    String documentId, LocalDate expirationDate, 
                                    Integer daysSinceExpiration, String urgencyLevel, 
                                    LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditComplianceEvent(
                    "EXPIRED_DOCUMENT_PROCESSED",
                    customerId.toString(),
                    String.format("Expired document processed - Type: %s, DaysExpired: %d, Urgency: %s", 
                            documentType, daysSinceExpiration, urgencyLevel),
                    Map.of(
                            "expirationId", expirationId,
                            "customerId", customerId.toString(),
                            "documentType", documentType,
                            "documentId", documentId,
                            "expirationDate", expirationDate.toString(),
                            "daysSinceExpiration", daysSinceExpiration,
                            "urgencyLevel", urgencyLevel,
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit expired document - ExpirationId: {}, DocumentType: {}", 
                    expirationId, documentType, e);
        }
    }
    
    private void handleProcessingFailure(String expirationId, UUID customerId, String documentType, 
                                       String documentId, Exception error) {
        try {
            documentRenewalService.recordProcessingFailure(expirationId, customerId, documentType, 
                    documentId, error.getMessage());
            
            auditService.auditComplianceEvent(
                    "EXPIRED_DOCUMENT_PROCESSING_FAILED",
                    customerId.toString(),
                    "Failed to process expired document: " + error.getMessage(),
                    Map.of(
                            "expirationId", expirationId,
                            "customerId", customerId.toString(),
                            "documentType", documentType != null ? documentType : "UNKNOWN",
                            "documentId", documentId,
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle processing failure - ExpirationId: {}, DocumentType: {}", 
                    expirationId, documentType, e);
        }
    }
    
    public void handleCircuitBreakerFallback(String eventJson, String topic, int partition, 
                                           long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("CIRCUIT BREAKER: Expired document processing circuit breaker activated - Topic: {}, Error: {}", 
                topic, e.getMessage());
        
        try {
            circuitBreakerService.handleFallback("expired-documents", eventJson, e);
        } catch (Exception fallbackError) {
            log.error("Fallback handling failed for expired document", fallbackError);
        }
    }
    
    @DltHandler
    public void handleDlt(String eventJson, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                         @Header(value = "x-original-topic", required = false) String originalTopic,
                         @Header(value = "x-error-message", required = false) String errorMessage) {
        
        log.error("CRITICAL: Expired document sent to DLT - OriginalTopic: {}, Error: {}, Event: {}", 
                originalTopic, errorMessage, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String expirationId = (String) event.get("expirationId");
            String customerId = (String) event.get("customerId");
            String documentType = (String) event.get("documentType");
            String documentId = (String) event.get("documentId");
            
            log.error("DLT: Expired document failed permanently - ExpirationId: {}, CustomerId: {}, DocumentType: {}, DocumentId: {} - IMMEDIATE MANUAL INTERVENTION REQUIRED", 
                    expirationId, customerId, documentType, documentId);
            
            // Critical: Send emergency notification for failed document processing
            customerNotificationService.sendEmergencyDocumentNotification(expirationId, customerId, 
                    documentType, documentId, "DLT: " + errorMessage);
            
            documentRenewalService.markForEmergencyReview(expirationId, customerId, 
                    documentType, documentId, "DLT: " + errorMessage);
            
        } catch (Exception e) {
            log.error("Failed to parse expired document DLT event: {}", eventJson, e);
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