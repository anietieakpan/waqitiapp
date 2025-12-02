package com.waqiti.payment.kafka;

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
 * DLQ Handler for FundReleaseEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class FundReleaseEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public FundReleaseEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("FundReleaseEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.FundReleaseEventConsumer.dlq:FundReleaseEventConsumer.dlq}",
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
            log.error("DLQ: Fund release recovery (CRITICAL)");
            String releaseId = headers.getOrDefault("releaseId", "").toString();
            String holdId = headers.getOrDefault("holdId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String amount = headers.getOrDefault("amount", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: Release credit failed (CRITICAL - funds locked)
            if (failureReason.contains("credit") || failureReason.contains("release failed")) {
                log.error("DLQ: Fund release credit failed: releaseId={}, amount={}", releaseId, amount);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 2: Hold not found (already released or expired)
            if (failureReason.contains("hold not found") || failureReason.contains("expired")) {
                log.warn("DLQ: Hold not found/expired: holdId={}", holdId);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 3: Wallet unavailable
            if (failureReason.contains("wallet") || failureReason.contains("unavailable")) {
                log.error("DLQ: Wallet unavailable for fund release: userId={}", userId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 4: Duplicate release
            if (failureReason.contains("duplicate") || failureReason.contains("already released")) {
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 5: Ledger update failure
            if (failureReason.contains("ledger")) {
                log.error("DLQ: Fund release ledger update failed: releaseId={}", releaseId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 6: Transient
            if (failureReason.contains("timeout")) {
                return DlqProcessingResult.RETRY;
            }

            log.error("DLQ: Fund release failed: releaseId={}, holdId={}", releaseId, holdId);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("DLQ: Error in fund release handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "FundReleaseEventConsumer";
    }
}
