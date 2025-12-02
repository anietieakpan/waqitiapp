package com.waqiti.rewards.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.rewards.service.ReferralRewardService;
import com.waqiti.rewards.service.ReferralProgramService;
import com.waqiti.rewards.enums.RewardType;
import com.waqiti.common.audit.AuditService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer for referral reward events
 * Updated to use new service layer architecture
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-09
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReferralRewardsEventsConsumer {

    private final ReferralRewardService rewardService;
    private final ReferralProgramService programService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private Counter processedCounter;
    private Counter failedCounter;
    private Timer processingTimer;

    @jakarta.annotation.PostConstruct
    public void initMetrics() {
        processedCounter = Counter.builder("referral.reward.events.processed")
                .description("Referral reward events processed")
                .register(meterRegistry);

        failedCounter = Counter.builder("referral.reward.events.failed")
                .description("Referral reward event failures")
                .register(meterRegistry);

        processingTimer = Timer.builder("referral.reward.events.processing.time")
                .description("Time to process referral reward event")
                .register(meterRegistry);
    }
    
    @KafkaListener(
            topics = {"referral-rewards-events", "referral-completed", "referral-bonus-earned"},
            groupId = "rewards-service-referral-rewards-group",
            containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000)
    )
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void handleReferralRewardsEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
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

        String linkId = null;
        UUID recipientId = null;
        String eventType = null;

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            linkId = (String) event.get("linkId");
            String programId = (String) event.get("programId");
            recipientId = UUID.fromString((String) event.get("recipientId"));
            String recipientType = (String) event.get("recipientType");
            eventType = (String) event.get("eventType");
            String rewardTypeStr = (String) event.get("rewardType");
            RewardType rewardType = RewardType.valueOf(rewardTypeStr);

            Long pointsAmount = event.get("pointsAmount") != null
                    ? Long.parseLong(event.get("pointsAmount").toString())
                    : null;
            BigDecimal cashbackAmount = event.get("cashbackAmount") != null
                    ? new BigDecimal(event.get("cashbackAmount").toString())
                    : null;

            MDC.put("recipient_id", recipientId.toString());
            MDC.put("program_id", programId);
            MDC.put("event_type", eventType);

            log.info("Processing referral reward event: linkId={}, programId={}, recipient={}, type={}, rewardType={}",
                    linkId, programId, recipientId, recipientType, rewardType);

            // Create reward using service layer
            var reward = rewardService.createReward(
                    linkId,
                    programId,
                    recipientId,
                    recipientType,
                    rewardType,
                    pointsAmount,
                    cashbackAmount
            );

            log.info("Referral reward created: rewardId={}, status={}, requiresApproval={}",
                    reward.getRewardId(), reward.getStatus(), reward.getRequiresApproval());

            auditService.auditFinancialEvent(
                    "REFERRAL_REWARD_CREATED",
                    recipientId.toString(),
                    String.format("Referral reward created: %s", reward.getRewardId()),
                    Map.of(
                            "reward_id", reward.getRewardId(),
                            "link_id", linkId,
                            "program_id", programId,
                            "recipient_type", recipientType,
                            "reward_type", rewardType.toString(),
                            "points_amount", pointsAmount != null ? pointsAmount.toString() : "0",
                            "cashback_amount", cashbackAmount != null ? cashbackAmount.toString() : "0",
                            "status", reward.getStatus().toString(),
                            "correlation_id", correlationId
                    )
            );

            acknowledgment.acknowledge();
            processedCounter.increment();

        } catch (Exception e) {
            log.error("Referral reward event processing failed: linkId={}, recipientId={}, eventType={}, error={}",
                    linkId, recipientId, eventType, e.getMessage(), e);
            failedCounter.increment();
            throw new RuntimeException("Referral reward event processing failed", e);

        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }
}