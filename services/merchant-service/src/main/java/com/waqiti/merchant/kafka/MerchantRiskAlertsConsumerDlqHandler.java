package com.waqiti.merchant.kafka;

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
 * DLQ Handler for MerchantRiskAlertsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class MerchantRiskAlertsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public MerchantRiskAlertsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("MerchantRiskAlertsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.MerchantRiskAlertsConsumer.dlq:MerchantRiskAlertsConsumer.dlq}",
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
            log.warn("DLQ: Merchant risk alert recovery");
            String alertId = headers.getOrDefault("alertId", "").toString();
            String merchantId = headers.getOrDefault("merchantId", "").toString();
            String riskType = headers.getOrDefault("riskType", "").toString();
            String severity = headers.getOrDefault("severity", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: High severity - merchant freeze/suspension
            if (severity.contains("HIGH") || severity.contains("CRITICAL")) {
                if (failureReason.contains("suspend") || failureReason.contains("freeze")) {
                    log.error("DLQ: Failed to suspend high-risk merchant: merchantId={}", merchantId);
                    return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
                }
            }

            // Strategy 2: Chargeback rate alert
            if (riskType.contains("chargeback") || riskType.contains("dispute")) {
                log.warn("DLQ: Merchant chargeback alert failed: merchantId={}", merchantId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 3: Fraud pattern detected
            if (riskType.contains("fraud") || riskType.contains("suspicious")) {
                log.error("DLQ: Merchant fraud alert failed: merchantId={}", merchantId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 4: Notification failure
            if (failureReason.contains("notification")) {
                return DlqProcessingResult.RETRY;
            }

            // Strategy 5: Duplicate
            if (failureReason.contains("duplicate")) {
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 6: Transient
            if (failureReason.contains("timeout")) {
                return DlqProcessingResult.RETRY;
            }

            log.warn("DLQ: Merchant risk alert failed: alertId={}, merchantId={}", alertId, merchantId);
            return DlqProcessingResult.RETRY;

        } catch (Exception e) {
            log.error("DLQ: Error in merchant risk alert handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "MerchantRiskAlertsConsumer";
    }
}
