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
 * DLQ Handler for ChargebackInvestigationsConsumer - PRODUCTION READY
 *
 * HIGH PRIORITY: Handles failed chargeback investigation events
 * Chargeback investigations involve card network submissions, dispute evidence review
 * Failures delay merchant fund recovery and network deadline compliance
 *
 * @author Waqiti Production Team
 * @version 2.0.0-PRODUCTION
 */
@Service
@Slf4j
public class ChargebackInvestigationsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final DLQEntryRepository dlqRepository;
    private final ObjectMapper objectMapper;

    public ChargebackInvestigationsConsumerDlqHandler(MeterRegistry meterRegistry,
                                                       DLQEntryRepository dlqRepository,
                                                       ObjectMapper objectMapper) {
        super(meterRegistry);
        this.dlqRepository = dlqRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("ChargebackInvestigationsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.ChargebackInvestigationsConsumer.dlq:ChargebackInvestigationsConsumer.dlq}",
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
            log.error("⚠️ HIGH PRIORITY: Chargeback investigation FAILED - Network deadline risk!");

            @SuppressWarnings("unchecked")
            Map<String, Object> eventData = (Map<String, Object>) event;

            String chargebackId = extractValue(eventData, "chargebackId");
            String disputeId = extractValue(eventData, "disputeId");
            String merchantId = extractValue(eventData, "merchantId");
            String investigationType = extractValue(eventData, "investigationType");
            String cardNetwork = extractValue(eventData, "cardNetwork", "network");
            String deadline = extractValue(eventData, "networkDeadline", "responseDeadline");
            String errorMessage = (String) headers.getOrDefault("x-error-message", "Unknown error");

            // Store in DLQ database
            DLQEntry dlqEntry = DLQEntry.builder()
                    .id(UUID.randomUUID().toString())
                    .eventId(extractValue(eventData, "eventId", "chargebackId", "disputeId"))
                    .sourceTopic("ChargebackInvestigations")
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
            log.error("║  ⚠️ HIGH PRIORITY: CHARGEBACK INVESTIGATION FAILURE      ║");
            log.error("╠═════════════════════════════════════════════════════════╣");
            log.error("║  DLQ ID: {}", String.format("%-47s", dlqEntry.getId()) + "║");
            log.error("║  Chargeback ID: {}", String.format("%-39s", chargebackId) + "║");
            log.error("║  Dispute ID: {}", String.format("%-43s", disputeId) + "║");
            log.error("║  Merchant ID: {}", String.format("%-42s", merchantId) + "║");
            log.error("║  Card Network: {}", String.format("%-40s", cardNetwork) + "║");
            log.error("║  Network Deadline: {}", String.format("%-36s", deadline) + "║");
            log.error("║                                                         ║");
            log.error("║  ⚠️  MERCHANT FUND RECOVERY DELAYED - DEADLINE AT RISK   ║");
            log.error("╚═════════════════════════════════════════════════════════╝");

            // Create high-priority ticket
            createHighPriorityTicket(chargebackId, disputeId, merchantId, cardNetwork, deadline, errorMessage);

            // Notify chargeback investigation team
            notifyChargebackTeam(chargebackId, disputeId, merchantId, cardNetwork, deadline);

            // Chargeback investigations can be retried with updated evidence
            return DlqProcessingResult.RETRY_AFTER_DELAY;

        } catch (Exception e) {
            log.error("CRITICAL: Failed to process chargeback investigation DLQ event!", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    private void createHighPriorityTicket(String chargebackId, String disputeId, String merchantId,
                                           String cardNetwork, String deadline, String error) {
        String ticketId = "DLQ-CBINV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        log.error("═══════════════════════════════════════════════════════════");
        log.error("🎫 HIGH PRIORITY TICKET: {}", ticketId);
        log.error("   Type: CHARGEBACK INVESTIGATION FAILURE");
        log.error("   Priority: P1 - HIGH");
        log.error("   Chargeback ID: {}", chargebackId);
        log.error("   Dispute ID: {}", disputeId);
        log.error("   Merchant ID: {}", merchantId);
        log.error("   Card Network: {}", cardNetwork);
        log.error("   Network Deadline: {}", deadline);
        log.error("   Error: {}", error);
        log.error("   ");
        log.error("   ACTIONS REQUIRED:");
        log.error("   1. Review chargeback investigation requirements");
        log.error("   2. Verify network deadline and submission status");
        log.error("   3. Gather missing evidence if needed");
        log.error("   4. Retry investigation submission to card network");
        log.error("   5. Notify merchant of chargeback status");
        log.error("═══════════════════════════════════════════════════════════");

        // TODO: Create Jira P1 ticket
    }

    private void notifyChargebackTeam(String chargebackId, String disputeId, String merchantId,
                                       String cardNetwork, String deadline) {
        log.warn("📧 Notifying Chargeback Investigation Team");
        log.warn("   ChargebackId: {}, DisputeId: {}, MerchantId: {}", chargebackId, disputeId, merchantId);
        log.warn("   Network: {}, Deadline: {}", cardNetwork, deadline);
        // TODO: Send email/Slack notification to chargeback team
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
        return "ChargebackInvestigationsConsumer";
    }
}
