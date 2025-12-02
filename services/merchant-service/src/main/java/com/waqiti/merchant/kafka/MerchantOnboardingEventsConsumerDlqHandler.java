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
 * DLQ Handler for MerchantOnboardingEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class MerchantOnboardingEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public MerchantOnboardingEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("MerchantOnboardingEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.MerchantOnboardingEventsConsumer.dlq:MerchantOnboardingEventsConsumer.dlq}",
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
            log.warn("DLQ: Merchant onboarding recovery");
            String merchantId = headers.getOrDefault("merchantId", "").toString();
            String onboardingStep = headers.getOrDefault("onboardingStep", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            if (failureReason.contains("KYB") || failureReason.contains("verification")) {
                log.error("DLQ: Merchant KYB failed: merchantId={}", merchantId);
                return DlqProcessingResult.RETRY;
            }
            if (failureReason.contains("background check") || failureReason.contains("screening")) {
                log.warn("DLQ: Merchant screening failed: merchantId={}", merchantId);
                return DlqProcessingResult.RETRY;
            }
            if (failureReason.contains("duplicate")) return DlqProcessingResult.DISCARDED;
            if (failureReason.contains("timeout")) return DlqProcessingResult.RETRY;
            return DlqProcessingResult.RETRY;

        } catch (Exception e) {
            log.error("Error handling DLQ event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "MerchantOnboardingEventsConsumer";
    }
}
