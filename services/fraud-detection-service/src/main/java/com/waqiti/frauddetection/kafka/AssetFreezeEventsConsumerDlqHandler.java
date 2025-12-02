package com.waqiti.frauddetection.kafka;

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
 * DLQ Handler for AssetFreezeEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class AssetFreezeEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public AssetFreezeEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("AssetFreezeEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.AssetFreezeEventsConsumer.dlq:AssetFreezeEventsConsumer.dlq}",
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
            // FIXED: Implement asset freeze recovery logic - CRITICAL
            log.error("Processing DLQ asset freeze event - COMPLIANCE CRITICAL");

            String freezeId = headers.getOrDefault("freezeId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String assetType = headers.getOrDefault("assetType", "").toString();
            String amount = headers.getOrDefault("amount", "0").toString();
            String reason = headers.getOrDefault("reason", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.error("DLQ: Database error for CRITICAL asset freeze. User: {}. Retrying immediately.", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("FreezeFailed") || failureReason.contains("LockFailed")) {
                log.error("DLQ: CRITICAL - Failed to freeze assets. User: {}, Asset: {}. Retrying.", userId, assetType);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyFrozen")) {
                log.info("DLQ: Assets already frozen. Freeze: {}. Marking as resolved.", freezeId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("UserNotFound") || failureReason.contains("AssetNotFound")) {
                log.warn("DLQ: User/asset not found for freeze. User: {}. Retrying.", userId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("ComplianceNotificationFailed")) {
                log.error("DLQ: Failed to notify compliance of asset freeze. Freeze: {}. Retrying.", freezeId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in asset freeze. Freeze: {}. URGENT manual review.", freezeId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in CRITICAL asset freeze. Event: {}, Headers: {}.", event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ asset freeze event - COMPLIANCE CRITICAL", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "AssetFreezeEventsConsumer";
    }
}
