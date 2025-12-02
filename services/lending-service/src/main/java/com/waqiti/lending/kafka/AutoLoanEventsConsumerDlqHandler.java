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
 * DLQ Handler for AutoLoanEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class AutoLoanEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public AutoLoanEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("AutoLoanEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.AutoLoanEventsConsumer.dlq:AutoLoanEventsConsumer.dlq}",
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
            log.warn("DLQ: Auto loan recovery");
            String loanId = headers.getOrDefault("loanId", "").toString();
            String eventType = headers.getOrDefault("eventType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("repossession") || failureReason.contains("collateral")) {
                log.error("DLQ: Auto repossession event failed: loanId={}", loanId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
            if (failureReason.contains("title") || failureReason.contains("lien")) {
                log.error("DLQ: Title/lien update failed: loanId={}", loanId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
            if (failureReason.contains("duplicate")) return DlqProcessingResult.DISCARDED;
            if (failureReason.contains("valuation") || failureReason.contains("appraisal")) {
                log.warn("DLQ: Vehicle valuation failed: loanId={}", loanId);
                return DlqProcessingResult.RETRY;
            }
            if (failureReason.contains("timeout")) return DlqProcessingResult.RETRY;
            log.warn("DLQ: Auto loan event failed: loanId={}, type={}", loanId, eventType);
            return DlqProcessingResult.RETRY;
        } catch (Exception e) {
            log.error("DLQ: Error in auto loan handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "AutoLoanEventsConsumer";
    }
}
