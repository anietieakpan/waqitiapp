package com.waqiti.dispute.kafka;

import com.waqiti.payment.dto.PaymentChargebackEvent;
import com.waqiti.dispute.entity.Dispute;
import com.waqiti.dispute.service.TransactionDisputeService;
import com.waqiti.common.audit.AuditService;
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
import jakarta.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChargebackInvestigationsConsumer {

    private final TransactionDisputeService disputeService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("chargeback_investigations_processed_total")
            .description("Total number of successfully processed chargeback investigation events")
            .register(meterRegistry);
        errorCounter = Counter.builder("chargeback_investigations_errors_total")
            .description("Total number of chargeback investigation processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("chargeback_investigations_processing_duration")
            .description("Time taken to process chargeback investigation events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"chargeback-investigations"},
        groupId = "dispute-chargeback-investigations-group",
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
    @CircuitBreaker(name = "chargeback-investigations", fallbackMethod = "handleChargebackInvestigationEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleChargebackInvestigationEvent(
            @Payload Map<String, Object> investigationEvent,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String chargebackId = (String) investigationEvent.get("chargebackId");
        String correlationId = String.format("investigation-%s-p%d-o%d", chargebackId, partition, offset);
        String eventKey = String.format("%s-%s-%s", chargebackId,
            investigationEvent.get("investigationType"), investigationEvent.get("timestamp"));

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing chargeback investigation: chargebackId={}, investigationType={}, priority={}",
                chargebackId, investigationEvent.get("investigationType"), investigationEvent.get("priority"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Process investigation
            processChargebackInvestigation(investigationEvent, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logDisputeEvent("CHARGEBACK_INVESTIGATION_PROCESSED", chargebackId,
                Map.of("chargebackId", chargebackId, "transactionId", investigationEvent.get("transactionId"),
                    "investigationType", investigationEvent.get("investigationType"),
                    "priority", investigationEvent.get("priority"),
                    "deadline", investigationEvent.get("deadline"),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process chargeback investigation: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("chargeback-investigation-fallback-events", Map.of(
                "originalEvent", investigationEvent, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleChargebackInvestigationEventFallback(
            Map<String, Object> investigationEvent,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String chargebackId = (String) investigationEvent.get("chargebackId");
        String correlationId = String.format("investigation-fallback-%s-p%d-o%d", chargebackId, partition, offset);

        log.error("Circuit breaker fallback triggered for chargeback investigation: chargebackId={}, error={}",
            chargebackId, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("chargeback-investigations-dlq", Map.of(
            "originalEvent", investigationEvent,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Chargeback Investigation Circuit Breaker Triggered",
                String.format("Chargeback %s investigation processing failed: %s", chargebackId, ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltChargebackInvestigationEvent(
            @Payload Map<String, Object> investigationEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String chargebackId = (String) investigationEvent.get("chargebackId");
        String correlationId = String.format("dlt-investigation-%s-%d", chargebackId, System.currentTimeMillis());

        log.error("Dead letter topic handler - Chargeback investigation permanently failed: chargebackId={}, topic={}, error={}",
            chargebackId, topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logDisputeEvent("CHARGEBACK_INVESTIGATION_DLT_EVENT", chargebackId,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "chargebackId", chargebackId, "investigationEvent", investigationEvent,
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Chargeback Investigation Dead Letter Event",
                String.format("Chargeback %s investigation sent to DLT: %s", chargebackId, exceptionMessage),
                Map.of("chargebackId", chargebackId, "topic", topic, "correlationId", correlationId)
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

    private void processChargebackInvestigation(Map<String, Object> investigationEvent, String correlationId) {
        String chargebackId = (String) investigationEvent.get("chargebackId");
        String transactionId = (String) investigationEvent.get("transactionId");
        String investigationType = (String) investigationEvent.getOrDefault("investigationType", "STANDARD");
        String priority = (String) investigationEvent.getOrDefault("priority", "MEDIUM");
        LocalDateTime deadline = (LocalDateTime) investigationEvent.get("deadline");

        log.info("Processing chargeback investigation: chargebackId={}, type={}, priority={}, deadline={}",
            chargebackId, investigationType, priority, deadline);

        // Update dispute status to under investigation
        disputeService.setChargebackStatus(chargebackId, "UNDER_INVESTIGATION", correlationId);

        // Process based on investigation type
        switch (investigationType) {
            case "AUTOMATIC":
                processAutomaticInvestigation(investigationEvent, correlationId);
                break;

            case "MANUAL":
                processManualInvestigation(investigationEvent, correlationId);
                break;

            case "ENHANCED":
                processEnhancedInvestigation(investigationEvent, correlationId);
                break;

            case "EXPEDITED":
                processExpeditedInvestigation(investigationEvent, correlationId);
                break;

            default:
                processStandardInvestigation(investigationEvent, correlationId);
                break;
        }

        // Set up deadline monitoring
        if (deadline != null) {
            scheduleDeadlineMonitoring(chargebackId, deadline, correlationId);
        }

        // Update metrics
        meterRegistry.counter("chargeback_investigations_total",
            "type", investigationType,
            "priority", priority).increment();

        log.info("Chargeback investigation processed: chargebackId={}, type={}",
            chargebackId, investigationType);
    }

    private void processAutomaticInvestigation(Map<String, Object> investigationEvent, String correlationId) {
        String chargebackId = (String) investigationEvent.get("chargebackId");
        String transactionId = (String) investigationEvent.get("transactionId");

        log.info("Processing automatic chargeback investigation: chargebackId={}", chargebackId);

        // Gather transaction evidence automatically
        gatherTransactionEvidence(chargebackId, transactionId, correlationId);

        // Run automated analysis
        runAutomatedAnalysis(chargebackId, correlationId);

        // Check if auto-representment is possible
        if (Boolean.TRUE.equals(investigationEvent.get("autoContestable"))) {
            processAutoRepresentment(chargebackId, correlationId);
        } else {
            // Send to manual review
            sendToManualReview(chargebackId, "AUTO_INVESTIGATION_COMPLETE", correlationId);
        }
    }

    private void processManualInvestigation(Map<String, Object> investigationEvent, String correlationId) {
        String chargebackId = (String) investigationEvent.get("chargebackId");

        log.info("Processing manual chargeback investigation: chargebackId={}", chargebackId);

        // Assign to dispute analyst
        String assignedAnalyst = assignToAnalyst(chargebackId, (String) investigationEvent.get("priority"));

        // Create investigation task
        kafkaTemplate.send("investigation-tasks", Map.of(
            "chargebackId", chargebackId,
            "taskType", "MANUAL_INVESTIGATION",
            "assignedTo", assignedAnalyst,
            "priority", investigationEvent.get("priority"),
            "deadline", investigationEvent.get("deadline"),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Notify assigned analyst
        notificationService.sendNotification(assignedAnalyst, "Chargeback Investigation Assigned",
            String.format("You have been assigned to investigate chargeback %s", chargebackId),
            correlationId);

        // Update dispute with assignment
        disputeService.assignChargebackInvestigation(chargebackId, assignedAnalyst, correlationId);
    }

    private void processEnhancedInvestigation(Map<String, Object> investigationEvent, String correlationId) {
        String chargebackId = (String) investigationEvent.get("chargebackId");

        log.info("Processing enhanced chargeback investigation: chargebackId={}", chargebackId);

        // Perform comprehensive evidence gathering
        gatherComprehensiveEvidence(chargebackId, correlationId);

        // Run fraud analysis
        runFraudAnalysis(chargebackId, correlationId);

        // Check merchant history
        analyzeMerchantHistory(chargebackId, correlationEvent.get("merchantId"), correlationId);

        // Assign to senior analyst
        String seniorAnalyst = assignToSeniorAnalyst(chargebackId);
        disputeService.assignChargebackInvestigation(chargebackId, seniorAnalyst, correlationId);

        // Create enhanced investigation task
        kafkaTemplate.send("investigation-tasks", Map.of(
            "chargebackId", chargebackId,
            "taskType", "ENHANCED_INVESTIGATION",
            "assignedTo", seniorAnalyst,
            "priority", "HIGH",
            "requiresManagerReview", true,
            "deadline", investigationEvent.get("deadline"),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void processExpeditedInvestigation(Map<String, Object> investigationEvent, String correlationId) {
        String chargebackId = (String) investigationEvent.get("chargebackId");

        log.info("Processing expedited chargeback investigation: chargebackId={}", chargebackId);

        // Fast-track evidence gathering
        gatherUrgentEvidence(chargebackId, correlationId);

        // Assign to available senior analyst immediately
        String availableAnalyst = getAvailableSeniorAnalyst();
        disputeService.assignChargebackInvestigation(chargebackId, availableAnalyst, correlationId);

        // Create urgent task
        kafkaTemplate.send("investigation-tasks", Map.of(
            "chargebackId", chargebackId,
            "taskType", "EXPEDITED_INVESTIGATION",
            "assignedTo", availableAnalyst,
            "priority", "CRITICAL",
            "expedited", true,
            "deadline", investigationEvent.get("deadline"),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Send urgent notification
        notificationService.sendCriticalAlert(
            "Expedited Chargeback Investigation",
            String.format("Urgent chargeback investigation required for %s", chargebackId),
            Map.of("chargebackId", chargebackId, "assignedTo", availableAnalyst)
        );
    }

    private void processStandardInvestigation(Map<String, Object> investigationEvent, String correlationId) {
        String chargebackId = (String) investigationEvent.get("chargebackId");

        log.info("Processing standard chargeback investigation: chargebackId={}", chargebackId);

        // Standard evidence gathering
        gatherStandardEvidence(chargebackId, correlationId);

        // Assign to available analyst
        String analyst = assignToAvailableAnalyst(chargebackId);
        disputeService.assignChargebackInvestigation(chargebackId, analyst, correlationId);

        // Create standard investigation task
        kafkaTemplate.send("investigation-tasks", Map.of(
            "chargebackId", chargebackId,
            "taskType", "STANDARD_INVESTIGATION",
            "assignedTo", analyst,
            "priority", investigationEvent.get("priority"),
            "deadline", investigationEvent.get("deadline"),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void gatherTransactionEvidence(String chargebackId, String transactionId, String correlationId) {
        // Request transaction details
        kafkaTemplate.send("evidence-gathering", Map.of(
            "chargebackId", chargebackId,
            "transactionId", transactionId,
            "evidenceType", "TRANSACTION_DETAILS",
            "priority", "MEDIUM",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void runAutomatedAnalysis(String chargebackId, String correlationId) {
        // Send to ML analysis
        kafkaTemplate.send("ml-chargeback-analysis", Map.of(
            "chargebackId", chargebackId,
            "analysisType", "WIN_PROBABILITY",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void processAutoRepresentment(String chargebackId, String correlationId) {
        // Submit automatic representment
        kafkaTemplate.send("chargeback-representments", Map.of(
            "chargebackId", chargebackId,
            "representmentType", "AUTOMATIC",
            "submissionMode", "AUTO",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Auto-representment initiated for chargeback: {}", chargebackId);
    }

    private void gatherComprehensiveEvidence(String chargebackId, String correlationId) {
        String[] evidenceTypes = {
            "TRANSACTION_DETAILS", "MERCHANT_RECORDS", "CUSTOMER_COMMUNICATION",
            "FRAUD_CHECKS", "3DS_AUTHENTICATION", "DELIVERY_CONFIRMATION"
        };

        for (String evidenceType : evidenceTypes) {
            kafkaTemplate.send("evidence-gathering", Map.of(
                "chargebackId", chargebackId,
                "evidenceType", evidenceType,
                "priority", "HIGH",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
    }

    private void runFraudAnalysis(String chargebackId, String correlationId) {
        kafkaTemplate.send("fraud-analysis", Map.of(
            "chargebackId", chargebackId,
            "analysisType", "CHARGEBACK_FRAUD",
            "depth", "COMPREHENSIVE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void scheduleDeadlineMonitoring(String chargebackId, LocalDateTime deadline, String correlationId) {
        kafkaTemplate.send("deadline-monitoring", Map.of(
            "chargebackId", chargebackId,
            "deadline", deadline,
            "reminderSchedule", Arrays.asList("7_DAYS", "3_DAYS", "1_DAY", "4_HOURS"),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void sendToManualReview(String chargebackId, String reason, String correlationId) {
        kafkaTemplate.send("chargeback-manual-queue", Map.of(
            "chargebackId", chargebackId,
            "priority", "MEDIUM",
            "reason", reason,
            "requiresManagerApproval", false,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private String assignToAnalyst(String chargebackId, String priority) {
        // Simple round-robin assignment logic (in real implementation, this would be more sophisticated)
        return "HIGH".equals(priority) || "CRITICAL".equals(priority) ?
            "senior-analyst-" + (chargebackId.hashCode() % 3 + 1) :
            "analyst-" + (chargebackId.hashCode() % 5 + 1);
    }

    private String assignToSeniorAnalyst(String chargebackId) {
        return "senior-analyst-" + (chargebackId.hashCode() % 3 + 1);
    }

    private String getAvailableSeniorAnalyst() {
        return "senior-analyst-1"; // In real implementation, check availability
    }

    private String assignToAvailableAnalyst(String chargebackId) {
        return "analyst-" + (chargebackId.hashCode() % 5 + 1);
    }

    private void gatherStandardEvidence(String chargebackId, String correlationId) {
        String[] standardEvidence = {"TRANSACTION_DETAILS", "MERCHANT_RECORDS"};

        for (String evidenceType : standardEvidence) {
            kafkaTemplate.send("evidence-gathering", Map.of(
                "chargebackId", chargebackId,
                "evidenceType", evidenceType,
                "priority", "MEDIUM",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
    }

    private void gatherUrgentEvidence(String chargebackId, String correlationId) {
        String[] urgentEvidence = {"TRANSACTION_DETAILS", "FRAUD_CHECKS", "3DS_AUTHENTICATION"};

        for (String evidenceType : urgentEvidence) {
            kafkaTemplate.send("evidence-gathering", Map.of(
                "chargebackId", chargebackId,
                "evidenceType", evidenceType,
                "priority", "CRITICAL",
                "expedited", true,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
    }

    private void analyzeMerchantHistory(String chargebackId, Object merchantId, String correlationId) {
        kafkaTemplate.send("merchant-analysis", Map.of(
            "chargebackId", chargebackId,
            "merchantId", merchantId,
            "analysisType", "CHARGEBACK_HISTORY",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }
}