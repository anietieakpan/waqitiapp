package com.waqiti.rewards.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.rewards.event.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Service for publishing referral-related events to Kafka
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-09
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReferralEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    // Topic names
    private static final String TOPIC_REFERRAL_TRACKING = "referral-tracking";
    private static final String TOPIC_REFERRAL_REWARDS = "referral-rewards-events";
    private static final String TOPIC_REFERRAL_MILESTONE = "referral-milestone-check";
    private static final String TOPIC_REFERRAL_LEADERBOARD = "referral-leaderboard-update";

    private Counter publishedCounter;
    private Counter failedCounter;

    @jakarta.annotation.PostConstruct
    public void initMetrics() {
        publishedCounter = Counter.builder("referral.events.published")
                .description("Referral events published to Kafka")
                .register(meterRegistry);

        failedCounter = Counter.builder("referral.events.publish.failed")
                .description("Failed to publish referral events")
                .register(meterRegistry);
    }

    /**
     * Publish referral link created event
     */
    public void publishLinkCreated(ReferralLinkCreatedEvent event) {
        String correlationId = getCorrelationId();
        event.setCorrelationId(correlationId);

        publish(TOPIC_REFERRAL_TRACKING, event.getLinkId(), event, correlationId);

        log.info("Published REFERRAL_LINK_CREATED event: linkId={}, userId={}, code={}",
                event.getLinkId(), event.getUserId(), event.getReferralCode());
    }

    /**
     * Publish referral clicked event
     */
    public void publishLinkClicked(ReferralClickedEvent event) {
        String correlationId = getCorrelationId();
        event.setCorrelationId(correlationId);

        publish(TOPIC_REFERRAL_TRACKING, event.getReferralCode(), event, correlationId);

        log.debug("Published REFERRAL_LINK_CLICKED event: code={}, clickId={}, unique={}",
                event.getReferralCode(), event.getClickId(), event.getIsUnique());
    }

    /**
     * Publish referral conversion event
     */
    public void publishConversion(ReferralConversionEvent event) {
        String correlationId = getCorrelationId();
        event.setCorrelationId(correlationId);

        publish(TOPIC_REFERRAL_TRACKING, event.getReferralCode(), event, correlationId);

        // Also trigger leaderboard update
        publishLeaderboardUpdate(
                event.getReferrerId(),
                event.getProgramId(),
                "REFERRAL_SUCCESSFUL",
                correlationId
        );

        // Trigger milestone check
        publishMilestoneCheck(
                event.getReferrerId(),
                event.getProgramId(),
                correlationId
        );

        log.info("Published REFERRAL_CONVERSION event: referrer={}, referee={}, type={}",
                event.getReferrerId(), event.getRefereeId(), event.getConversionType());
    }

    /**
     * Publish referral reward issued event
     */
    public void publishRewardIssued(ReferralRewardIssuedEvent event) {
        String correlationId = getCorrelationId();
        event.setCorrelationId(correlationId);

        publish(TOPIC_REFERRAL_REWARDS, event.getRewardId(), event, correlationId);

        // Update leaderboard with reward
        publishLeaderboardRewardUpdate(
                event.getRecipientId(),
                event.getProgramId(),
                event.getPointsAmount(),
                event.getCashbackAmount(),
                correlationId
        );

        log.info("Published REFERRAL_REWARD_ISSUED event: rewardId={}, recipient={}, type={}, amount={}",
                event.getRewardId(), event.getRecipientId(), event.getRewardType(),
                event.getCashbackAmount() != null ? event.getCashbackAmount() : event.getPointsAmount());
    }

    /**
     * Publish milestone achieved event
     */
    public void publishMilestoneAchieved(ReferralMilestoneAchievedEvent event) {
        String correlationId = getCorrelationId();
        event.setCorrelationId(correlationId);

        publish(TOPIC_REFERRAL_MILESTONE, event.getAchievementId(), event, correlationId);

        log.info("Published REFERRAL_MILESTONE_ACHIEVED event: achievementId={}, userId={}, milestone={}",
                event.getAchievementId(), event.getUserId(), event.getMilestoneName());
    }

    /**
     * Publish leaderboard update event
     */
    public void publishLeaderboardUpdate(java.util.UUID userId, String programId,
                                         String eventType, String correlationId) {
        try {
            var event = new java.util.HashMap<String, Object>();
            event.put("eventId", java.util.UUID.randomUUID().toString());
            event.put("eventType", eventType);
            event.put("timestamp", java.time.Instant.now().toString());
            event.put("correlationId", correlationId);
            event.put("userId", userId.toString());
            event.put("programId", programId);

            String eventJson = objectMapper.writeValueAsString(event);
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    TOPIC_REFERRAL_LEADERBOARD,
                    userId.toString(),
                    eventJson
            );

            addHeaders(record, correlationId);

            sendAsync(record);

            log.debug("Published leaderboard update: userId={}, programId={}, type={}",
                    userId, programId, eventType);

        } catch (Exception e) {
            log.error("Failed to publish leaderboard update", e);
            failedCounter.increment();
        }
    }

    /**
     * Publish leaderboard reward update
     */
    private void publishLeaderboardRewardUpdate(java.util.UUID userId, String programId,
                                                Long points, java.math.BigDecimal cashback,
                                                String correlationId) {
        try {
            var event = new java.util.HashMap<String, Object>();
            event.put("eventId", java.util.UUID.randomUUID().toString());
            event.put("eventType", "REWARD_EARNED");
            event.put("timestamp", java.time.Instant.now().toString());
            event.put("correlationId", correlationId);
            event.put("userId", userId.toString());
            event.put("programId", programId);
            if (points != null) event.put("points", points);
            if (cashback != null) event.put("cashback", cashback.toString());

            String eventJson = objectMapper.writeValueAsString(event);
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    TOPIC_REFERRAL_LEADERBOARD,
                    userId.toString(),
                    eventJson
            );

            addHeaders(record, correlationId);
            sendAsync(record);

        } catch (Exception e) {
            log.error("Failed to publish leaderboard reward update", e);
            failedCounter.increment();
        }
    }

    /**
     * Publish milestone check trigger
     */
    public void publishMilestoneCheck(java.util.UUID userId, String programId,
                                      String correlationId) {
        try {
            var event = new java.util.HashMap<String, Object>();
            event.put("eventId", java.util.UUID.randomUUID().toString());
            event.put("eventType", "MILESTONE_CHECK");
            event.put("timestamp", java.time.Instant.now().toString());
            event.put("correlationId", correlationId);
            event.put("userId", userId.toString());
            event.put("programId", programId);

            String eventJson = objectMapper.writeValueAsString(event);
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    TOPIC_REFERRAL_MILESTONE,
                    userId.toString(),
                    eventJson
            );

            addHeaders(record, correlationId);
            sendAsync(record);

            log.debug("Published milestone check: userId={}, programId={}", userId, programId);

        } catch (Exception e) {
            log.error("Failed to publish milestone check", e);
            failedCounter.increment();
        }
    }

    /**
     * Generic publish method
     */
    private void publish(String topic, String key, Object event, String correlationId) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, eventJson);

            addHeaders(record, correlationId);

            sendAsync(record);

            publishedCounter.increment();

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event: topic={}, key={}", topic, key, e);
            failedCounter.increment();
            throw new RuntimeException("Failed to serialize event", e);
        }
    }

    /**
     * Add common headers to record
     */
    private void addHeaders(ProducerRecord<String, String> record, String correlationId) {
        record.headers().add(new RecordHeader("correlation_id",
                correlationId.getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("source",
                "rewards-service".getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("timestamp",
                String.valueOf(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Send asynchronously with callback
     */
    private void sendAsync(ProducerRecord<String, String> record) {
        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(record);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug("Event sent successfully: topic={}, partition={}, offset={}",
                        record.topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to send event: topic={}, key={}, error={}",
                        record.topic(), record.key(), ex.getMessage(), ex);
                failedCounter.increment();
            }
        });
    }

    /**
     * Get correlation ID from MDC or generate new one
     */
    private String getCorrelationId() {
        String correlationId = MDC.get("correlation_id");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = java.util.UUID.randomUUID().toString();
        }
        return correlationId;
    }
}
