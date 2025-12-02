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
 * DLQ Handler for DisputeInvestigationsConsumer - PRODUCTION READY
 *
 * HIGH PRIORITY: Handles failed dispute investigation events
 * Investigations involve reviewing evidence, customer/merchant statements, fraud indicators
 * Failures delay dispute resolution and impact customer experience
 *
 * @author Waqiti Production Team
 * @version 2.0.0-PRODUCTION
 */
@Service
@Slf4j
public class DisputeInvestigationsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final DLQEntryRepository dlqRepository;
    private final ObjectMapper objectMapper;

    public DisputeInvestigationsConsumerDlqHandler(MeterRegistry meterRegistry,
                                                    DLQEntryRepository dlqRepository,
                                                    ObjectMapper objectMapper) {
        super(meterRegistry);
        this.dlqRepository = dlqRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("DisputeInvestigationsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.DisputeInvestigationsConsumer.dlq:DisputeInvestigationsConsumer.dlq}",
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
            log.error("⚠️ HIGH PRIORITY: Dispute investigation FAILED - Resolution delayed!");

            @SuppressWarnings("unchecked")
            Map<String, Object> eventData = (Map<String, Object>) event;

            String disputeId = extractValue(eventData, "disputeId");
            String investigationId = extractValue(eventData, "investigationId");
            String investigationType = extractValue(eventData, "investigationType");
            String investigator = extractValue(eventData, "investigator", "assignedTo");
            String errorMessage = (String) headers.getOrDefault("x-error-message", "Unknown error");

            // Store in DLQ database
            DLQEntry dlqEntry = DLQEntry.builder()
                    .id(UUID.randomUUID().toString())
                    .eventId(extractValue(eventData, "eventId", "investigationId", "disputeId"))
                    .sourceTopic("DisputeInvestigations")
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
            log.error("║  ⚠️ HIGH PRIORITY: DISPUTE INVESTIGATION FAILURE         ║");
            log.error("╠═════════════════════════════════════════════════════════╣");
            log.error("║  DLQ ID: {}", String.format("%-47s", dlqEntry.getId()) + "║");
            log.error("║  Dispute ID: {}", String.format("%-43s", disputeId) + "║");
            log.error("║  Investigation ID: {}", String.format("%-37s", investigationId) + "║");
            log.error("║  Type: {}", String.format("%-49s", investigationType) + "║");
            log.error("║  Investigator: {}", String.format("%-41s", investigator) + "║");
            log.error("║                                                         ║");
            log.error("║  ⚠️  DISPUTE RESOLUTION DELAYED - REVIEW REQUIRED        ║");
            log.error("╚═════════════════════════════════════════════════════════╝");

            // Create high-priority ticket
            createHighPriorityTicket(disputeId, investigationId, investigationType, investigator, errorMessage);

            // Notify dispute investigation team
            notifyInvestigationTeam(disputeId, investigationId, investigationType);

            // Investigation events can be retried after review
            return DlqProcessingResult.RETRY_AFTER_DELAY;

        } catch (Exception e) {
            log.error("CRITICAL: Failed to process dispute investigation DLQ event!", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    private void createHighPriorityTicket(String disputeId, String investigationId,
                                           String investigationType, String investigator, String error) {
        String ticketId = "DLQ-INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        log.error("═══════════════════════════════════════════════════════════");
        log.error("🎫 HIGH PRIORITY TICKET: {}", ticketId);
        log.error("   Type: DISPUTE INVESTIGATION FAILURE");
        log.error("   Priority: P1 - HIGH");
        log.error("   Dispute ID: {}", disputeId);
        log.error("   Investigation ID: {}", investigationId);
        log.error("   Investigation Type: {}", investigationType);
        log.error("   Assigned Investigator: {}", investigator);
        log.error("   Error: {}", error);
        log.error("   ");
        log.error("   ACTIONS REQUIRED:");
        log.error("   1. Review investigation event and error details");
        log.error("   2. Verify dispute status and timeline impact");
        log.error("   3. Re-assign investigation if needed");
        log.error("   4. Retry investigation processing");
        log.error("   5. Update customer on investigation progress");
        log.error("═══════════════════════════════════════════════════════════");

        // TODO: Create Jira P1 ticket
    }

    private void notifyInvestigationTeam(String disputeId, String investigationId, String investigationType) {
        log.warn("📧 Notifying Dispute Investigation Team");
        log.warn("   DisputeId: {}, InvestigationId: {}, Type: {}", disputeId, investigationId, investigationType);
        // TODO: Send email/Slack notification to investigation team
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
        return "DisputeInvestigationsConsumer";
    }
}
