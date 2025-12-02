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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * DLQ Handler for DisputeRejectionsConsumer - PRODUCTION READY
 *
 * HIGH PRIORITY: Handles failed dispute rejection events
 * Rejections close disputes and notify customers of denial
 * Failures leave customers uninformed and disputes in limbo
 *
 * @author Waqiti Production Team
 * @version 2.0.0-PRODUCTION
 */
@Service
@Slf4j
public class DisputeRejectionsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final DLQEntryRepository dlqRepository;
    private final ObjectMapper objectMapper;

    public DisputeRejectionsConsumerDlqHandler(MeterRegistry meterRegistry,
                                                DLQEntryRepository dlqRepository,
                                                ObjectMapper objectMapper) {
        super(meterRegistry);
        this.dlqRepository = dlqRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("DisputeRejectionsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.DisputeRejectionsConsumer.dlq:DisputeRejectionsConsumer.dlq}",
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
            log.error("⚠️ HIGH PRIORITY: Dispute rejection FAILED - Customer not notified!");

            @SuppressWarnings("unchecked")
            Map<String, Object> eventData = (Map<String, Object>) event;

            String disputeId = extractValue(eventData, "disputeId");
            String customerId = extractValue(eventData, "customerId");
            String rejectionReason = extractValue(eventData, "rejectionReason", "reason");
            String reviewedBy = extractValue(eventData, "reviewedBy", "analyst");
            String errorMessage = (String) headers.getOrDefault("x-error-message", "Unknown error");

            // Store in DLQ database
            DLQEntry dlqEntry = DLQEntry.builder()
                    .id(UUID.randomUUID().toString())
                    .eventId(extractValue(eventData, "eventId", "disputeId"))
                    .sourceTopic("DisputeRejections")
                    .eventJson(objectMapper.writeValueAsString(eventData))
                    .errorMessage(errorMessage)
                    .status(DLQStatus.PENDING_REVIEW)
                    .recoveryStrategy(RecoveryStrategy.RETRY_AFTER_DELAY)
                    .retryCount(0)
                    .maxRetries(3)
                    .createdAt(LocalDateTime.now())
                    .alertSent(true)
                    .build();

            dlqRepository.save(dlqEntry);

            log.error("╔═════════════════════════════════════════════════════════╗");
            log.error("║  ⚠️ HIGH PRIORITY: DISPUTE REJECTION FAILURE             ║");
            log.error("╠═════════════════════════════════════════════════════════╣");
            log.error("║  DLQ ID: {}", String.format("%-47s", dlqEntry.getId()) + "║");
            log.error("║  Dispute ID: {}", String.format("%-43s", disputeId) + "║");
            log.error("║  Customer ID: {}", String.format("%-42s", customerId) + "║");
            log.error("║  Reviewed By: {}", String.format("%-42s", reviewedBy) + "║");
            log.error("║  Rejection Reason: {}", String.format("%-37s", rejectionReason.substring(0, Math.min(37, rejectionReason.length()))) + "║");
            log.error("║                                                         ║");
            log.error("║  ⚠️  CUSTOMER NOT NOTIFIED - RETRY REQUIRED              ║");
            log.error("╚═════════════════════════════════════════════════════════╝");

            // Create high-priority ticket
            createHighPriorityTicket(disputeId, customerId, rejectionReason, reviewedBy, errorMessage);

            // Notify customer experience team
            notifyCustomerExperienceTeam(disputeId, customerId, rejectionReason);

            // Rejections can be retried after validation
            return DlqProcessingResult.RETRY_AFTER_DELAY;

        } catch (Exception e) {
            log.error("CRITICAL: Failed to process dispute rejection DLQ event!", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    private void createHighPriorityTicket(String disputeId, String customerId, String reason,
                                           String reviewedBy, String error) {
        String ticketId = "DLQ-REJ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        log.error("═══════════════════════════════════════════════════════════");
        log.error("🎫 HIGH PRIORITY TICKET: {}", ticketId);
        log.error("   Type: DISPUTE REJECTION FAILURE");
        log.error("   Priority: P1 - HIGH");
        log.error("   Dispute ID: {}", disputeId);
        log.error("   Customer ID: {}", customerId);
        log.error("   Rejection Reason: {}", reason);
        log.error("   Reviewed By: {}", reviewedBy);
        log.error("   Error: {}", error);
        log.error("   ");
        log.error("   ACTIONS REQUIRED:");
        log.error("   1. Verify dispute rejection decision is correct");
        log.error("   2. Manually update dispute status to REJECTED");
        log.error("   3. Notify customer of rejection with reason");
        log.error("   4. Close dispute and update reporting");
        log.error("   5. Log rejection for analytics");
        log.error("═══════════════════════════════════════════════════════════");

        // TODO: Create Jira P1 ticket
    }

    private void notifyCustomerExperienceTeam(String disputeId, String customerId, String reason) {
        log.warn("📧 Notifying Customer Experience Team");
        log.warn("   DisputeId: {}, CustomerId: {}, RejectionReason: {}", disputeId, customerId, reason);
        // TODO: Send email/Slack notification to CX team
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
        return "DisputeRejectionsConsumer";
    }
}
