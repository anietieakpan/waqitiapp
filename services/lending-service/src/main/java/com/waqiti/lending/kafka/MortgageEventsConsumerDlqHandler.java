package com.waqiti.lending.kafka;

import com.waqiti.common.kafka.BaseDlqConsumer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;

/**
 * DLQ Handler for MortgageEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class MortgageEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public MortgageEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("MortgageEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.MortgageEventsConsumer.dlq:MortgageEventsConsumer.dlq}",
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
            log.error("DLQ: Mortgage event recovery (FINANCIAL CRITICAL)");
            String mortgageId = headers.getOrDefault("mortgageId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String eventType = headers.getOrDefault("eventType", "").toString();
            String amount = headers.getOrDefault("amount", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: Mortgage payment processing failure (CRITICAL)
            if (eventType.contains("PAYMENT") && failureReason.contains("processing")) {
                log.error("DLQ: Mortgage payment failed: mortgageId={}, amount={}", mortgageId, amount);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 2: Escrow account failure
            if (failureReason.contains("escrow")) {
                log.error("DLQ: Escrow operation failed: mortgageId={}", mortgageId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 3: Property lien recording failure
            if (eventType.contains("LIEN") || failureReason.contains("recording")) {
                log.error("DLQ: Lien recording failed: mortgageId={}", mortgageId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 4: Interest rate adjustment failure
            if (eventType.contains("RATE") || failureReason.contains("adjustment")) {
                log.error("DLQ: Rate adjustment failed: mortgageId={}", mortgageId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 5: Foreclosure notification failure
            if (eventType.contains("FORECLOSURE")) {
                log.error("DLQ: Foreclosure notification failed: mortgageId={}", mortgageId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 6: Duplicate event
            if (failureReason.contains("duplicate")) {
                return DlqProcessingResult.DISCARDED;
            }

            log.error("DLQ: Mortgage event failed: mortgageId={}, type={}", mortgageId, eventType);
            return DlqProcessingResult.RETRY;

        } catch (Exception e) {
            log.error("DLQ: Error in mortgage event handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "MortgageEventsConsumer";
    }
}
