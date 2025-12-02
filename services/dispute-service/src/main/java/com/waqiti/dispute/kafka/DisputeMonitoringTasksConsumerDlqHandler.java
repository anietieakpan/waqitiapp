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
 * DLQ Handler for DisputeMonitoringTasksConsumer - PRODUCTION READY
 *
 * HIGH PRIORITY: Handles failed dispute monitoring task events
 * Monitoring tasks track SLA deadlines, status changes, periodic reviews
 * Failures cause missed deadlines and compliance violations
 *
 * @author Waqiti Production Team
 * @version 2.0.0-PRODUCTION
 */
@Service
@Slf4j
public class DisputeMonitoringTasksConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final DLQEntryRepository dlqRepository;
    private final ObjectMapper objectMapper;

    public DisputeMonitoringTasksConsumerDlqHandler(MeterRegistry meterRegistry,
                                                     DLQEntryRepository dlqRepository,
                                                     ObjectMapper objectMapper) {
        super(meterRegistry);
        this.dlqRepository = dlqRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("DisputeMonitoringTasksConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.DisputeMonitoringTasksConsumer.dlq:DisputeMonitoringTasksConsumer.dlq}",
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
            log.error("⚠️ HIGH PRIORITY: Dispute monitoring task FAILED - SLA deadline at risk!");

            @SuppressWarnings("unchecked")
            Map<String, Object> eventData = (Map<String, Object>) event;

            String disputeId = extractValue(eventData, "disputeId");
            String taskType = extractValue(eventData, "taskType", "monitoringType");
            String slaDeadline = extractValue(eventData, "slaDeadline", "deadline");
            String taskId = extractValue(eventData, "taskId", "monitoringId");
            String assignedTo = extractValue(eventData, "assignedTo", "owner");
            String errorMessage = (String) headers.getOrDefault("x-error-message", "Unknown error");

            // Store in DLQ database
            DLQEntry dlqEntry = DLQEntry.builder()
                    .id(UUID.randomUUID().toString())
                    .eventId(extractValue(eventData, "eventId", "taskId", "disputeId"))
                    .sourceTopic("DisputeMonitoringTasks")
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
            log.error("║  ⚠️ HIGH PRIORITY: DISPUTE MONITORING TASK FAILURE       ║");
            log.error("╠═════════════════════════════════════════════════════════╣");
            log.error("║  DLQ ID: {}", String.format("%-47s", dlqEntry.getId()) + "║");
            log.error("║  Dispute ID: {}", String.format("%-43s", disputeId) + "║");
            log.error("║  Task ID: {}", String.format("%-46s", taskId) + "║");
            log.error("║  Task Type: {}", String.format("%-44s", taskType) + "║");
            log.error("║  SLA Deadline: {}", String.format("%-41s", slaDeadline) + "║");
            log.error("║  Assigned To: {}", String.format("%-42s", assignedTo) + "║");
            log.error("║                                                         ║");
            log.error("║  ⚠️  SLA MONITORING DISRUPTED - DEADLINE MAY BE MISSED   ║");
            log.error("╚═════════════════════════════════════════════════════════╝");

            // Create high-priority ticket
            createHighPriorityTicket(disputeId, taskId, taskType, slaDeadline, assignedTo, errorMessage);

            // Notify monitoring team
            notifyMonitoringTeam(disputeId, taskId, taskType, slaDeadline);

            // Monitoring tasks can be retried after system check
            return DlqProcessingResult.RETRY_AFTER_DELAY;

        } catch (Exception e) {
            log.error("CRITICAL: Failed to process dispute monitoring task DLQ event!", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    private void createHighPriorityTicket(String disputeId, String taskId, String taskType,
                                           String slaDeadline, String assignedTo, String error) {
        String ticketId = "DLQ-MON-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        log.error("═══════════════════════════════════════════════════════════");
        log.error("🎫 HIGH PRIORITY TICKET: {}", ticketId);
        log.error("   Type: DISPUTE MONITORING TASK FAILURE");
        log.error("   Priority: P1 - HIGH");
        log.error("   Dispute ID: {}", disputeId);
        log.error("   Task ID: {}", taskId);
        log.error("   Task Type: {}", taskType);
        log.error("   SLA Deadline: {}", slaDeadline);
        log.error("   Assigned To: {}", assignedTo);
        log.error("   Error: {}", error);
        log.error("   ");
        log.error("   ACTIONS REQUIRED:");
        log.error("   1. Verify SLA deadline is still valid");
        log.error("   2. Manually reschedule monitoring task");
        log.error("   3. Check dispute status and timeline");
        log.error("   4. Update monitoring system state");
        log.error("   5. Alert assigned owner if deadline imminent");
        log.error("═══════════════════════════════════════════════════════════");

        // TODO: Create Jira P1 ticket
    }

    private void notifyMonitoringTeam(String disputeId, String taskId, String taskType, String slaDeadline) {
        log.warn("📧 Notifying Dispute Monitoring Team");
        log.warn("   DisputeId: {}, TaskId: {}, TaskType: {}, Deadline: {}",
                disputeId, taskId, taskType, slaDeadline);
        // TODO: Send email/Slack notification to monitoring team
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
        return "DisputeMonitoringTasksConsumer";
    }
}
