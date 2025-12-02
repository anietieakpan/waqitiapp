package com.waqiti.rewards.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.rewards.domain.ReferralLink;
import com.waqiti.rewards.service.ReferralLinkService;
import com.waqiti.rewards.service.ReferralValidationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer for user signup events with referral codes
 * Processes signups that used a referral link
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-09
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReferralSignupEventConsumer {

    private final ReferralLinkService linkService;
    private final ReferralValidationService validationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private Counter processedCounter;
    private Counter failedCounter;
    private Timer processingTimer;

    @jakarta.annotation.PostConstruct
    public void initMetrics() {
        processedCounter = Counter.builder("referral.signup.processed")
                .description("Referral signups processed")
                .register(meterRegistry);

        failedCounter = Counter.builder("referral.signup.failed")
                .description("Referral signup processing failures")
                .register(meterRegistry);

        processingTimer = Timer.builder("referral.signup.processing.time")
                .description("Time to process referral signup")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "user-signup",
            groupId = "rewards-service-referral-signup-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleSignupEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_MESSAGE_KEY, required = false) String messageKey,
            @Header(value = "correlation_id", required = false) byte[] correlationIdBytes,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = correlationIdBytes != null
                ? new String(correlationIdBytes, StandardCharsets.UTF_8)
                : UUID.randomUUID().toString();

        MDC.put("correlation_id", correlationId);
        MDC.put("topic", topic);
        MDC.put("partition", String.valueOf(partition));
        MDC.put("offset", String.valueOf(offset));

        String userId = null;
        String referralCode = null;

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            userId = (String) event.get("userId");
            referralCode = (String) event.get("referralCode");
            String ipAddress = (String) event.get("ipAddress");
            String userAgent = (String) event.get("userAgent");

            MDC.put("user_id", userId);
            MDC.put("referral_code", referralCode != null ? referralCode : "none");

            log.info("Processing signup event: userId={}, referralCode={}", userId, referralCode);

            // Only process if referral code is present
            if (referralCode != null && !referralCode.isBlank()) {
                processReferralSignup(userId, referralCode, ipAddress, userAgent, correlationId);
            } else {
                log.debug("Signup without referral code, skipping referral processing");
            }

            acknowledgment.acknowledge();
            processedCounter.increment();

        } catch (Exception e) {
            log.error("Failed to process signup event: userId={}, referralCode={}, error={}",
                    userId, referralCode, e.getMessage(), e);
            failedCounter.increment();

            // Acknowledge to prevent poison pill (errors will be in DLQ via RetryableTopic)
            acknowledgment.acknowledge();

        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }

    private void processReferralSignup(String userId, String referralCode,
                                       String ipAddress, String userAgent,
                                       String correlationId) {
        try {
            // Get the referral link
            ReferralLink link = linkService.getLinkByCode(referralCode);

            UUID referrerId = link.getUserId();
            UUID refereeId = UUID.fromString(userId);

            // Validate the referral is eligible
            boolean isValid = validationService.validateReferralEligibility(
                    link.getProgram().getProgramId(),
                    referrerId,
                    refereeId,
                    ipAddress
            );

            if (!isValid) {
                log.warn("Referral validation failed: referrer={}, referee={}, code={}",
                        referrerId, refereeId, referralCode);
                return;
            }

            // Record the conversion (signup)
            linkService.recordConversion(link.getLinkId(), refereeId);

            log.info("Referral signup processed successfully: referrer={}, referee={}, code={}",
                    referrerId, refereeId, referralCode);

            auditService.auditEvent(
                    "REFERRAL_SIGNUP_PROCESSED",
                    userId,
                    String.format("User signed up via referral code: %s", referralCode),
                    Map.of(
                            "referral_code", referralCode,
                            "referrer_id", referrerId.toString(),
                            "link_id", link.getLinkId(),
                            "program_id", link.getProgram().getProgramId(),
                            "correlation_id", correlationId
                    )
            );

        } catch (Exception e) {
            log.error("Error processing referral signup: userId={}, code={}, error={}",
                    userId, referralCode, e.getMessage(), e);
            throw e;
        }
    }
}
