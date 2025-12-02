package com.waqiti.analytics.events.consumers;

import com.waqiti.analytics.domain.*;
import com.waqiti.analytics.repository.*;
import com.waqiti.analytics.service.*;
import com.waqiti.common.events.savings.SavingsGoalMilestoneEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class SavingsGoalMilestoneEventsConsumer {

    private final UserSavingsAnalyticsRepository userSavingsAnalyticsRepository;
    private final GoalMilestoneRepository goalMilestoneRepository;
    private final UserEngagementMetricsRepository userEngagementMetricsRepository;
    private final SavingsPatternRepository savingsPatternRepository;
    private final NotificationServiceClient notificationServiceClient;
    private final RewardsServiceClient rewardsServiceClient;
    private final EventProcessingTrackingService eventProcessingTrackingService;
    private final MeterRegistry meterRegistry;

    private Counter eventsProcessedCounter;
    private Counter eventsFailedCounter;
    private Counter milestoneTypesCounter;
    private Counter notificationsSentCounter;
    private Counter rewardsAwardedCounter;
    private Timer eventProcessingTimer;

    public SavingsGoalMilestoneEventsConsumer(
            UserSavingsAnalyticsRepository userSavingsAnalyticsRepository,
            GoalMilestoneRepository goalMilestoneRepository,
            UserEngagementMetricsRepository userEngagementMetricsRepository,
            SavingsPatternRepository savingsPatternRepository,
            NotificationServiceClient notificationServiceClient,
            RewardsServiceClient rewardsServiceClient,
            EventProcessingTrackingService eventProcessingTrackingService,
            MeterRegistry meterRegistry) {
        
        this.userSavingsAnalyticsRepository = userSavingsAnalyticsRepository;
        this.goalMilestoneRepository = goalMilestoneRepository;
        this.userEngagementMetricsRepository = userEngagementMetricsRepository;
        this.savingsPatternRepository = savingsPatternRepository;
        this.notificationServiceClient = notificationServiceClient;
        this.rewardsServiceClient = rewardsServiceClient;
        this.eventProcessingTrackingService = eventProcessingTrackingService;
        this.meterRegistry = meterRegistry;

        initializeMetrics();
    }

    private void initializeMetrics() {
        this.eventsProcessedCounter = Counter.builder("savings_goal_milestone_events_processed_total")
                .description("Total number of savings goal milestone events processed")
                .tag("consumer", "savings-goal-milestone-consumer")
                .register(meterRegistry);

        this.eventsFailedCounter = Counter.builder("savings_goal_milestone_events_failed_total")
                .description("Total number of savings goal milestone events failed")
                .tag("consumer", "savings-goal-milestone-consumer")
                .register(meterRegistry);

        this.milestoneTypesCounter = Counter.builder("savings_goal_milestone_types_total")
                .description("Total number of milestone events by type")
                .tag("consumer", "savings-goal-milestone-consumer")
                .register(meterRegistry);

        this.notificationsSentCounter = Counter.builder("savings_goal_milestone_notifications_sent_total")
                .description("Total notifications sent for milestone events")
                .tag("consumer", "savings-goal-milestone-consumer")
                .register(meterRegistry);

        this.rewardsAwardedCounter = Counter.builder("savings_goal_milestone_rewards_awarded_total")
                .description("Total rewards awarded for milestone achievements")
                .tag("consumer", "savings-goal-milestone-consumer")
                .register(meterRegistry);

        this.eventProcessingTimer = Timer.builder("savings_goal_milestone_event_processing_duration")
                .description("Time taken to process savings goal milestone events")
                .tag("consumer", "savings-goal-milestone-consumer")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "${kafka.topics.savings-goal-milestone-events:savings-goal-milestone-events}",
            groupId = "${kafka.consumer.group-id:analytics-savings-milestone-consumer-group}",
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${kafka.consumer.concurrency:5}"
    )
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
            include = {ServiceIntegrationException.class, Exception.class},
            dltTopicSuffix = "-dlt",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            autoCreateTopics = "true"
    )
    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED, timeout = 30)
    public void handleSavingsGoalMilestoneEvent(
            @Payload SavingsGoalMilestoneEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = event.getCorrelationId();

        try {
            log.info("Processing savings goal milestone event: eventId={}, userId={}, goalId={}, milestoneType={}, " +
                    "topic={}, partition={}, offset={}, correlationId={}",
                    event.getEventId(), event.getUserId(), event.getGoalId(), event.getMilestoneType(),
                    topic, partition, offset, correlationId);

            if (eventProcessingTrackingService.isEventAlreadyProcessed(event.getEventId(), "SAVINGS_MILESTONE")) {
                log.warn("Duplicate savings goal milestone event detected: eventId={}, correlationId={}. Skipping processing.",
                        event.getEventId(), correlationId);
                acknowledgment.acknowledge();
                return;
            }

            validateEvent(event);

            processMilestoneAchievement(event, correlationId);

            updateUserSavingsAnalytics(event, correlationId);

            updateEngagementMetrics(event, correlationId);

            analyzeSavingsPatterns(event, correlationId);

            sendMilestoneNotification(event, correlationId);

            awardMilestoneRewards(event, correlationId);

            eventProcessingTrackingService.markEventAsProcessed(
                    event.getEventId(),
                    "SAVINGS_MILESTONE",
                    "analytics-service",
                    correlationId
            );

            eventsProcessedCounter.increment();
            Counter.builder("savings_goal_milestone_types_processed")
                    .tag("milestone_type", event.getMilestoneType())
                    .register(meterRegistry)
                    .increment();

            acknowledgment.acknowledge();

            log.info("Successfully processed savings goal milestone event: eventId={}, userId={}, correlationId={}",
                    event.getEventId(), event.getUserId(), correlationId);

        } catch (Exception e) {
            eventsFailedCounter.increment();
            log.error("Failed to process savings goal milestone event: eventId={}, userId={}, correlationId={}, error={}",
                    event.getEventId(), event.getUserId(), correlationId, e.getMessage(), e);
            throw new RuntimeException("Savings goal milestone event processing failed", e);
        } finally {
            sample.stop(eventProcessingTimer);
        }
    }

    private void validateEvent(SavingsGoalMilestoneEvent event) {
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID is required");
        }

        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }

        if (event.getGoalId() == null || event.getGoalId().trim().isEmpty()) {
            throw new IllegalArgumentException("Goal ID is required");
        }

        if (event.getMilestoneType() == null || event.getMilestoneType().trim().isEmpty()) {
            throw new IllegalArgumentException("Milestone type is required");
        }

        if (event.getCurrentAmount() == null || event.getCurrentAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Current amount must be non-negative");
        }

        if (event.getTargetAmount() == null || event.getTargetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Target amount must be positive");
        }
    }

    @CircuitBreaker(name = "savingsAnalytics", fallbackMethod = "processMilestoneAchievementFallback")
    @Retry(name = "savingsAnalytics")
    @TimeLimiter(name = "savingsAnalytics")
    private void processMilestoneAchievement(SavingsGoalMilestoneEvent event, String correlationId) {
        log.debug("Processing milestone achievement for goal: goalId={}, milestoneType={}, correlationId={}",
                event.getGoalId(), event.getMilestoneType(), correlationId);

        GoalMilestone milestone = GoalMilestone.builder()
                .goalId(event.getGoalId())
                .userId(event.getUserId())
                .milestoneType(event.getMilestoneType())
                .milestonePercentage(event.getMilestonePercentage())
                .achievedAmount(event.getCurrentAmount())
                .targetAmount(event.getTargetAmount())
                .currency(event.getCurrency())
                .achievedAt(event.getAchievedAt() != null ? event.getAchievedAt() : LocalDateTime.now())
                .goalName(event.getGoalName())
                .goalCategory(event.getGoalCategory())
                .isFirstMilestone(event.getIsFirstMilestone() != null && event.getIsFirstMilestone())
                .daysToAchieve(calculateDaysToAchieve(event))
                .contributionsCount(event.getContributionsCount())
                .averageContribution(calculateAverageContribution(event))
                .correlationId(correlationId)
                .build();

        goalMilestoneRepository.save(milestone);

        milestoneTypesCounter.increment();

        log.debug("Milestone achievement recorded: milestoneId={}, goalId={}, milestoneType={}, correlationId={}",
                milestone.getId(), event.getGoalId(), event.getMilestoneType(), correlationId);
    }

    private void processMilestoneAchievementFallback(SavingsGoalMilestoneEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for milestone achievement processing: goalId={}, correlationId={}, error={}",
                event.getGoalId(), correlationId, e.getMessage());
    }

    @CircuitBreaker(name = "savingsAnalytics", fallbackMethod = "updateUserSavingsAnalyticsFallback")
    @Retry(name = "savingsAnalytics")
    @TimeLimiter(name = "savingsAnalytics")
    private void updateUserSavingsAnalytics(SavingsGoalMilestoneEvent event, String correlationId) {
        log.debug("Updating user savings analytics: userId={}, goalId={}, correlationId={}",
                event.getUserId(), event.getGoalId(), correlationId);

        Optional<UserSavingsAnalytics> analyticsOpt = userSavingsAnalyticsRepository.findByUserId(event.getUserId());
        
        UserSavingsAnalytics analytics;
        if (analyticsOpt.isPresent()) {
            analytics = analyticsOpt.get();
        } else {
            analytics = UserSavingsAnalytics.builder()
                    .userId(event.getUserId())
                    .totalGoalsCreated(0)
                    .activeGoalsCount(0)
                    .completedGoalsCount(0)
                    .totalSavedAmount(BigDecimal.ZERO)
                    .totalMilestonesAchieved(0)
                    .averageCompletionRate(BigDecimal.ZERO)
                    .averageSavingsVelocity(BigDecimal.ZERO)
                    .longestSavingsStreak(0)
                    .currentSavingsStreak(0)
                    .lastContributionDate(null)
                    .firstGoalCreatedAt(null)
                    .build();
        }

        analytics.setTotalMilestonesAchieved(analytics.getTotalMilestonesAchieved() + 1);

        if (event.getCurrentAmount() != null) {
            analytics.setTotalSavedAmount(
                    analytics.getTotalSavedAmount().add(event.getCurrentAmount())
            );
        }

        if ("GOAL_COMPLETED".equals(event.getMilestoneType())) {
            analytics.setCompletedGoalsCount(analytics.getCompletedGoalsCount() + 1);
            analytics.setActiveGoalsCount(Math.max(0, analytics.getActiveGoalsCount() - 1));
        }

        BigDecimal completionRate = calculateCompletionRate(
                analytics.getCompletedGoalsCount(),
                analytics.getTotalGoalsCreated()
        );
        analytics.setAverageCompletionRate(completionRate);

        if (event.getContributionDate() != null) {
            analytics.setLastContributionDate(event.getContributionDate());
            updateSavingsStreak(analytics, event.getContributionDate());
        }

        BigDecimal savingsVelocity = calculateSavingsVelocity(event);
        if (savingsVelocity.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal currentAverage = analytics.getAverageSavingsVelocity();
            BigDecimal newAverage = currentAverage
                    .multiply(BigDecimal.valueOf(analytics.getTotalMilestonesAchieved() - 1))
                    .add(savingsVelocity)
                    .divide(BigDecimal.valueOf(analytics.getTotalMilestonesAchieved()), 2, RoundingMode.HALF_UP);
            analytics.setAverageSavingsVelocity(newAverage);
        }

        analytics.setLastUpdatedAt(LocalDateTime.now());

        userSavingsAnalyticsRepository.save(analytics);

        log.debug("User savings analytics updated: userId={}, totalMilestones={}, completedGoals={}, correlationId={}",
                event.getUserId(), analytics.getTotalMilestonesAchieved(), analytics.getCompletedGoalsCount(), correlationId);
    }

    private void updateUserSavingsAnalyticsFallback(SavingsGoalMilestoneEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for user savings analytics update: userId={}, correlationId={}, error={}",
                event.getUserId(), correlationId, e.getMessage());
    }

    @CircuitBreaker(name = "savingsAnalytics", fallbackMethod = "updateEngagementMetricsFallback")
    @Retry(name = "savingsAnalytics")
    @TimeLimiter(name = "savingsAnalytics")
    private void updateEngagementMetrics(SavingsGoalMilestoneEvent event, String correlationId) {
        log.debug("Updating engagement metrics: userId={}, milestoneType={}, correlationId={}",
                event.getUserId(), event.getMilestoneType(), correlationId);

        Optional<UserEngagementMetrics> metricsOpt = userEngagementMetricsRepository.findByUserId(event.getUserId());
        
        UserEngagementMetrics metrics;
        if (metricsOpt.isPresent()) {
            metrics = metricsOpt.get();
        } else {
            metrics = UserEngagementMetrics.builder()
                    .userId(event.getUserId())
                    .savingsEngagementScore(0)
                    .totalSavingsActions(0)
                    .savingsMilestonesAchieved(0)
                    .lastSavingsActivity(null)
                    .build();
        }

        metrics.setSavingsMilestonesAchieved(metrics.getSavingsMilestonesAchieved() + 1);
        metrics.setTotalSavingsActions(metrics.getTotalSavingsActions() + 1);
        metrics.setLastSavingsActivity(LocalDateTime.now());

        int engagementBoost = calculateEngagementBoost(event.getMilestoneType());
        metrics.setSavingsEngagementScore(metrics.getSavingsEngagementScore() + engagementBoost);

        userEngagementMetricsRepository.save(metrics);

        log.debug("Engagement metrics updated: userId={}, engagementScore={}, milestonesAchieved={}, correlationId={}",
                event.getUserId(), metrics.getSavingsEngagementScore(), metrics.getSavingsMilestonesAchieved(), correlationId);
    }

    private void updateEngagementMetricsFallback(SavingsGoalMilestoneEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for engagement metrics update: userId={}, correlationId={}, error={}",
                event.getUserId(), correlationId, e.getMessage());
    }

    @CircuitBreaker(name = "savingsAnalytics", fallbackMethod = "analyzeSavingsPatternsFallback")
    @Retry(name = "savingsAnalytics")
    @TimeLimiter(name = "savingsAnalytics")
    private void analyzeSavingsPatterns(SavingsGoalMilestoneEvent event, String correlationId) {
        log.debug("Analyzing savings patterns: userId={}, goalId={}, correlationId={}",
                event.getUserId(), event.getGoalId(), correlationId);

        Optional<SavingsPattern> patternOpt = savingsPatternRepository.findByUserIdAndGoalId(
                event.getUserId(), event.getGoalId());
        
        SavingsPattern pattern;
        if (patternOpt.isPresent()) {
            pattern = patternOpt.get();
        } else {
            pattern = SavingsPattern.builder()
                    .userId(event.getUserId())
                    .goalId(event.getGoalId())
                    .totalContributions(0)
                    .averageContributionAmount(BigDecimal.ZERO)
                    .contributionFrequency("UNKNOWN")
                    .peakContributionDay(null)
                    .consistencyScore(BigDecimal.ZERO)
                    .build();
        }

        if (event.getContributionsCount() != null) {
            pattern.setTotalContributions(event.getContributionsCount());
        }

        if (event.getLastContributionAmount() != null && event.getContributionsCount() != null && event.getContributionsCount() > 0) {
            BigDecimal avgContribution = event.getCurrentAmount()
                    .divide(BigDecimal.valueOf(event.getContributionsCount()), 2, RoundingMode.HALF_UP);
            pattern.setAverageContributionAmount(avgContribution);
        }

        if (event.getContributionDate() != null) {
            pattern.setPeakContributionDay(event.getContributionDate().getDayOfWeek().toString());
        }

        BigDecimal consistencyScore = calculateConsistencyScore(event);
        pattern.setConsistencyScore(consistencyScore);

        String frequency = determineContributionFrequency(event);
        pattern.setContributionFrequency(frequency);

        pattern.setLastAnalyzedAt(LocalDateTime.now());

        savingsPatternRepository.save(pattern);

        log.debug("Savings patterns analyzed: userId={}, goalId={}, frequency={}, consistencyScore={}, correlationId={}",
                event.getUserId(), event.getGoalId(), frequency, consistencyScore, correlationId);
    }

    private void analyzeSavingsPatternsFallback(SavingsGoalMilestoneEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for savings patterns analysis: userId={}, correlationId={}, error={}",
                event.getUserId(), correlationId, e.getMessage());
    }

    @CircuitBreaker(name = "notificationService", fallbackMethod = "sendMilestoneNotificationFallback")
    @Retry(name = "notificationService")
    @TimeLimiter(name = "notificationService")
    private void sendMilestoneNotification(SavingsGoalMilestoneEvent event, String correlationId) {
        log.debug("Sending milestone notification: userId={}, milestoneType={}, correlationId={}",
                event.getUserId(), event.getMilestoneType(), correlationId);

        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("userId", event.getUserId());
        notificationData.put("goalName", event.getGoalName());
        notificationData.put("milestoneType", event.getMilestoneType());
        notificationData.put("milestonePercentage", event.getMilestonePercentage());
        notificationData.put("currentAmount", event.getCurrentAmount());
        notificationData.put("targetAmount", event.getTargetAmount());
        notificationData.put("currency", event.getCurrency());
        notificationData.put("goalCategory", event.getGoalCategory());
        notificationData.put("isFirstMilestone", event.getIsFirstMilestone());
        notificationData.put("correlationId", correlationId);

        String notificationType = determineNotificationType(event.getMilestoneType());
        notificationData.put("notificationType", notificationType);

        notificationServiceClient.sendNotification(notificationData, correlationId);

        notificationsSentCounter.increment();

        log.debug("Milestone notification sent: userId={}, milestoneType={}, notificationType={}, correlationId={}",
                event.getUserId(), event.getMilestoneType(), notificationType, correlationId);
    }

    private void sendMilestoneNotificationFallback(SavingsGoalMilestoneEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for milestone notification: userId={}, correlationId={}, error={}",
                event.getUserId(), correlationId, e.getMessage());
    }

    @CircuitBreaker(name = "rewardsService", fallbackMethod = "awardMilestoneRewardsFallback")
    @Retry(name = "rewardsService")
    @TimeLimiter(name = "rewardsService")
    private void awardMilestoneRewards(SavingsGoalMilestoneEvent event, String correlationId) {
        log.debug("Awarding milestone rewards: userId={}, milestoneType={}, correlationId={}",
                event.getUserId(), event.getMilestoneType(), correlationId);

        BigDecimal rewardAmount = calculateMilestoneReward(event);

        if (rewardAmount.compareTo(BigDecimal.ZERO) > 0) {
            Map<String, Object> rewardData = new HashMap<>();
            rewardData.put("userId", event.getUserId());
            rewardData.put("rewardType", "SAVINGS_MILESTONE");
            rewardData.put("rewardAmount", rewardAmount);
            rewardData.put("currency", event.getCurrency());
            rewardData.put("milestoneType", event.getMilestoneType());
            rewardData.put("goalId", event.getGoalId());
            rewardData.put("goalName", event.getGoalName());
            rewardData.put("milestonePercentage", event.getMilestonePercentage());
            rewardData.put("correlationId", correlationId);

            rewardsServiceClient.awardReward(rewardData, correlationId);

            rewardsAwardedCounter.increment();

            log.debug("Milestone reward awarded: userId={}, rewardAmount={}, milestoneType={}, correlationId={}",
                    event.getUserId(), rewardAmount, event.getMilestoneType(), correlationId);
        } else {
            log.debug("No reward for this milestone type: userId={}, milestoneType={}, correlationId={}",
                    event.getUserId(), event.getMilestoneType(), correlationId);
        }
    }

    private void awardMilestoneRewardsFallback(SavingsGoalMilestoneEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for milestone rewards: userId={}, correlationId={}, error={}",
                event.getUserId(), correlationId, e.getMessage());
    }

    private Integer calculateDaysToAchieve(SavingsGoalMilestoneEvent event) {
        if (event.getGoalCreatedAt() != null && event.getAchievedAt() != null) {
            return (int) ChronoUnit.DAYS.between(
                    event.getGoalCreatedAt().toLocalDate(),
                    event.getAchievedAt().toLocalDate()
            );
        }
        return null;
    }

    private BigDecimal calculateAverageContribution(SavingsGoalMilestoneEvent event) {
        if (event.getContributionsCount() != null && event.getContributionsCount() > 0 
                && event.getCurrentAmount() != null) {
            return event.getCurrentAmount()
                    .divide(BigDecimal.valueOf(event.getContributionsCount()), 2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal calculateCompletionRate(int completedGoals, int totalGoals) {
        if (totalGoals == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(completedGoals)
                .divide(BigDecimal.valueOf(totalGoals), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private void updateSavingsStreak(UserSavingsAnalytics analytics, LocalDate contributionDate) {
        if (analytics.getLastContributionDate() == null) {
            analytics.setCurrentSavingsStreak(1);
            analytics.setLongestSavingsStreak(1);
            return;
        }

        long daysBetween = ChronoUnit.DAYS.between(analytics.getLastContributionDate(), contributionDate);

        if (daysBetween == 1) {
            analytics.setCurrentSavingsStreak(analytics.getCurrentSavingsStreak() + 1);
            if (analytics.getCurrentSavingsStreak() > analytics.getLongestSavingsStreak()) {
                analytics.setLongestSavingsStreak(analytics.getCurrentSavingsStreak());
            }
        } else if (daysBetween > 1) {
            analytics.setCurrentSavingsStreak(1);
        }
    }

    private BigDecimal calculateSavingsVelocity(SavingsGoalMilestoneEvent event) {
        if (event.getGoalCreatedAt() != null && event.getCurrentAmount() != null) {
            long daysSinceCreation = ChronoUnit.DAYS.between(
                    event.getGoalCreatedAt().toLocalDate(),
                    LocalDate.now()
            );
            
            if (daysSinceCreation > 0) {
                return event.getCurrentAmount()
                        .divide(BigDecimal.valueOf(daysSinceCreation), 2, RoundingMode.HALF_UP);
            }
        }
        return BigDecimal.ZERO;
    }

    private int calculateEngagementBoost(String milestoneType) {
        switch (milestoneType) {
            case "GOAL_COMPLETED":
                return 50;
            case "MILESTONE_75_PERCENT":
                return 30;
            case "MILESTONE_50_PERCENT":
                return 20;
            case "MILESTONE_25_PERCENT":
                return 10;
            case "FIRST_CONTRIBUTION":
                return 15;
            case "STREAK_MILESTONE":
                return 25;
            default:
                return 5;
        }
    }

    private BigDecimal calculateConsistencyScore(SavingsGoalMilestoneEvent event) {
        if (event.getContributionsCount() != null && event.getGoalCreatedAt() != null) {
            long daysSinceCreation = ChronoUnit.DAYS.between(
                    event.getGoalCreatedAt().toLocalDate(),
                    LocalDate.now()
            );
            
            if (daysSinceCreation > 0) {
                double contributionRate = (double) event.getContributionsCount() / daysSinceCreation;
                return BigDecimal.valueOf(Math.min(contributionRate * 100, 100))
                        .setScale(2, RoundingMode.HALF_UP);
            }
        }
        return BigDecimal.ZERO;
    }

    private String determineContributionFrequency(SavingsGoalMilestoneEvent event) {
        if (event.getContributionsCount() == null || event.getGoalCreatedAt() == null) {
            return "UNKNOWN";
        }

        long daysSinceCreation = ChronoUnit.DAYS.between(
                event.getGoalCreatedAt().toLocalDate(),
                LocalDate.now()
        );

        if (daysSinceCreation == 0) {
            return "NEW";
        }

        double avgDaysBetweenContributions = (double) daysSinceCreation / event.getContributionsCount();

        if (avgDaysBetweenContributions <= 1) {
            return "DAILY";
        } else if (avgDaysBetweenContributions <= 7) {
            return "WEEKLY";
        } else if (avgDaysBetweenContributions <= 14) {
            return "BIWEEKLY";
        } else if (avgDaysBetweenContributions <= 31) {
            return "MONTHLY";
        } else {
            return "IRREGULAR";
        }
    }

    private String determineNotificationType(String milestoneType) {
        switch (milestoneType) {
            case "GOAL_COMPLETED":
                return "SAVINGS_GOAL_COMPLETED";
            case "MILESTONE_75_PERCENT":
                return "SAVINGS_MILESTONE_75";
            case "MILESTONE_50_PERCENT":
                return "SAVINGS_MILESTONE_50";
            case "MILESTONE_25_PERCENT":
                return "SAVINGS_MILESTONE_25";
            case "FIRST_CONTRIBUTION":
                return "SAVINGS_FIRST_CONTRIBUTION";
            case "STREAK_MILESTONE":
                return "SAVINGS_STREAK_ACHIEVED";
            default:
                return "SAVINGS_MILESTONE_GENERIC";
        }
    }

    private BigDecimal calculateMilestoneReward(SavingsGoalMilestoneEvent event) {
        BigDecimal baseReward = BigDecimal.ZERO;

        switch (event.getMilestoneType()) {
            case "GOAL_COMPLETED":
                baseReward = new BigDecimal("20.00");
                break;
            case "MILESTONE_75_PERCENT":
                baseReward = new BigDecimal("10.00");
                break;
            case "MILESTONE_50_PERCENT":
                baseReward = new BigDecimal("5.00");
                break;
            case "FIRST_CONTRIBUTION":
                baseReward = new BigDecimal("5.00");
                break;
            case "STREAK_MILESTONE":
                baseReward = new BigDecimal("15.00");
                break;
            default:
                return BigDecimal.ZERO;
        }

        if (Boolean.TRUE.equals(event.getIsFirstMilestone())) {
            baseReward = baseReward.multiply(new BigDecimal("1.5"));
        }

        return baseReward;
    }
}