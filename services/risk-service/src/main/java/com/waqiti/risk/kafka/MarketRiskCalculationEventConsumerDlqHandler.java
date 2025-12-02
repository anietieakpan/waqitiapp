package com.waqiti.risk.kafka;

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
 * DLQ Handler for MarketRiskCalculationEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class MarketRiskCalculationEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public MarketRiskCalculationEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("MarketRiskCalculationEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.MarketRiskCalculationEventConsumer.dlq:MarketRiskCalculationEventConsumer.dlq}",
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
            log.info("Processing DLQ event: topic={}, event={}",
                headers.get("kafka_receivedTopic"), event.getClass().getSimpleName());

            // Determine if this is a critical event
            boolean isCritical = getServiceName().contains("HighRisk") ||
                                 getServiceName().contains("Alert") ||
                                 getServiceName().contains("Critical");

            if (isCritical) {
                log.error("CRITICAL: DLQ event from critical topic: {}", getServiceName());
            }

            // Check if error is recoverable
            Exception lastException = (Exception) headers.get("exception");
            boolean isRecoverable = lastException != null &&
                (lastException instanceof java.net.SocketTimeoutException ||
                 (lastException.getMessage() != null &&
                  (lastException.getMessage().contains("timeout") ||
                   lastException.getMessage().contains("connection") ||
                   lastException.getMessage().contains("unavailable"))));

            if (isRecoverable) {
                log.info("Recoverable error detected - scheduling retry");
                return DlqProcessingResult.RETRY;
            } else {
                log.warn("Non-recoverable error - requires manual intervention");
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

        } catch (Exception e) {
            log.error("Error handling DLQ event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "MarketRiskCalculationEventConsumer";
    }
}
