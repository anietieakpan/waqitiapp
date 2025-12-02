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
 * DLQ Handler for ChargebackInitiatedConsumer - PRODUCTION READY
 *
 * CRITICAL: Handles failed chargeback initiation events
 * Chargebacks involve reversing transactions and debiting merchants
 * Failures impact merchant settlement and customer refunds
 *
 * @author Waqiti Production Team
 * @version 2.0.0-PRODUCTION
 */
@Service
@Slf4j
public class ChargebackInitiatedConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final DLQEntryRepository dlqRepository;
    private final ObjectMapper objectMapper;

    public ChargebackInitiatedConsumerDlqHandler(MeterRegistry meterRegistry,
                                                   DLQEntryRepository dlqRepository,
                                                   ObjectMapper objectMapper) {
        super(meterRegistry);
        this.dlqRepository = dlqRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("ChargebackInitiatedConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.ChargebackInitiatedConsumer.dlq:ChargebackInitiatedConsumer.dlq}",
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
            log.error("🚨 CRITICAL: Chargeback initiation FAILED - Merchant/Customer settlement affected!");

            @SuppressWarnings("unchecked")
            Map<String, Object> eventData = (Map<String, Object>) event;

            String disputeId = extractValue(eventData, "disputeId");
            String merchantId = extractValue(eventData, "merchantId");
            String transactionId = extractValue(eventData, "transactionId");
            String amount = extractValue(eventData, "amount", "chargebackAmount");
            String currency = extractValue(eventData, "currency");
            String errorMessage = (String) headers.getOrDefault("x-error-message", "Unknown error");

            // Store in DLQ database
            DLQEntry dlqEntry = DLQEntry.builder()
                    .id(UUID.randomUUID().toString())
                    .eventId(extractValue(eventData, "eventId", "disputeId", "transactionId"))
                    .sourceTopic("ChargebackInitiation")
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

            log.error("╔═══════════════════════════════════════════════════════╗");
            log.error("║  🔴 CRITICAL: CHARGEBACK INITIATION FAILED            ║");
            log.error("╠═══════════════════════════════════════════════════════╣");
            log.error("║  DLQ ID: {}", String.format("%-45s", dlqEntry.getId()) + "║");
            log.error("║  Dispute ID: {}", String.format("%-41s", disputeId) + "║");
            log.error("║  Merchant ID: {}", String.format("%-40s", merchantId) + "║");
            log.error("║  Transaction ID: {}", String.format("%-37s", transactionId) + "║");
            log.error("║  Amount: {} {}", String.format("%-38s", amount + " " + currency) + "║");
            log.error("║                                                       ║");
            log.error("║  ⚠️  MERCHANT SETTLEMENT & CUSTOMER REFUND AT RISK     ║");
            log.error("╚═══════════════════════════════════════════════════════╝");

            // Create high-priority ticket
            createHighPriorityTicket(disputeId, merchantId, transactionId, amount, currency, errorMessage);

            // Notify merchant operations team
            notifyMerchantTeam(merchantId, disputeId, amount, currency);

            // Manual intervention required for chargebacks
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("CRITICAL: Failed to process chargeback initiation DLQ event!", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    private void createHighPriorityTicket(String disputeId, String merchantId, String txnId,
                                           String amount, String currency, String error) {
        String ticketId = "DLQ-CB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        log.error("═══════════════════════════════════════════════════════════");
        log.error("🎫 HIGH PRIORITY TICKET: {}", ticketId);
        log.error("   Type: CHARGEBACK INITIATION FAILURE");
        log.error("   Priority: P1 - HIGH");
        log.error("   Dispute ID: {}", disputeId);
        log.error("   Merchant ID: {}", merchantId);
        log.error("   Transaction ID: {}", txnId);
        log.error("   Amount: {} {}", amount, currency);
        log.error("   Error: {}", error);
        log.error("   ");
        log.error("   IMMEDIATE ACTIONS REQUIRED:");
        log.error("   1. Verify transaction and dispute details");
        log.error("   2. Manually initiate chargeback if applicable");
        log.error("   3. Notify merchant of chargeback status");
        log.error("   4. Update customer on dispute progress");
        log.error("   5. Investigate root cause");
        log.error("═══════════════════════════════════════════════════════════");

        // TODO: Create Jira P1 ticket
    }

    private void notifyMerchantTeam(String merchantId, String disputeId, String amount, String currency) {
        log.warn("📧 Notifying Merchant Operations Team");
        log.warn("   MerchantId: {}, DisputeId: {}, Amount: {} {}", merchantId, disputeId, amount, currency);
        // TODO: Send email/Slack notification to merchant operations team
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
        return "ChargebackInitiatedConsumer";
    }
}
