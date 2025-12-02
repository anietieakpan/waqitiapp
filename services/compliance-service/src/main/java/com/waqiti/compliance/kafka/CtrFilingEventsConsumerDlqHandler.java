package com.waqiti.compliance.kafka;

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
 * DLQ Handler for CtrFilingEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class CtrFilingEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public CtrFilingEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("CtrFilingEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.CtrFilingEventsConsumer.dlq:CtrFilingEventsConsumer.dlq}",
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
            log.error("DLQ: CTR filing (Currency Transaction Report - FEDERAL LAW)");
            String ctrId = headers.getOrDefault("ctrId", "").toString();
            String transactionId = headers.getOrDefault("transactionId", "").toString();
            String amount = headers.getOrDefault("amount", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("FinCEN") || failureReason.contains("filing")) {
                log.error("DLQ: CTR filing to FinCEN failed: ctrId={}", ctrId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
            if (failureReason.contains("deadline") || failureReason.contains("15 day")) {
                log.error("DLQ: CTR deadline risk: ctrId={}", ctrId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
            if (failureReason.contains("duplicate")) return DlqProcessingResult.DISCARDED;
            if (failureReason.contains("validation") || failureReason.contains("incomplete")) {
                log.error("DLQ: CTR data validation failed: ctrId={}", ctrId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
            if (failureReason.contains("timeout")) return DlqProcessingResult.RETRY;
            log.error("DLQ: CTR filing failed (COMPLIANCE CRITICAL): ctrId={}", ctrId);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        } catch (Exception e) {
            log.error("DLQ: Error in CTR filing handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "CtrFilingEventsConsumer";
    }
}
