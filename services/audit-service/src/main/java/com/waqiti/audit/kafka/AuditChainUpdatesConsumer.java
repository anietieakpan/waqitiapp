package com.waqiti.audit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.audit.domain.AuditEvent;
import com.waqiti.audit.domain.DataLineage;
import com.waqiti.audit.repository.AuditEventRepository;
import com.waqiti.audit.repository.DataLineageRepository;
import com.waqiti.audit.service.AuditService;
import com.waqiti.audit.service.ComprehensiveAuditTrailService;
import com.waqiti.audit.service.CryptographicIntegrityService;
import com.waqiti.audit.service.AuditNotificationService;
import com.waqiti.common.audit.AuditService as CommonAuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class AuditChainUpdatesConsumer {

    private final AuditEventRepository auditEventRepository;
    private final DataLineageRepository dataLineageRepository;
    private final AuditService auditService;
    private final ComprehensiveAuditTrailService auditTrailService;
    private final CryptographicIntegrityService integrityService;
    private final AuditNotificationService auditNotificationService;
    private final CommonAuditService commonAuditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;
    private Counter chainUpdateCounter;
    private Counter integrityValidationCounter;
    private Counter chainBreakCounter;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("audit_chain_updates_processed_total")
            .description("Total number of successfully processed audit chain update events")
            .register(meterRegistry);
        errorCounter = Counter.builder("audit_chain_updates_errors_total")
            .description("Total number of audit chain update processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("audit_chain_updates_processing_duration")
            .description("Time taken to process audit chain update events")
            .register(meterRegistry);
        chainUpdateCounter = Counter.builder("audit_chain_updates_total")
            .description("Total number of audit chain updates processed")
            .register(meterRegistry);
        integrityValidationCounter = Counter.builder("audit_chain_integrity_validations_total")
            .description("Total number of audit chain integrity validations performed")
            .register(meterRegistry);
        chainBreakCounter = Counter.builder("audit_chain_breaks_detected_total")
            .description("Total number of audit chain breaks detected")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"audit.chain.updates"},
        groupId = "audit-chain-updates-processor-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "audit-chain-updates", fallbackMethod = "handleAuditChainUpdatesEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAuditChainUpdatesEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("audit-chain-%d-p%d-o%d", System.currentTimeMillis(), partition, offset);
        String eventKey = String.format("chain-%d-%d-%d", partition, offset, System.currentTimeMillis());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing audit chain update event: partition={}, offset={}, correlationId={}",
                partition, offset, correlationId);

            // Clean expired entries periodically
            cleanExpiredEntries();

            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);

            String updateType = (String) eventData.get("updateType");
            String chainId = (String) eventData.get("chainId");
            String previousHash = (String) eventData.get("previousHash");
            String currentHash = (String) eventData.get("currentHash");
            String serviceName = (String) eventData.get("serviceName");
            String userId = (String) eventData.get("userId");
            String transactionId = (String) eventData.get("transactionId");
            String resourceId = (String) eventData.get("resourceId");
            Map<String, Object> chainData = (Map<String, Object>) eventData.get("chainData");
            Boolean requiresIntegrityCheck = (Boolean) eventData.getOrDefault("requiresIntegrityCheck", true);

            chainUpdateCounter.increment();

            // Process audit chain update
            processAuditChainUpdate(eventData, updateType, chainId, previousHash, currentHash,
                serviceName, userId, transactionId, resourceId, chainData,
                requiresIntegrityCheck, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            commonAuditService.logAuditEvent("AUDIT_CHAIN_UPDATE_PROCESSED", correlationId,
                Map.of("updateType", updateType, "chainId", chainId, "serviceName", serviceName,
                    "integrityValidated", requiresIntegrityCheck, "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process audit chain update event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("audit-chain-updates-fallback-events", Map.of(
                "originalMessage", message, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAuditChainUpdatesEventFallback(
            String message,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("audit-chain-fallback-%d-p%d-o%d",
            System.currentTimeMillis(), partition, offset);

        log.error("Circuit breaker fallback triggered for audit chain updates: partition={}, offset={}, error={}",
            partition, offset, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("audit-chain-updates-dlq", Map.of(
            "originalMessage", message,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Audit Chain Updates Circuit Breaker Triggered",
                String.format("Audit chain updates processing failed: %s", ex.getMessage()),
                "CRITICAL"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAuditChainUpdatesEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-audit-chain-%d", System.currentTimeMillis());

        log.error("Dead letter topic handler - Audit chain update permanently failed: topic={}, error={}",
            topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        commonAuditService.logAuditEvent("AUDIT_CHAIN_UPDATE_DLT_EVENT", correlationId,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Audit Chain Update Dead Letter Event",
                String.format("CRITICAL: Audit chain update sent to DLT - potential chain integrity issue: %s", exceptionMessage),
                Map.of("topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        // Check if the entry has expired
        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }

        return true;
    }

    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) { // Only clean when we have many entries
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void processAuditChainUpdate(Map<String, Object> eventData, String updateType,
            String chainId, String previousHash, String currentHash, String serviceName,
            String userId, String transactionId, String resourceId, Map<String, Object> chainData,
            Boolean requiresIntegrityCheck, String correlationId) {

        // Create audit event for the chain update
        AuditEvent auditEvent = AuditEvent.builder()
            .eventType("AUDIT_CHAIN_UPDATE")
            .serviceName(serviceName)
            .userId(userId)
            .transactionId(transactionId)
            .resourceId(resourceId)
            .resourceType("AUDIT_CHAIN")
            .action(updateType)
            .description(String.format("Audit chain update: %s for chain %s", updateType, chainId))
            .result(AuditEvent.AuditResult.SUCCESS)
            .severity(AuditEvent.AuditSeverity.HIGH) // Chain updates are critical
            .correlationId(correlationId)
            .metadata(convertToStringMap(eventData))
            .complianceTags("AUDIT_CHAIN,INTEGRITY,COMPLIANCE")
            .beforeState(previousHash)
            .afterState(currentHash)
            .build();

        auditEventRepository.save(auditEvent);

        // Process different types of chain updates
        switch (updateType.toUpperCase()) {
            case "CHAIN_LINK_ADDED":
                processChainLinkAdded(chainId, currentHash, previousHash, chainData, correlationId);
                break;
            case "CHAIN_VALIDATION":
                processChainValidation(chainId, currentHash, previousHash, correlationId);
                break;
            case "CHAIN_INTEGRITY_CHECK":
                processChainIntegrityCheck(chainId, correlationId);
                break;
            case "CHAIN_BREAK_DETECTED":
                processChainBreakDetected(chainId, previousHash, currentHash, correlationId);
                break;
            case "CHAIN_RESTORATION":
                processChainRestoration(chainId, currentHash, correlationId);
                break;
            default:
                log.warn("Unknown chain update type: {}, correlationId: {}", updateType, correlationId);
        }

        // Perform integrity validation if required
        if (requiresIntegrityCheck) {
            performChainIntegrityValidation(chainId, currentHash, previousHash, correlationId);
        }

        // Update data lineage if applicable
        if (transactionId != null && resourceId != null) {
            updateDataLineage(transactionId, resourceId, chainId, currentHash, serviceName, correlationId);
        }

        // Send downstream events
        kafkaTemplate.send("audit-chain-processed", Map.of(
            "eventId", auditEvent.getId(),
            "updateType", updateType,
            "chainId", chainId,
            "serviceName", serviceName,
            "userId", userId,
            "previousHash", previousHash != null ? previousHash : "",
            "currentHash", currentHash,
            "integrityValidated", requiresIntegrityCheck,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Processed audit chain update: type={}, chainId={}, service={}, correlationId={}",
            updateType, chainId, serviceName, correlationId);
    }

    private void processChainLinkAdded(String chainId, String currentHash, String previousHash,
            Map<String, Object> chainData, String correlationId) {

        log.info("Processing chain link addition: chainId={}, correlationId={}", chainId, correlationId);

        // Validate the chain link
        boolean isValid = integrityService.validateChainLink(chainId, currentHash, previousHash);

        if (!isValid) {
            log.error("CRITICAL: Invalid chain link detected for chainId={}, correlationId={}", chainId, correlationId);
            handleChainIntegrityFailure(chainId, "INVALID_CHAIN_LINK", correlationId);
            return;
        }

        // Store the chain link data
        auditTrailService.addChainLink(chainId, currentHash, previousHash, chainData, correlationId);

        // Send notification for successful chain link addition
        kafkaTemplate.send("audit-chain-link-added", Map.of(
            "chainId", chainId,
            "linkHash", currentHash,
            "previousHash", previousHash != null ? previousHash : "",
            "isValid", true,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void processChainValidation(String chainId, String currentHash, String previousHash, String correlationId) {
        log.info("Processing chain validation: chainId={}, correlationId={}", chainId, correlationId);

        boolean isValid = integrityService.validateChain(chainId, currentHash);
        integrityValidationCounter.increment();

        if (!isValid) {
            log.error("CRITICAL: Chain validation failed for chainId={}, correlationId={}", chainId, correlationId);
            handleChainIntegrityFailure(chainId, "CHAIN_VALIDATION_FAILED", correlationId);
        } else {
            log.info("Chain validation successful: chainId={}, correlationId={}", chainId, correlationId);
        }

        // Send validation result
        kafkaTemplate.send("audit-chain-validation-result", Map.of(
            "chainId", chainId,
            "isValid", isValid,
            "currentHash", currentHash,
            "validationTimestamp", Instant.now(),
            "correlationId", correlationId
        ));
    }

    private void processChainIntegrityCheck(String chainId, String correlationId) {
        log.info("Processing chain integrity check: chainId={}, correlationId={}", chainId, correlationId);

        try {
            boolean integrityOk = integrityService.performFullChainIntegrityCheck(chainId);
            integrityValidationCounter.increment();

            if (!integrityOk) {
                log.error("CRITICAL: Chain integrity check failed for chainId={}, correlationId={}", chainId, correlationId);
                handleChainIntegrityFailure(chainId, "INTEGRITY_CHECK_FAILED", correlationId);
            } else {
                log.info("Chain integrity check passed: chainId={}, correlationId={}", chainId, correlationId);
            }

            // Send integrity check result
            kafkaTemplate.send("audit-chain-integrity-result", Map.of(
                "chainId", chainId,
                "integrityStatus", integrityOk ? "VALID" : "INVALID",
                "checkTimestamp", Instant.now(),
                "correlationId", correlationId
            ));

        } catch (Exception e) {
            log.error("Error during chain integrity check for chainId={}, correlationId={}: {}",
                chainId, correlationId, e.getMessage());
            handleChainIntegrityFailure(chainId, "INTEGRITY_CHECK_ERROR", correlationId);
        }
    }

    private void processChainBreakDetected(String chainId, String previousHash, String currentHash, String correlationId) {
        log.error("CRITICAL: Chain break detected - chainId={}, correlationId={}", chainId, correlationId);

        chainBreakCounter.increment();

        // Send critical alert
        auditNotificationService.sendCriticalAuditAlert(
            "Critical Audit Chain Break Detected",
            String.format("CRITICAL: Audit chain break detected for chain %s", chainId),
            Map.of(
                "chainId", chainId,
                "previousHash", previousHash != null ? previousHash : "",
                "currentHash", currentHash,
                "correlationId", correlationId
            )
        );

        // Send to incident management
        kafkaTemplate.send("audit-chain-incidents", Map.of(
            "incidentType", "CHAIN_BREAK",
            "chainId", chainId,
            "severity", "CRITICAL",
            "description", "Audit chain break detected",
            "previousHash", previousHash != null ? previousHash : "",
            "currentHash", currentHash,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        handleChainIntegrityFailure(chainId, "CHAIN_BREAK_DETECTED", correlationId);
    }

    private void processChainRestoration(String chainId, String currentHash, String correlationId) {
        log.info("Processing chain restoration: chainId={}, correlationId={}", chainId, correlationId);

        // Validate the restored chain
        boolean isValid = integrityService.validateChain(chainId, currentHash);

        if (isValid) {
            log.info("Chain restoration successful: chainId={}, correlationId={}", chainId, correlationId);

            // Send restoration success notification
            auditNotificationService.sendAuditNotification(
                "Audit Chain Restored",
                String.format("Audit chain %s has been successfully restored", chainId),
                Map.of("chainId", chainId, "correlationId", correlationId)
            );
        } else {
            log.error("Chain restoration failed validation: chainId={}, correlationId={}", chainId, correlationId);
            handleChainIntegrityFailure(chainId, "RESTORATION_VALIDATION_FAILED", correlationId);
        }

        // Send restoration result
        kafkaTemplate.send("audit-chain-restoration-result", Map.of(
            "chainId", chainId,
            "restorationStatus", isValid ? "SUCCESS" : "FAILED",
            "currentHash", currentHash,
            "restorationTimestamp", Instant.now(),
            "correlationId", correlationId
        ));
    }

    private void performChainIntegrityValidation(String chainId, String currentHash, String previousHash, String correlationId) {
        try {
            boolean isValid = integrityService.validateChainIntegrity(chainId, currentHash, previousHash);
            integrityValidationCounter.increment();

            if (!isValid) {
                log.error("Chain integrity validation failed: chainId={}, correlationId={}", chainId, correlationId);
                handleChainIntegrityFailure(chainId, "INTEGRITY_VALIDATION_FAILED", correlationId);
            }

        } catch (Exception e) {
            log.error("Error during chain integrity validation: chainId={}, correlationId={}, error={}",
                chainId, correlationId, e.getMessage());
            handleChainIntegrityFailure(chainId, "INTEGRITY_VALIDATION_ERROR", correlationId);
        }
    }

    private void updateDataLineage(String transactionId, String resourceId, String chainId,
            String currentHash, String serviceName, String correlationId) {

        DataLineage lineage = DataLineage.builder()
            .transactionId(transactionId)
            .resourceId(resourceId)
            .resourceType("AUDIT_CHAIN")
            .serviceName(serviceName)
            .operation("CHAIN_UPDATE")
            .chainId(chainId)
            .chainHash(currentHash)
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        dataLineageRepository.save(lineage);

        log.debug("Updated data lineage: transactionId={}, resourceId={}, chainId={}, correlationId={}",
            transactionId, resourceId, chainId, correlationId);
    }

    private void handleChainIntegrityFailure(String chainId, String failureType, String correlationId) {
        log.error("CRITICAL: Chain integrity failure - chainId={}, type={}, correlationId={}",
            chainId, failureType, correlationId);

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Critical Audit Chain Integrity Failure",
                String.format("CRITICAL: Audit chain integrity failure - Chain: %s, Type: %s", chainId, failureType),
                Map.of("chainId", chainId, "failureType", failureType, "correlationId", correlationId)
            );
        } catch (Exception e) {
            log.error("Failed to send critical chain integrity alert: {}", e.getMessage());
        }

        // Send to security incident management
        kafkaTemplate.send("security-incidents", Map.of(
            "incidentType", "AUDIT_CHAIN_INTEGRITY_FAILURE",
            "severity", "CRITICAL",
            "chainId", chainId,
            "failureType", failureType,
            "description", "Critical audit chain integrity failure detected",
            "requiresImmediateAction", true,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private Map<String, String> convertToStringMap(Map<String, Object> objectMap) {
        Map<String, String> stringMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : objectMap.entrySet()) {
            stringMap.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : null);
        }
        return stringMap;
    }
}