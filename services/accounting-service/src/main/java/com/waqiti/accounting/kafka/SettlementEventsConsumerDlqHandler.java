package com.waqiti.accounting.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.accounting.service.DlqRecoveryService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

/**
 * DLQ Handler for SettlementEventsConsumer
 * Enhanced with automated DLQ recovery system
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 */
@Service
@Slf4j
public class SettlementEventsConsumerDlqHandler extends AccountingDlqHandler {

    public SettlementEventsConsumerDlqHandler(
            MeterRegistry meterRegistry,
            DlqRecoveryService dlqRecoveryService,
            ObjectMapper objectMapper) {
        super(meterRegistry, dlqRecoveryService, objectMapper);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("SettlementEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.SettlementEventsConsumer.dlq:SettlementEventsConsumer.dlq}",
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
            log.info("DLQ: Processing Settlement event recovery");

            String settlementId = headers.getOrDefault("settlementId", "").toString();
            String merchantId = headers.getOrDefault("merchantId", "").toString();
            String amount = headers.getOrDefault("amount", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: Merchant not found
            if (failureReason.contains("merchant not found")) {
                log.error("DLQ: CRITICAL - Settlement for non-existent merchant: {}", merchantId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 2: Bank transfer failure
            if (failureReason.contains("bank transfer") || failureReason.contains("payout failed")) {
                log.warn("DLQ: Bank transfer failed for settlement: settlementId={}, merchantId={}", settlementId, merchantId);
                // Retry bank transfer
                return DlqProcessingResult.RETRY;
            }

            // Strategy 3: Insufficient platform balance
            if (failureReason.contains("insufficient platform balance")) {
                log.error("DLQ: CRITICAL - Insufficient platform balance for settlement: amount={}", amount);
                // Alert finance team immediately
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 4: Settlement already completed (duplicate)
            if (failureReason.contains("duplicate") || failureReason.contains("already settled")) {
                log.info("DLQ: Duplicate settlement event: settlementId={}", settlementId);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 5: Tax calculation failure
            if (failureReason.contains("tax calculation")) {
                log.warn("DLQ: Tax calculation failed for settlement: settlementId={}", settlementId);
                // Retry with manual tax override
                return DlqProcessingResult.RETRY;
            }

            // Strategy 6: Fee calculation failure
            if (failureReason.contains("fee calculation")) {
                log.warn("DLQ: Fee calculation failed for settlement: settlementId={}", settlementId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 7: Notification failure (non-critical)
            if (failureReason.contains("notification")) {
                log.info("DLQ: Settlement completed but notification failed: settlementId={}", settlementId);
                return DlqProcessingResult.SUCCESS;
            }

            // Strategy 8: Transient errors
            if (failureReason.contains("timeout") || failureReason.contains("database")) {
                log.info("DLQ: Transient error during settlement: {}", failureReason);
                return DlqProcessingResult.RETRY;
            }

            // Default: Manual review (settlements are critical financial operations)
            log.error("DLQ: Unknown settlement failure, finance review required: {}", failureReason);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("DLQ: Error handling settlement event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "SettlementEventsConsumer";
    }
}
