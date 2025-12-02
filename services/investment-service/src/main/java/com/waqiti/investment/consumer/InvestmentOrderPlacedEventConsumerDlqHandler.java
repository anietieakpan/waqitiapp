package com.waqiti.investment.consumer;

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
 * DLQ Handler for InvestmentOrderPlacedEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class InvestmentOrderPlacedEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public InvestmentOrderPlacedEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("InvestmentOrderPlacedEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.InvestmentOrderPlacedEventConsumer.dlq:InvestmentOrderPlacedEventConsumer.dlq}",
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
            log.info("DLQ: Processing investment order recovery");
            String orderId = headers.getOrDefault("orderId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String symbol = headers.getOrDefault("symbol", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: Investment account not found
            if (failureReason.contains("account not found")) {
                log.error("DLQ: Investment account not found: orderId={}", orderId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 2: Insufficient balance
            if (failureReason.contains("insufficient balance")) {
                log.info("DLQ: Insufficient funds for investment: orderId={}", orderId);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 3: Market closed
            if (failureReason.contains("market closed") || failureReason.contains("trading hours")) {
                log.info("DLQ: Order placed outside market hours: orderId={}", orderId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 4: Duplicate order
            if (failureReason.contains("duplicate")) {
                log.info("DLQ: Duplicate investment order: orderId={}", orderId);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 5: Price validation failure
            if (failureReason.contains("price") || failureReason.contains("limit exceeded")) {
                log.warn("DLQ: Investment price validation failed: orderId={}", orderId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 6: Regulatory/compliance check
            if (failureReason.contains("accredited investor") || failureReason.contains("SEC")) {
                log.warn("DLQ: Investment regulatory check failed: orderId={}", orderId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 7: Transient
            if (failureReason.contains("timeout") || failureReason.contains("broker")) {
                log.info("DLQ: Transient error, retrying investment order");
                return DlqProcessingResult.RETRY;
            }

            log.warn("DLQ: Unknown investment order failure: {}", failureReason);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("DLQ: Error handling investment order", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "InvestmentOrderPlacedEventConsumer";
    }
}
