package com.waqiti.insurance.kafka;

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
 * DLQ Handler for InsurancePolicyEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class InsurancePolicyEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public InsurancePolicyEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("InsurancePolicyEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.InsurancePolicyEventsConsumer.dlq:InsurancePolicyEventsConsumer.dlq}",
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
            log.warn("DLQ: Insurance policy event recovery");
            String policyId = headers.getOrDefault("policyId", "").toString();
            String eventType = headers.getOrDefault("eventType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("issuance") || failureReason.contains("activation")) {
                log.error("DLQ: Policy issuance failed: policyId={}", policyId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
            if (failureReason.contains("cancellation") || failureReason.contains("termination")) {
                log.error("DLQ: Policy cancellation failed: policyId={}", policyId);
                return DlqProcessingResult.RETRY;
            }
            if (failureReason.contains("duplicate")) return DlqProcessingResult.DISCARDED;
            if (failureReason.contains("timeout")) return DlqProcessingResult.RETRY;
            return DlqProcessingResult.RETRY;

        } catch (Exception e) {
            log.error("Error handling DLQ event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "InsurancePolicyEventsConsumer";
    }
}
