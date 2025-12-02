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
 * DLQ Handler for Payment3DSAuthenticationEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class Payment3DSAuthenticationEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public Payment3DSAuthenticationEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("Payment3DSAuthenticationEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.Payment3DSAuthenticationEventsConsumer.dlq:Payment3DSAuthenticationEventsConsumer.dlq}",
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
            log.warn("DLQ: 3DS authentication recovery");
            String authId = headers.getOrDefault("authId", "").toString();
            String paymentId = headers.getOrDefault("paymentId", "").toString();
            String authStatus = headers.getOrDefault("authStatus", "").toString();
            String cardIssuer = headers.getOrDefault("cardIssuer", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: Authentication success but callback failed (CRITICAL)
            if (authStatus.contains("SUCCESS") && failureReason.contains("callback")) {
                log.error("DLQ: 3DS success but callback failed: paymentId={}", paymentId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 2: Authentication timeout
            if (authStatus.contains("TIMEOUT") || failureReason.contains("timeout")) {
                log.warn("DLQ: 3DS timeout: authId={}", authId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 3: Issuer unavailable
            if (failureReason.contains("issuer") || failureReason.contains("unavailable")) {
                log.warn("DLQ: 3DS issuer unavailable: cardIssuer={}", cardIssuer);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 4: Authentication failed (user declined)
            if (authStatus.contains("FAILED") || authStatus.contains("DECLINED")) {
                log.info("DLQ: 3DS declined by user (non-critical): paymentId={}", paymentId);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 5: Duplicate auth event
            if (failureReason.contains("duplicate")) {
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 6: State update failure
            if (failureReason.contains("state")) {
                log.warn("DLQ: 3DS state update failed: authId={}", authId);
                return DlqProcessingResult.RETRY;
            }

            log.warn("DLQ: 3DS authentication failed: authId={}, status={}", authId, authStatus);
            return DlqProcessingResult.RETRY;

        } catch (Exception e) {
            log.error("DLQ: Error in 3DS authentication handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "Payment3DSAuthenticationEventsConsumer";
    }
}
