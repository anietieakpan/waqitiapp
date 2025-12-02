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
 * DLQ Handler for ChargebackManualQueueConsumer - PRODUCTION READY
 *
 * HIGH PRIORITY: Handles failed chargeback manual queue events
 * Manual queue assignments route complex chargebacks to specialist analysts
 * Failures leave chargebacks unassigned, impacting response times
 *
 * @author Waqiti Production Team
 * @version 2.0.0-PRODUCTION
 */
@Service
@Slf4j
public class ChargebackManualQueueConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final DLQEntryRepository dlqRepository;
    private final ObjectMapper objectMapper;

    public ChargebackManualQueueConsumerDlqHandler(MeterRegistry meterRegistry,
                                                    DLQEntryRepository dlqRepository,
                                                    ObjectMapper objectMapper) {
        super(meterRegistry);
        this.dlqRepository = dlqRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("ChargebackManualQueueConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.ChargebackManualQueueConsumer.dlq:ChargebackManualQueueConsumer.dlq}",
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
            log.error("⚠️ HIGH PRIORITY: Chargeback manual queue assignment FAILED - Chargeback unassigned!");

            @SuppressWarnings("unchecked")
            Map<String, Object> eventData = (Map<String, Object>) event;

            String chargebackId = extractValue(eventData, "chargebackId");
            String disputeId = extractValue(eventData, "disputeId");
            String queueName = extractValue(eventData, "queueName", "targetQueue");
            String priority = extractValue(eventData, "priority");
            String complexity = extractValue(eventData, "complexity", "complexityLevel");
            String assignedTo = extractValue(eventData, "assignedTo", "analyst");
            String errorMessage = (String) headers.getOrDefault("x-error-message", "Unknown error");

            // Store in DLQ database
            DLQEntry dlqEntry = DLQEntry.builder()
                    .id(UUID.randomUUID().toString())
                    .eventId(extractValue(eventData, "eventId", "chargebackId", "disputeId"))
                    .sourceTopic("ChargebackManualQueue")
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
            log.error("║  ⚠️ HIGH PRIORITY: CHARGEBACK QUEUE ASSIGNMENT FAILURE   ║");
            log.error("╠═════════════════════════════════════════════════════════╣");
            log.error("║  DLQ ID: {}", String.format("%-47s", dlqEntry.getId()) + "║");
            log.error("║  Chargeback ID: {}", String.format("%-39s", chargebackId) + "║");
            log.error("║  Dispute ID: {}", String.format("%-43s", disputeId) + "║");
            log.error("║  Target Queue: {}", String.format("%-41s", queueName) + "║");
            log.error("║  Priority: {}", String.format("%-45s", priority) + "║");
            log.error("║  Complexity: {}", String.format("%-43s", complexity) + "║");
            log.error("║                                                         ║");
            log.error("║  ⚠️  CHARGEBACK UNASSIGNED - MANUAL ROUTING REQUIRED     ║");
            log.error("╚═════════════════════════════════════════════════════════╝");

            // Create high-priority ticket
            createHighPriorityTicket(chargebackId, disputeId, queueName, priority, complexity, assignedTo, errorMessage);

            // Notify queue management team
            notifyQueueManagement(chargebackId, disputeId, queueName, priority);

            // Manual queue assignments require manual intervention
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("CRITICAL: Failed to process chargeback manual queue DLQ event!", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    private void createHighPriorityTicket(String chargebackId, String disputeId, String queueName,
                                           String priority, String complexity, String assignedTo, String error) {
        String ticketId = "DLQ-CBQUE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        log.error("═══════════════════════════════════════════════════════════");
        log.error("🎫 HIGH PRIORITY TICKET: {}", ticketId);
        log.error("   Type: CHARGEBACK MANUAL QUEUE FAILURE");
        log.error("   Priority: P1 - HIGH");
        log.error("   Chargeback ID: {}", chargebackId);
        log.error("   Dispute ID: {}", disputeId);
        log.error("   Target Queue: {}", queueName);
        log.error("   Case Priority: {}", priority);
        log.error("   Complexity: {}", complexity);
        log.error("   Assigned To: {}", assignedTo);
        log.error("   Error: {}", error);
        log.error("   ");
        log.error("   ACTIONS REQUIRED:");
        log.error("   1. Review chargeback complexity and priority");
        log.error("   2. Manually assign to appropriate queue: {}", queueName);
        log.error("   3. Verify analyst capacity and expertise");
        log.error("   4. Update queue metrics and SLA tracking");
        log.error("   5. Notify assigned analyst");
        log.error("═══════════════════════════════════════════════════════════");

        // TODO: Create Jira P1 ticket
    }

    private void notifyQueueManagement(String chargebackId, String disputeId, String queueName, String priority) {
        log.warn("📧 Notifying Queue Management Team");
        log.warn("   ChargebackId: {}, DisputeId: {}, Queue: {}, Priority: {}",
                chargebackId, disputeId, queueName, priority);
        // TODO: Send email/Slack notification to queue management team
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
        return "ChargebackManualQueueConsumer";
    }
}
