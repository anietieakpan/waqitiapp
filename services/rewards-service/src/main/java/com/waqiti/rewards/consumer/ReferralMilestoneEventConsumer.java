package com.waqiti.rewards.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.rewards.domain.ReferralMilestone;
import com.waqiti.rewards.service.ReferralMilestoneService;
import com.waqiti.rewards.service.ReferralNotificationService;
import com.waqiti.rewards.service.ReferralStatisticsService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer for referral milestone events
 * Checks and processes milestone achievements
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-09
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReferralMilestoneEventConsumer {

    private final ReferralMilestoneService milestoneService;
    private final ReferralStatisticsService statisticsService;
    private final ReferralNotificationService notificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private Counter processedCounter;
    private Counter achievementsCounter;
    private Counter failedCounter;
    private Timer processingTimer;

    @jakarta.annotation.PostConstruct
    public void initMetrics() {
        processedCounter = Counter.builder("referral.milestone.checked")
                .description("Milestone checks processed")
                .register(meterRegistry);

        achievementsCounter = Counter.builder("referral.milestone.achieved")
                .description("Milestones achieved")
                .register(meterRegistry);

        failedCounter = Counter.builder("referral.milestone.failed")
                .description("Milestone processing failures")
                .register(meterRegistry);

        processingTimer = Timer.builder("referral.milestone.processing.time")
                .description("Time to process milestone check")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "referral-milestone-check",
            groupId = "rewards-service-referral-milestone-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleMilestoneCheckEvent(
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

        String userId = null;
        String programId = null;

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            userId = (String) event.get("userId");
            programId = (String) event.get("programId");
            String eventType = (String) event.getOrDefault("eventType", "MILESTONE_CHECK");

            MDC.put("user_id", userId);
            MDC.put("program_id", programId);

            log.info("Processing milestone check: userId={}, programId={}", userId, programId);

            // Get user statistics
            var stats = statisticsService.getUserStatistics(UUID.fromString(userId), programId);

            int referralCount = stats.getTotalReferrals() != null ? stats.getTotalReferrals() : 0;
            int conversionCount = stats.getTotalConversions() != null ? stats.getTotalConversions() : 0;
            BigDecimal revenue = stats.getTotalRevenue() != null ? stats.getTotalRevenue() : BigDecimal.ZERO;

            // Check and process milestones
            List<ReferralMilestone> achieved = milestoneService.checkAndProcessMilestones(
                    UUID.fromString(userId),
                    programId,
                    referralCount,
                    conversionCount,
                    revenue
            );

            if (!achieved.isEmpty()) {
                log.info("User {} achieved {} milestones in program {}",
                        userId, achieved.size(), programId);

                for (ReferralMilestone milestone : achieved) {
                    // Send notification
                    notificationService.notifyMilestoneAchieved(
                            UUID.fromString(userId),
                            milestone.getMilestoneName(),
                            milestone.getRewardDescription()
                    );

                    achievementsCounter.increment();

                    auditService.auditEvent(
                            "REFERRAL_MILESTONE_ACHIEVED",
                            userId,
                            String.format("Milestone achieved: %s", milestone.getMilestoneName()),
                            Map.of(
                                    "milestone_id", milestone.getMilestoneId(),
                                    "milestone_name", milestone.getMilestoneName(),
                                    "program_id", programId,
                                    "referral_count", referralCount,
                                    "conversion_count", conversionCount,
                                    "reward_type", milestone.getRewardType().toString(),
                                    "correlation_id", correlationId
                            )
                    );
                }
            }

            acknowledgment.acknowledge();
            processedCounter.increment();

        } catch (Exception e) {
            log.error("Failed to process milestone check: userId={}, programId={}, error={}",
                    userId, programId, e.getMessage(), e);
            failedCounter.increment();

            acknowledgment.acknowledge();

        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }
}
