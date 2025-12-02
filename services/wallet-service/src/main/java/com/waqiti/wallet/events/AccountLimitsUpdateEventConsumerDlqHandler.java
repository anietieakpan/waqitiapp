package com.waqiti.wallet.events;

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
 * DLQ Handler for AccountLimitsUpdateEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class AccountLimitsUpdateEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public AccountLimitsUpdateEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("AccountLimitsUpdateEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.AccountLimitsUpdateEventConsumer.dlq:AccountLimitsUpdateEventConsumer.dlq}",
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
            log.info("DLQ: Processing account limits update recovery");
            String accountId = headers.getOrDefault("accountId", "").toString();
            String limitType = headers.getOrDefault("limitType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: Account not found
            if (failureReason.contains("account not found")) {
                log.error("DLQ: Limits update for non-existent account: {}", accountId);
                return DlqProcessingResult.PERMANENT_FAILURE;
            }

            // Strategy 2: Invalid limit value
            if (failureReason.contains("invalid limit") || failureReason.contains("negative")) {
                log.error("DLQ: Invalid limit value: accountId={}, type={}", accountId, limitType);
                return DlqProcessingResult.PERMANENT_FAILURE;
            }

            // Strategy 3: KYC tier mismatch
            if (failureReason.contains("KYC") || failureReason.contains("tier")) {
                log.warn("DLQ: KYC tier prevents limit update: accountId={}", accountId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 4: Regulatory limit violation
            if (failureReason.contains("regulatory") || failureReason.contains("exceeds maximum")) {
                log.warn("DLQ: Limit exceeds regulatory maximum: accountId={}", accountId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 5: Duplicate update
            if (failureReason.contains("duplicate") || failureReason.contains("already set")) {
                log.info("DLQ: Limit already updated: accountId={}", accountId);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 6: Transient
            if (failureReason.contains("timeout") || failureReason.contains("database")) {
                log.info("DLQ: Transient error, retrying limits update");
                return DlqProcessingResult.RETRY;
            }

            log.warn("DLQ: Unknown limits update failure: {}", failureReason);
            return DlqProcessingResult.RETRY;

        } catch (Exception e) {
            log.error("DLQ: Error handling limits update", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "AccountLimitsUpdateEventConsumer";
    }
}
