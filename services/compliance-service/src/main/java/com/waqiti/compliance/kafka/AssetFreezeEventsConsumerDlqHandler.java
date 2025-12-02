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
            log.error("DLQ: Asset freeze recovery (LEGAL/COMPLIANCE CRITICAL)");
            String freezeId = headers.getOrDefault("freezeId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String freezeReason = headers.getOrDefault("freezeReason", "").toString();
            String authorityType = headers.getOrDefault("authorityType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: Court order freeze failure (LEGAL CRITICAL)
            if (authorityType.contains("COURT") || freezeReason.contains("court order")) {
                log.error("DLQ: Court-ordered freeze failed: userId={}, freezeId={}", userId, freezeId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 2: Sanctions/OFAC freeze failure
            if (freezeReason.contains("OFAC") || freezeReason.contains("sanction")) {
                log.error("DLQ: OFAC/sanctions freeze failed: userId={}", userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 3: All accounts not frozen
            if (failureReason.contains("partial") || failureReason.contains("incomplete")) {
                log.error("DLQ: Partial asset freeze: userId={}", userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 4: Legal notification failure
            if (failureReason.contains("notification") || failureReason.contains("authority")) {
                log.error("DLQ: Authority notification failed for freeze: freezeId={}", freezeId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 5: Duplicate freeze order
            if (failureReason.contains("duplicate") || failureReason.contains("already frozen")) {
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 6: Audit trail failure
            if (failureReason.contains("audit") || failureReason.contains("record")) {
                log.error("DLQ: Freeze audit record failed: freezeId={}", freezeId);
                return DlqProcessingResult.RETRY;
            }

            log.error("DLQ: Asset freeze failed: freezeId={}, reason={}", freezeId, freezeReason);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("DLQ: Error in asset freeze handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "AssetFreezeEventsConsumer";
    }
}
