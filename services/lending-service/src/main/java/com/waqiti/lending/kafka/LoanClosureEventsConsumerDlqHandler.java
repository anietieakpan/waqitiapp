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
 * DLQ Handler for LoanClosureEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class LoanClosureEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public LoanClosureEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("LoanClosureEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.LoanClosureEventsConsumer.dlq:LoanClosureEventsConsumer.dlq}",
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
            log.warn("DLQ: Loan closure recovery");
            String closureId = headers.getOrDefault("closureId", "").toString();
            String loanId = headers.getOrDefault("loanId", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("balance") || failureReason.contains("outstanding")) {
                log.error("DLQ: Loan closure with outstanding balance: loanId={}", loanId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
            if (failureReason.contains("lien") || failureReason.contains("collateral")) {
                log.error("DLQ: Lien release failed: loanId={}", loanId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
            if (failureReason.contains("duplicate")) return DlqProcessingResult.DISCARDED;
            if (failureReason.contains("credit bureau")) {
                log.warn("DLQ: Closure credit reporting failed: loanId={}", loanId);
                return DlqProcessingResult.RETRY;
            }
            if (failureReason.contains("timeout")) return DlqProcessingResult.RETRY;
            return DlqProcessingResult.RETRY;

        } catch (Exception e) {
            log.error("Error handling DLQ event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "LoanClosureEventsConsumer";
    }
}
