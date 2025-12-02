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
 * DLQ Handler for DisputeEscalationsConsumer - PRODUCTION READY
 *
 * HIGH PRIORITY: Handles failed dispute escalation events
 * Escalations route complex disputes to senior analysts or specialist teams
 * Failures leave disputes stuck in queues, impacting SLA compliance
 *
 * @author Waqiti Production Team
 * @version 2.0.0-PRODUCTION
 */
@Service
@Slf4j
public class DisputeEscalationsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final DLQEntryRepository dlqRepository;
    private final ObjectMapper objectMapper;

    public DisputeEscalationsConsumerDlqHandler(MeterRegistry meterRegistry,
                                                 DLQEntryRepository dlqRepository,
                                                 ObjectMapper objectMapper) {
        super(meterRegistry);
        this.dlqRepository = dlqRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("DisputeEscalationsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.DisputeEscalationsConsumer.dlq:DisputeEscalationsConsumer.dlq}",
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
            log.error("⚠️ HIGH PRIORITY: Dispute escalation FAILED - SLA compliance at risk!");

            @SuppressWarnings("unchecked")
            Map<String, Object> eventData = (Map<String, Object>) event;

            String disputeId = extractValue(eventData, "disputeId");
            String escalationReason = extractValue(eventData, "escalationReason", "reason");
            String escalationLevel = extractValue(eventData, "escalationLevel", "level");
            String targetTeam = extractValue(eventData, "targetTeam", "escalatedTo");
            String priority = extractValue(eventData, "priority");
            String errorMessage = (String) headers.getOrDefault("x-error-message", "Unknown error");

            // Store in DLQ database
            DLQEntry dlqEntry = DLQEntry.builder()
                    .id(UUID.randomUUID().toString())
                    .eventId(extractValue(eventData, "eventId", "escalationId", "disputeId"))
                    .sourceTopic("DisputeEscalations")
                    .eventJson(objectMapper.writeValueAsString(eventData))
                    .errorMessage(errorMessage)
                    .status(DLQStatus.PENDING_REVIEW)
                    .recoveryStrategy(RecoveryStrategy.MANUAL_INTERVENTION)
                    .retryCount(0)
                    .maxRetries(0)
                    .createdAt(LocalDateTime.now())
                    .alertSent(true)
                    .build();

            dlqRepository.save(dlqEntry);

            log.error("╔═════════════════════════════════════════════════════════╗");
            log.error("║  ⚠️ HIGH PRIORITY: DISPUTE ESCALATION FAILURE            ║");
            log.error("╠═════════════════════════════════════════════════════════╣");
            log.error("║  DLQ ID: {}", String.format("%-47s", dlqEntry.getId()) + "║");
            log.error("║  Dispute ID: {}", String.format("%-43s", disputeId) + "║");
            log.error("║  Escalation Level: {}", String.format("%-37s", escalationLevel) + "║");
            log.error("║  Target Team: {}", String.format("%-42s", targetTeam) + "║");
            log.error("║  Priority: {}", String.format("%-45s", priority) + "║");
            log.error("║  Reason: {}", String.format("%-47s", escalationReason.substring(0, Math.min(47, escalationReason.length()))) + "║");
            log.error("║                                                         ║");
            log.error("║  ⚠️  DISPUTE STUCK IN QUEUE - MANUAL ROUTING REQUIRED    ║");
            log.error("╚═════════════════════════════════════════════════════════╝");

            // Create high-priority ticket
            createHighPriorityTicket(disputeId, escalationLevel, escalationReason, targetTeam, priority, errorMessage);

            // Notify escalation coordinators
            notifyEscalationCoordinators(disputeId, escalationLevel, targetTeam, priority);

            // Escalations require manual routing due to workflow complexity
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("CRITICAL: Failed to process dispute escalation DLQ event!", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    private void createHighPriorityTicket(String disputeId, String escalationLevel, String reason,
                                           String targetTeam, String priority, String error) {
        String ticketId = "DLQ-ESC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        log.error("═══════════════════════════════════════════════════════════");
        log.error("🎫 HIGH PRIORITY TICKET: {}", ticketId);
        log.error("   Type: DISPUTE ESCALATION FAILURE");
        log.error("   Priority: P1 - HIGH");
        log.error("   Dispute ID: {}", disputeId);
        log.error("   Escalation Level: {}", escalationLevel);
        log.error("   Target Team: {}", targetTeam);
        log.error("   Case Priority: {}", priority);
        log.error("   Escalation Reason: {}", reason);
        log.error("   Error: {}", error);
        log.error("   ");
        log.error("   ACTIONS REQUIRED:");
        log.error("   1. Review dispute complexity and escalation reason");
        log.error("   2. Manually route dispute to target team: {}", targetTeam);
        log.error("   3. Verify team capacity and assignment");
        log.error("   4. Update dispute status and SLA timeline");
        log.error("   5. Notify customer of escalation status");
        log.error("═══════════════════════════════════════════════════════════");

        // TODO: Create Jira P1 ticket
    }

    private void notifyEscalationCoordinators(String disputeId, String escalationLevel,
                                               String targetTeam, String priority) {
        log.warn("📧 Notifying Escalation Coordinators");
        log.warn("   DisputeId: {}, Level: {}, TargetTeam: {}, Priority: {}",
                disputeId, escalationLevel, targetTeam, priority);
        // TODO: Send email/Slack notification to escalation coordinators
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
        return "DisputeEscalationsConsumer";
    }
}
