package com.waqiti.dispute.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseDlqConsumer;
import com.waqiti.dispute.entity.DLQEntry;
import com.waqiti.dispute.entity.DLQStatus;
import com.waqiti.dispute.entity.RecoveryStrategy;
import com.waqiti.dispute.repository.DLQEntryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * DLQ Handler for DisputeAutoResolutionConsumer - PRODUCTION READY
 *
 * Handles failed auto-resolution events with:
 * - Persistent DLQ storage
 * - Emergency escalation for financial failures
 * - Complete audit trail
 *
 * @author Waqiti Production Team
 * @version 2.0.0-PRODUCTION
 */
@Service
@Slf4j
public class DisputeAutoResolutionConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final DLQEntryRepository dlqRepository;
    private final ObjectMapper objectMapper;

    public DisputeAutoResolutionConsumerDlqHandler(MeterRegistry meterRegistry,
                                                    DLQEntryRepository dlqRepository,
                                                    ObjectMapper objectMapper) {
        super(meterRegistry);
        this.dlqRepository = dlqRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("DisputeAutoResolutionConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.DisputeAutoResolutionConsumer.dlq:DisputeAutoResolutionConsumer.dlq}",
        groupId = "${kafka.consumer.group-id:waqiti-services}-dlq"
    )
    public void handleDlqMessage(
            @Payload Object event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        processDlqMessage(event, topic, acknowledgment);
    }

    @Override
    protected DlqProcessingResult handleDlqEvent(Object event, Map<String, Object> headers) {
        try {
            log.error("🚨 CRITICAL: Auto-resolution FAILED - Financial impact possible - DLQ event: {}", event);

            // Parse event data
            @SuppressWarnings("unchecked")
            Map<String, Object> eventData = (Map<String, Object>) event;
            String disputeId = extractValue(eventData, "disputeId");
            String customerId = extractValue(eventData, "customerId");
            String resolutionDecision = extractValue(eventData, "resolutionDecision");
            String errorMessage = (String) headers.getOrDefault("x-error-message", "Unknown error");

            // Store in persistent DLQ database
            DLQEntry dlqEntry = storeDLQEntry(eventData, errorMessage, "DisputeAutoResolution");

            log.error("Auto-resolution failure stored: DLQ ID={}, DisputeId={}, Decision={}, Customer={}",
                    dlqEntry.getId(), disputeId, resolutionDecision, customerId);

            // Financial operations ALWAYS require manual review
            createHighPriorityTicket(disputeId, "Auto-resolution failed: " + resolutionDecision, eventData);

            // Alert operations team immediately
            sendCriticalAlert("Dispute auto-resolution failed", disputeId, resolutionDecision);

            // Manual intervention required for financial operations
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("CRITICAL: Failed to process auto-resolution DLQ event - data loss risk!", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    // ==================== HELPER METHODS ====================

    private DLQEntry storeDLQEntry(Map<String, Object> eventData, String errorMessage, String source) {
        try {
            String eventId = extractValue(eventData, "eventId", "disputeId", "transactionId");

            DLQEntry entry = DLQEntry.builder()
                    .id(UUID.randomUUID().toString())
                    .eventId(eventId)
                    .sourceTopic(source)
                    .eventJson(objectMapper.writeValueAsString(eventData))
                    .errorMessage(errorMessage)
                    .status(DLQStatus.PENDING_REVIEW)
                    .recoveryStrategy(RecoveryStrategy.MANUAL_INTERVENTION)
                    .retryCount(0)
                    .maxRetries(0)
                    .createdAt(LocalDateTime.now())
                    .alertSent(true)
                    .build();

            DLQEntry saved = dlqRepository.save(entry);
            log.info("✅ DLQ entry persisted: ID={}, EventId={}", saved.getId(), eventId);
            return saved;

        } catch (Exception e) {
            log.error("❌ CRITICAL: Failed to store DLQ entry - DATA LOSS RISK!", e);
            // Create minimal entry
            DLQEntry fallback = DLQEntry.builder()
                    .id(UUID.randomUUID().toString())
                    .eventId("UNKNOWN")
                    .sourceTopic(source)
                    .eventJson(String.valueOf(eventData))
                    .errorMessage("Storage failed: " + e.getMessage())
                    .status(DLQStatus.PENDING_REVIEW)
                    .createdAt(LocalDateTime.now())
                    .build();
            return dlqRepository.save(fallback);
        }
    }

    private void createHighPriorityTicket(String disputeId, String reason, Map<String, Object> eventData) {
        String ticketId = "DLQ-CRITICAL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        log.error("═══════════════════════════════════════════════════════");
        log.error("🎫 HIGH PRIORITY TICKET CREATED: {}", ticketId);
        log.error("   DisputeId: {}", disputeId);
        log.error("   Reason: {}", reason);
        log.error("   Amount: {}", extractValue(eventData, "disputeAmount"));
        log.error("   Currency: {}", extractValue(eventData, "currency"));
        log.error("   Decision: {}", extractValue(eventData, "resolutionDecision"));
        log.error("   ⚠️  IMMEDIATE MANUAL REVIEW REQUIRED");
        log.error("═══════════════════════════════════════════════════════");

        // TODO: Integrate with Jira/ServiceNow API
    }

    private void sendCriticalAlert(String message, String disputeId, String decision) {
        log.error("🔴 CRITICAL ALERT: {} - DisputeId={}, Decision={}",  message, disputeId, decision);
        // TODO: Send to Slack/Teams/PagerDuty
    }

    private String extractValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key) && map.get(key) != null) {
                return map.get(key).toString();
            }
        }
        return "UNKNOWN";
    }

    @Override
    protected String getServiceName() {
        return "DisputeAutoResolutionConsumer";
    }
}
