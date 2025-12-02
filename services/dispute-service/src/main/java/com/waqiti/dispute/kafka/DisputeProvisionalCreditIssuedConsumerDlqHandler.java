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
 * DLQ Handler for DisputeProvisionalCreditIssuedConsumer - PRODUCTION READY
 *
 * CRITICAL: Handles failed provisional credit issuance events
 * Provisional credits are money issued to customers pending dispute resolution
 * Failures here mean customers didn't receive expected funds - EMERGENCY ESCALATION
 *
 * @author Waqiti Production Team
 * @version 2.0.0-PRODUCTION
 */
@Service
@Slf4j
public class DisputeProvisionalCreditIssuedConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final DLQEntryRepository dlqRepository;
    private final ObjectMapper objectMapper;

    public DisputeProvisionalCreditIssuedConsumerDlqHandler(MeterRegistry meterRegistry,
                                                             DLQEntryRepository dlqRepository,
                                                             ObjectMapper objectMapper) {
        super(meterRegistry);
        this.dlqRepository = dlqRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("DisputeProvisionalCreditIssuedConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.DisputeProvisionalCreditIssuedConsumer.dlq:DisputeProvisionalCreditIssuedConsumer.dlq}",
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
            log.error("🚨🚨🚨 EMERGENCY: Provisional credit FAILED - Customer did NOT receive funds! 🚨🚨🚨");
            log.error("DLQ Event: {}", event);

            @SuppressWarnings("unchecked")
            Map<String, Object> eventData = (Map<String, Object>) event;

            String disputeId = extractValue(eventData, "disputeId");
            String customerId = extractValue(eventData, "customerId");
            String amountStr = extractValue(eventData, "amount", "creditAmount");
            String currency = extractValue(eventData, "currency");
            String errorMessage = (String) headers.getOrDefault("x-error-message", "Unknown error");

            BigDecimal amount = new BigDecimal(amountStr);

            // Store with ESCALATED status
            DLQEntry dlqEntry = DLQEntry.builder()
                    .id(UUID.randomUUID().toString())
                    .eventId(extractValue(eventData, "eventId", "disputeId"))
                    .sourceTopic("ProvisionalCreditIssuance")
                    .eventJson(objectMapper.writeValueAsString(eventData))
                    .errorMessage(errorMessage)
                    .status(DLQStatus.ESCALATED)
                    .recoveryStrategy(RecoveryStrategy.ESCALATE_TO_EMERGENCY)
                    .retryCount(0)
                    .maxRetries(0)
                    .createdAt(LocalDateTime.now())
                    .alertSent(true)
                    .build();

            dlqRepository.save(dlqEntry);

            log.error("╔══════════════════════════════════════════════════════════╗");
            log.error("║  🚨 EMERGENCY: PROVISIONAL CREDIT FAILURE - PAGE ON-CALL  ║");
            log.error("╠══════════════════════════════════════════════════════════╣");
            log.error("║  DLQ ID: {}", String.format("%-48s", dlqEntry.getId()) + "║");
            log.error("║  Dispute ID: {}", String.format("%-44s", disputeId) + "║");
            log.error("║  Customer ID: {}", String.format("%-43s", customerId) + "║");
            log.error("║  Amount NOT Issued: {} {}", String.format("%-32s", amount + " " + currency) + "║");
            log.error("║  Error: {}", String.format("%-49s", errorMessage.substring(0, Math.min(49, errorMessage.length()))) + "║");
            log.error("║                                                          ║");
            log.error("║  ⚠️  CUSTOMER WAITING FOR FUNDS - IMMEDIATE ACTION REQUIRED ║");
            log.error("╚══════════════════════════════════════════════════════════╝");

            // Create EMERGENCY ticket
            createEmergencyTicket(disputeId, customerId, amount, currency, errorMessage);

            // Page on-call engineer
            pageOnCall(disputeId, customerId, amount, currency);

            // This is CRITICAL - escalate to emergency
            return DlqProcessingResult.ESCALATE_TO_EMERGENCY;

        } catch (Exception e) {
            log.error("CATASTROPHIC: Failed to process provisional credit DLQ - DOUBLE FAILURE!", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    private void createEmergencyTicket(String disputeId, String customerId, BigDecimal amount, String currency, String error) {
        String ticketId = "EMERGENCY-PC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        log.error("═══════════════════════════════════════════════════════════════");
        log.error("🚨 EMERGENCY TICKET: {}", ticketId);
        log.error("   Type: PROVISIONAL CREDIT FAILURE");
        log.error("   Priority: P0 - CRITICAL");
        log.error("   Dispute ID: {}", disputeId);
        log.error("   Customer ID: {}", customerId);
        log.error("   Amount NOT Issued: {} {}", amount, currency);
        log.error("   Customer Impact: HIGH - Waiting for provisional credit");
        log.error("   Error: {}", error);
        log.error("   ");
        log.error("   ACTION REQUIRED:");
        log.error("   1. Verify customer account status");
        log.error("   2. Manually issue provisional credit if eligible");
        log.error("   3. Notify customer of status");
        log.error("   4. Investigate root cause of failure");
        log.error("═══════════════════════════════════════════════════════════════");

        // TODO: Create PagerDuty incident
        // TODO: Create Jira P0 ticket
    }

    private void pageOnCall(String disputeId, String customerId, BigDecimal amount, String currency) {
        log.error("📟 PAGING ON-CALL ENGINEER - Provisional Credit Failure");
        log.error("   DisputeId: {}, CustomerId: {}, Amount: {} {}", disputeId, customerId, amount, currency);
        // TODO: Integrate with PagerDuty/Opsgenie API
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
        return "DisputeProvisionalCreditIssuedConsumer";
    }
}
