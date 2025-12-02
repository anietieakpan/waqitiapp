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
 * DLQ Handler for LoanDisbursementEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class LoanDisbursementEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public LoanDisbursementEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("LoanDisbursementEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.LoanDisbursementEventConsumer.dlq:LoanDisbursementEventConsumer.dlq}",
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
            log.error("DLQ: CRITICAL - Loan disbursement recovery");
            String loanId = headers.getOrDefault("loanId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String amount = headers.getOrDefault("amount", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: Account not found
            if (failureReason.contains("account not found")) {
                log.error("DLQ: Loan account not found: {}", loanId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 2: Insufficient platform funds (CRITICAL)
            if (failureReason.contains("insufficient platform funds")) {
                log.error("DLQ: CRITICAL - Platform cannot disburse loan: loanId={}, amount={}", loanId, amount);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 3: Bank transfer failure
            if (failureReason.contains("bank transfer") || failureReason.contains("ACH failed")) {
                log.warn("DLQ: Bank transfer failed for loan: {}", loanId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 4: Duplicate disbursement
            if (failureReason.contains("duplicate") || failureReason.contains("already disbursed")) {
                log.info("DLQ: Loan already disbursed: {}", loanId);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 5: Regulatory hold
            if (failureReason.contains("regulatory") || failureReason.contains("compliance hold")) {
                log.warn("DLQ: Loan under regulatory hold: {}", loanId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 6: Credit limit exceeded
            if (failureReason.contains("credit limit")) {
                log.error("DLQ: User credit limit exceeded for loan: userId={}", userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 7: Transient errors
            if (failureReason.contains("timeout") || failureReason.contains("database")) {
                log.info("DLQ: Transient error, retrying loan disbursement");
                return DlqProcessingResult.RETRY;
            }

            // Default: CRITICAL - loans are money movement
            log.error("DLQ: Unknown loan disbursement failure: {}", failureReason);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("DLQ: Error handling loan disbursement", e);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    @Override
    protected String getServiceName() {
        return "LoanDisbursementEventConsumer";
    }
}
