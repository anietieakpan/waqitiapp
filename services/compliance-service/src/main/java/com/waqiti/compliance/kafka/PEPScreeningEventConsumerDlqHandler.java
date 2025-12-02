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
 * DLQ Handler for PEPScreeningEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class PEPScreeningEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public PEPScreeningEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("PEPScreeningEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.PEPScreeningEventConsumer.dlq:PEPScreeningEventConsumer.dlq}",
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
            log.error("DLQ: PEP screening recovery (COMPLIANCE CRITICAL)");
            String screeningId = headers.getOrDefault("screeningId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String matchStatus = headers.getOrDefault("matchStatus", "").toString();
            String pepType = headers.getOrDefault("pepType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: PEP match but account not flagged (CRITICAL)
            if (matchStatus.contains("MATCH") && failureReason.contains("account flag")) {
                log.error("DLQ: PEP match but account not flagged: userId={}, type={}", userId, pepType);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 2: Enhanced due diligence trigger failure
            if (matchStatus.contains("MATCH") && failureReason.contains("EDD")) {
                log.error("DLQ: EDD trigger failed for PEP: userId={}", userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 3: Compliance officer notification failure
            if (failureReason.contains("notification") || failureReason.contains("alert")) {
                log.error("DLQ: PEP alert notification failed: userId={}", userId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 4: Screening service unavailable
            if (failureReason.contains("service") || failureReason.contains("timeout")) {
                log.warn("DLQ: PEP screening service unavailable: userId={}", userId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 5: Duplicate screening
            if (failureReason.contains("duplicate")) {
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 6: Record keeping failure
            if (failureReason.contains("record") || failureReason.contains("audit")) {
                log.error("DLQ: PEP screening record failed: screeningId={}", screeningId);
                return DlqProcessingResult.RETRY;
            }

            log.error("DLQ: PEP screening failed: screeningId={}, userId={}", screeningId, userId);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("DLQ: Error in PEP screening handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "PEPScreeningEventConsumer";
    }
}
