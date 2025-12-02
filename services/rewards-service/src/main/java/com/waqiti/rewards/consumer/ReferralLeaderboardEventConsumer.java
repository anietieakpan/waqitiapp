package com.waqiti.rewards.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.rewards.service.ReferralLeaderboardService;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer for referral leaderboard events
 * Updates leaderboard entries based on referral activity
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-09
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReferralLeaderboardEventConsumer {

    private final ReferralLeaderboardService leaderboardService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private Counter processedCounter;
    private Counter failedCounter;
    private Timer processingTimer;

    @jakarta.annotation.PostConstruct
    public void initMetrics() {
        processedCounter = Counter.builder("referral.leaderboard.updated")
                .description("Leaderboard updates processed")
                .register(meterRegistry);

        failedCounter = Counter.builder("referral.leaderboard.failed")
                .description("Leaderboard update failures")
                .register(meterRegistry);

        processingTimer = Timer.builder("referral.leaderboard.processing.time")
                .description("Time to process leaderboard update")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "referral-leaderboard-update",
            groupId = "rewards-service-referral-leaderboard-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleLeaderboardUpdateEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_PARTITION) long offset,
            @Header(value = "correlation_id", required = false) byte[] correlationIdBytes,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = correlationIdBytes != null
                ? new String(correlationIdBytes, StandardCharsets.UTF_8)
                : UUID.randomUUID().toString();

        MDC.put("correlation_id", correlationId);
        MDC.put("topic", topic);

        String userId = null;
        String programId = null;

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            userId = (String) event.get("userId");
            programId = (String) event.get("programId");
            String eventType = (String) event.get("eventType");
            String periodType = (String) event.getOrDefault("periodType", "WEEKLY");

            MDC.put("user_id", userId);
            MDC.put("program_id", programId);
            MDC.put("event_type", eventType);

            log.debug("Processing leaderboard update: userId={}, programId={}, eventType={}",
                    userId, programId, eventType);

            LocalDate today = LocalDate.now();
            LocalDate periodStart = calculatePeriodStart(periodType, today);
            LocalDate periodEnd = calculatePeriodEnd(periodType, today);

            switch (eventType) {
                case "REFERRAL_CREATED":
                case "REFERRAL_SUCCESSFUL":
                    boolean successful = "REFERRAL_SUCCESSFUL".equals(eventType);
                    leaderboardService.recordReferral(
                            UUID.fromString(userId),
                            programId,
                            periodType,
                            periodStart,
                            periodEnd,
                            successful
                    );
                    break;

                case "REWARD_EARNED":
                    Long points = event.get("points") != null
                            ? Long.parseLong(event.get("points").toString())
                            : null;
                    BigDecimal cashback = event.get("cashback") != null
                            ? new BigDecimal(event.get("cashback").toString())
                            : null;

                    leaderboardService.recordReward(
                            UUID.fromString(userId),
                            programId,
                            periodType,
                            periodStart,
                            periodEnd,
                            points,
                            cashback
                    );
                    break;

                default:
                    log.warn("Unknown leaderboard event type: {}", eventType);
            }

            acknowledgment.acknowledge();
            processedCounter.increment();

        } catch (Exception e) {
            log.error("Failed to process leaderboard update: userId={}, programId={}, error={}",
                    userId, programId, e.getMessage(), e);
            failedCounter.increment();

            acknowledgment.acknowledge();

        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }

    private LocalDate calculatePeriodStart(String periodType, LocalDate date) {
        switch (periodType.toUpperCase()) {
            case "DAILY":
                return date;
            case "WEEKLY":
                return date.with(java.time.DayOfWeek.MONDAY);
            case "MONTHLY":
                return date.withDayOfMonth(1);
            case "ALL_TIME":
                return LocalDate.of(2020, 1, 1);
            default:
                return date;
        }
    }

    private LocalDate calculatePeriodEnd(String periodType, LocalDate date) {
        switch (periodType.toUpperCase()) {
            case "DAILY":
                return date;
            case "WEEKLY":
                return date.with(java.time.DayOfWeek.SUNDAY);
            case "MONTHLY":
                return date.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
            case "ALL_TIME":
                return LocalDate.now();
            default:
                return date;
        }
    }
}
