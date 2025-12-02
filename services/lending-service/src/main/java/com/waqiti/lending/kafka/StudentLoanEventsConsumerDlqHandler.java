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
 * DLQ Handler for StudentLoanEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class StudentLoanEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public StudentLoanEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("StudentLoanEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.StudentLoanEventsConsumer.dlq:StudentLoanEventsConsumer.dlq}",
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
            log.warn("DLQ: Student loan recovery");
            String loanId = headers.getOrDefault("loanId", "").toString();
            String eventType = headers.getOrDefault("eventType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("deferment") || failureReason.contains("forbearance")) {
                log.error("DLQ: Student loan deferment failed: loanId={}", loanId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
            if (failureReason.contains("forgiveness") || failureReason.contains("discharge")) {
                log.error("DLQ: Loan forgiveness processing failed: loanId={}", loanId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
            if (failureReason.contains("duplicate")) return DlqProcessingResult.DISCARDED;
            if (failureReason.contains("servicer") || failureReason.contains("department")) {
                log.warn("DLQ: Servicer integration failed: loanId={}", loanId);
                return DlqProcessingResult.RETRY;
            }
            if (failureReason.contains("timeout")) return DlqProcessingResult.RETRY;
            log.warn("DLQ: Student loan event failed: loanId={}, type={}", loanId, eventType);
            return DlqProcessingResult.RETRY;
        } catch (Exception e) {
            log.error("DLQ: Error in student loan handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "StudentLoanEventsConsumer";
    }
}
