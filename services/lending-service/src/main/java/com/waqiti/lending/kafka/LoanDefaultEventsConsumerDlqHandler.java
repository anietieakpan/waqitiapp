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
 * DLQ Handler for LoanDefaultEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class LoanDefaultEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public LoanDefaultEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("LoanDefaultEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.LoanDefaultEventsConsumer.dlq:LoanDefaultEventsConsumer.dlq}",
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
            log.error("DLQ: Loan default recovery (CRITICAL)");
            String loanId = headers.getOrDefault("loanId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String defaultAmount = headers.getOrDefault("defaultAmount", "").toString();
            String daysPastDue = headers.getOrDefault("daysPastDue", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: Collections trigger failure (CRITICAL)
            if (failureReason.contains("collections") || failureReason.contains("recovery")) {
                log.error("DLQ: Collections trigger failed: loanId={}, amount={}", loanId, defaultAmount);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 2: Credit bureau reporting failure
            if (failureReason.contains("credit bureau") || failureReason.contains("reporting")) {
                log.error("DLQ: Credit bureau reporting failed: loanId={}", loanId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 3: Legal escalation failure
            if (failureReason.contains("legal") || failureReason.contains("attorney")) {
                log.error("DLQ: Legal escalation failed: loanId={}, days={}", loanId, daysPastDue);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 4: Collateral seizure trigger failure
            if (failureReason.contains("collateral") || failureReason.contains("seizure")) {
                log.error("DLQ: Collateral seizure failed: loanId={}", loanId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 5: Customer notification failure
            if (failureReason.contains("notification")) {
                log.error("DLQ: Default notification failed: loanId={}", loanId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 6: Duplicate default event
            if (failureReason.contains("duplicate")) {
                return DlqProcessingResult.DISCARDED;
            }

            log.error("DLQ: Loan default failed: loanId={}, daysPastDue={}", loanId, daysPastDue);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("DLQ: Error in loan default handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "LoanDefaultEventsConsumer";
    }
}
