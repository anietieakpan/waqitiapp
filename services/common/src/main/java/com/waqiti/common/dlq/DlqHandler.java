package com.waqiti.common.dlq;

import com.waqiti.common.audit.AuditService;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.common.notification.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic DLQ handler that provides common functionality for all DLQ consumers.
 * Handles error analysis, root cause identification, and escalation workflows.
 */
@Component
@Slf4j
public class DlqHandler {

    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final DlqErrorAnalyzer errorAnalyzer;
    private final DlqRetryManager retryManager;

    // Metrics
    private Counter dlqProcessedCounter;
    private Counter dlqErrorCounter;
    private Timer dlqProcessingTimer;
    private final ConcurrentHashMap<String, Counter> topicSpecificCounters = new ConcurrentHashMap<>();

    public DlqHandler(AuditService auditService,
                     NotificationService notificationService,
                     KafkaTemplate<String, Object> kafkaTemplate,
                     MeterRegistry meterRegistry,
                     DlqErrorAnalyzer errorAnalyzer,
                     DlqRetryManager retryManager) {
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        this.errorAnalyzer = errorAnalyzer;
        this.retryManager = retryManager;
    }

    @PostConstruct
    public void initMetrics() {
        dlqProcessedCounter = Counter.builder("dlq_messages_processed_total")
            .description("Total number of DLQ messages processed")
            .register(meterRegistry);
        dlqErrorCounter = Counter.builder("dlq_processing_errors_total")
            .description("Total number of DLQ processing errors")
            .register(meterRegistry);
        dlqProcessingTimer = Timer.builder("dlq_processing_duration")
            .description("Time taken to process DLQ messages")
            .register(meterRegistry);
    }

    /**
     * Processes a DLQ message with comprehensive error analysis and handling.
     */
    public void handleDlqMessage(@Payload Object originalMessage,
                                @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                @Header(KafkaHeaders.OFFSET) long offset,
                                Map<String, Object> headers) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String messageId = generateMessageId(topic, partition, offset);

        try {
            log.info("Processing DLQ message: topic={}, messageId={}, error={}",
                topic, messageId, exceptionMessage);

            // Create DLQ message with comprehensive metadata
            DlqMessage dlqMessage = createDlqMessage(originalMessage, topic, exceptionMessage,
                partition, offset, headers, messageId);

            // Analyze error and determine root cause
            errorAnalyzer.analyzeError(dlqMessage);

            // Determine business impact and escalation level
            assessBusinessImpact(dlqMessage);

            // Store DLQ message for analysis
            storeDlqMessage(dlqMessage);

            // Handle based on error category and business impact
            processBasedOnCategory(dlqMessage);

            // Record metrics
            recordMetrics(dlqMessage);

            // Send notifications based on escalation level
            sendNotifications(dlqMessage);

            // Audit the DLQ processing
            auditDlqProcessing(dlqMessage);

            dlqProcessedCounter.increment();
            getTopicCounter(topic).increment();

        } catch (Exception e) {
            dlqErrorCounter.increment();
            log.error("Failed to process DLQ message: topic={}, messageId={}, error={}",
                topic, messageId, e.getMessage(), e);

            // Send critical alert for DLQ processing failure
            sendCriticalAlert(topic, messageId, e.getMessage());
        } finally {
            sample.stop(dlqProcessingTimer);
        }
    }

    private DlqMessage createDlqMessage(Object originalMessage, String topic, String exceptionMessage,
                                       int partition, long offset, Map<String, Object> headers, String messageId) {
        return DlqMessage.builder()
            .messageId(messageId)
            .originalTopic(topic)
            .dlqTopic(topic + ".DLQ")
            .originalMessage(originalMessage)
            .errorMessage(exceptionMessage)
            .failureTimestamp(Instant.now())
            .partition(partition)
            .offset(offset)
            .headers(headers)
            .correlationId(extractCorrelationId(headers))
            .requiresManualIntervention(false) // Will be determined by analyzer
            .build();
    }

    private void assessBusinessImpact(DlqMessage dlqMessage) {
        String topic = dlqMessage.getOriginalTopic();

        // Determine business impact based on topic and error type
        if (isFinancialTopic(topic)) {
            dlqMessage.setBusinessImpact(DlqMessage.BusinessImpact.CRITICAL);
            dlqMessage.setEscalationLevel(DlqMessage.EscalationLevel.ENGINEERING);
        } else if (isComplianceTopic(topic)) {
            dlqMessage.setBusinessImpact(DlqMessage.BusinessImpact.HIGH);
            dlqMessage.setEscalationLevel(DlqMessage.EscalationLevel.OPERATIONAL);
        } else if (isSecurityTopic(topic)) {
            dlqMessage.setBusinessImpact(DlqMessage.BusinessImpact.CRITICAL);
            dlqMessage.setEscalationLevel(DlqMessage.EscalationLevel.CRITICAL);
        } else {
            dlqMessage.setBusinessImpact(DlqMessage.BusinessImpact.MEDIUM);
            dlqMessage.setEscalationLevel(DlqMessage.EscalationLevel.OPERATIONAL);
        }

        // Escalate based on error frequency
        if (errorAnalyzer.isHighFrequencyError(dlqMessage.getOriginalTopic(), dlqMessage.getErrorType())) {
            escalateLevel(dlqMessage);
        }
    }

    private void processBasedOnCategory(DlqMessage dlqMessage) {
        switch (dlqMessage.getErrorCategory()) {
            case SERIALIZATION_ERROR:
            case VALIDATION_ERROR:
                // These typically require manual intervention
                dlqMessage.setRequiresManualIntervention(true);
                scheduleManualReview(dlqMessage);
                break;

            case NETWORK_ERROR:
            case TIMEOUT_ERROR:
            case EXTERNAL_SERVICE_ERROR:
                // These can often be retried
                if (retryManager.shouldRetry(dlqMessage)) {
                    scheduleRetry(dlqMessage);
                } else {
                    dlqMessage.setRequiresManualIntervention(true);
                    scheduleManualReview(dlqMessage);
                }
                break;

            case DATABASE_ERROR:
                // Check if it's a transient issue
                if (errorAnalyzer.isTransientDatabaseError(dlqMessage.getErrorMessage())) {
                    scheduleRetry(dlqMessage);
                } else {
                    dlqMessage.setRequiresManualIntervention(true);
                    escalateLevel(dlqMessage);
                }
                break;

            default:
                // Unknown errors require investigation
                dlqMessage.setRequiresManualIntervention(true);
                escalateLevel(dlqMessage);
                break;
        }
    }

    private void scheduleRetry(DlqMessage dlqMessage) {
        retryManager.scheduleRetry(dlqMessage);
        log.info("Scheduled retry for DLQ message: messageId={}, retryCount={}",
            dlqMessage.getMessageId(), dlqMessage.getRetryCount());
    }

    private void scheduleManualReview(DlqMessage dlqMessage) {
        // Send to manual review queue
        kafkaTemplate.send("dlq-manual-review", dlqMessage);
        log.warn("DLQ message requires manual review: messageId={}, topic={}, error={}",
            dlqMessage.getMessageId(), dlqMessage.getOriginalTopic(), dlqMessage.getErrorMessage());
    }

    private void storeDlqMessage(DlqMessage dlqMessage) {
        // Store in audit log for analysis
        auditService.logDlqEvent("DLQ_MESSAGE_RECEIVED", dlqMessage.getOriginalTopic(),
            Map.of("messageId", dlqMessage.getMessageId(),
                   "errorCategory", dlqMessage.getErrorCategory(),
                   "businessImpact", dlqMessage.getBusinessImpact(),
                   "requiresManualIntervention", dlqMessage.getRequiresManualIntervention(),
                   "correlationId", dlqMessage.getCorrelationId(),
                   "timestamp", dlqMessage.getFailureTimestamp()));
    }

    private void sendNotifications(DlqMessage dlqMessage) {
        switch (dlqMessage.getEscalationLevel()) {
            case CRITICAL:
                com.waqiti.common.notification.model.CriticalAlertRequest criticalRequest =
                    com.waqiti.common.notification.model.CriticalAlertRequest.builder()
                        .title("Critical DLQ Message - Immediate Action Required")
                        .message(formatCriticalAlert(dlqMessage))
                        .severity(com.waqiti.common.notification.model.CriticalAlertRequest.AlertSeverity.CRITICAL)
                        .source("DLQ-Handler")
                        .metadata(Map.of("messageId", dlqMessage.getMessageId(),
                                       "topic", dlqMessage.getOriginalTopic(),
                                       "businessImpact", dlqMessage.getBusinessImpact().toString()))
                        .build();
                notificationService.sendCriticalAlert(criticalRequest);
                break;

            case ENGINEERING:
                notificationService.sendEngineeringAlert(
                    "DLQ Message Requires Engineering Review",
                    formatEngineeringAlert(dlqMessage)
                );
                break;

            case OPERATIONAL:
                notificationService.sendOperationalAlert(
                    "DLQ Message Detected",
                    formatOperationalAlert(dlqMessage),
                    "MEDIUM"
                );
                break;

            case MANAGEMENT:
                notificationService.sendManagementAlert(
                    "High-Impact DLQ Issue",
                    formatManagementAlert(dlqMessage)
                );
                break;
        }
    }

    private void recordMetrics(DlqMessage dlqMessage) {
        // Record metrics by error category
        Counter.builder("dlq_messages_by_category")
            .tag("category", dlqMessage.getErrorCategory().toString())
            .tag("topic", dlqMessage.getOriginalTopic())
            .tag("business_impact", dlqMessage.getBusinessImpact().toString())
            .register(meterRegistry)
            .increment();
    }

    private void auditDlqProcessing(DlqMessage dlqMessage) {
        auditService.logDlqEvent("DLQ_MESSAGE_PROCESSED", dlqMessage.getOriginalTopic(),
            Map.of("messageId", dlqMessage.getMessageId(),
                   "errorCategory", dlqMessage.getErrorCategory(),
                   "businessImpact", dlqMessage.getBusinessImpact(),
                   "escalationLevel", dlqMessage.getEscalationLevel(),
                   "requiresManualIntervention", dlqMessage.getRequiresManualIntervention(),
                   "rootCause", dlqMessage.getRootCause(),
                   "processingTimestamp", Instant.now()));
    }

    private String generateMessageId(String topic, int partition, long offset) {
        return String.format("dlq-%s-p%d-o%d-%d", topic, partition, offset, System.currentTimeMillis());
    }

    private String extractCorrelationId(Map<String, Object> headers) {
        if (headers != null && headers.containsKey("correlationId")) {
            return headers.get("correlationId").toString();
        }
        return null;
    }

    private boolean isFinancialTopic(String topic) {
        return topic.contains("payment") || topic.contains("balance") || topic.contains("settlement") ||
               topic.contains("loan") || topic.contains("credit") || topic.contains("wallet");
    }

    private boolean isComplianceTopic(String topic) {
        return topic.contains("compliance") || topic.contains("aml") || topic.contains("sar") ||
               topic.contains("kyc") || topic.contains("audit");
    }

    private boolean isSecurityTopic(String topic) {
        return topic.contains("fraud") || topic.contains("auth") || topic.contains("security") ||
               topic.contains("breach") || topic.contains("anomaly");
    }

    private void escalateLevel(DlqMessage dlqMessage) {
        switch (dlqMessage.getEscalationLevel()) {
            case NONE:
                dlqMessage.setEscalationLevel(DlqMessage.EscalationLevel.OPERATIONAL);
                break;
            case OPERATIONAL:
                dlqMessage.setEscalationLevel(DlqMessage.EscalationLevel.ENGINEERING);
                break;
            case ENGINEERING:
                dlqMessage.setEscalationLevel(DlqMessage.EscalationLevel.MANAGEMENT);
                break;
            case MANAGEMENT:
                dlqMessage.setEscalationLevel(DlqMessage.EscalationLevel.CRITICAL);
                break;
        }
    }

    private Counter getTopicCounter(String topic) {
        return topicSpecificCounters.computeIfAbsent(topic, t ->
            Counter.builder("dlq_messages_by_topic")
                .tag("topic", t)
                .register(meterRegistry));
    }

    private String formatCriticalAlert(DlqMessage dlqMessage) {
        return String.format("CRITICAL DLQ Alert:\n" +
            "Topic: %s\n" +
            "Message ID: %s\n" +
            "Error: %s\n" +
            "Business Impact: %s\n" +
            "Requires immediate investigation and resolution.",
            dlqMessage.getOriginalTopic(), dlqMessage.getMessageId(),
            dlqMessage.getErrorMessage(), dlqMessage.getBusinessImpact());
    }

    private String formatEngineeringAlert(DlqMessage dlqMessage) {
        return String.format("Engineering DLQ Alert:\n" +
            "Topic: %s\n" +
            "Message ID: %s\n" +
            "Error Category: %s\n" +
            "Root Cause: %s\n" +
            "Manual Intervention Required: %s",
            dlqMessage.getOriginalTopic(), dlqMessage.getMessageId(),
            dlqMessage.getErrorCategory(), dlqMessage.getRootCause(),
            dlqMessage.getRequiresManualIntervention());
    }

    private String formatOperationalAlert(DlqMessage dlqMessage) {
        return String.format("DLQ Message Detected:\n" +
            "Topic: %s\n" +
            "Error Category: %s\n" +
            "Business Impact: %s",
            dlqMessage.getOriginalTopic(), dlqMessage.getErrorCategory(),
            dlqMessage.getBusinessImpact());
    }

    private String formatManagementAlert(DlqMessage dlqMessage) {
        return String.format("High-Impact DLQ Issue:\n" +
            "Topic: %s\n" +
            "Business Impact: %s\n" +
            "Multiple failures detected requiring management attention.",
            dlqMessage.getOriginalTopic(), dlqMessage.getBusinessImpact());
    }

    private void sendCriticalAlert(String topic, String messageId, String error) {
        try {
            com.waqiti.common.notification.model.CriticalAlertRequest request =
                com.waqiti.common.notification.model.CriticalAlertRequest.builder()
                    .title("DLQ Processing Failure")
                    .message(String.format("Failed to process DLQ message: topic=%s, messageId=%s, error=%s",
                        topic, messageId, error))
                    .severity(com.waqiti.common.notification.model.CriticalAlertRequest.AlertSeverity.HIGH)
                    .source("DLQ-Handler")
                    .metadata(Map.of("topic", topic, "messageId", messageId))
                    .build();
            notificationService.sendCriticalAlert(request);
        } catch (Exception e) {
            log.error("Failed to send critical alert for DLQ processing failure: {}", e.getMessage());
        }
    }
}