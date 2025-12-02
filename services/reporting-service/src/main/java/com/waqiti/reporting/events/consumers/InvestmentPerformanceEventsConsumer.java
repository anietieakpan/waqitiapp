package com.waqiti.reporting.events.consumers;

import com.waqiti.reporting.domain.*;
import com.waqiti.reporting.repository.*;
import com.waqiti.reporting.service.*;
import com.waqiti.common.events.investment.InvestmentPerformanceEvent;
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
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvestmentPerformanceEventsConsumer {

    private final InvestmentPerformanceRepository investmentPerformanceRepository;
    private final PortfolioSnapshotRepository portfolioSnapshotRepository;
    private final UserInvestmentMetricsRepository userInvestmentMetricsRepository;
    private final NotificationServiceClient notificationServiceClient;
    private final EventProcessingTrackingService eventProcessingTrackingService;
    private final MeterRegistry meterRegistry;

    private Counter eventsProcessedCounter;
    private Counter eventsFailedCounter;
    private Counter performanceUpdatesCounter;
    private Counter notificationsSentCounter;
    private Timer eventProcessingTimer;

    public InvestmentPerformanceEventsConsumer(
            InvestmentPerformanceRepository investmentPerformanceRepository,
            PortfolioSnapshotRepository portfolioSnapshotRepository,
            UserInvestmentMetricsRepository userInvestmentMetricsRepository,
            NotificationServiceClient notificationServiceClient,
            EventProcessingTrackingService eventProcessingTrackingService,
            MeterRegistry meterRegistry) {
        
        this.investmentPerformanceRepository = investmentPerformanceRepository;
        this.portfolioSnapshotRepository = portfolioSnapshotRepository;
        this.userInvestmentMetricsRepository = userInvestmentMetricsRepository;
        this.notificationServiceClient = notificationServiceClient;
        this.eventProcessingTrackingService = eventProcessingTrackingService;
        this.meterRegistry = meterRegistry;

        initializeMetrics();
    }

    private void initializeMetrics() {
        this.eventsProcessedCounter = Counter.builder("investment_performance_events_processed_total")
                .description("Total number of investment performance events processed")
                .tag("consumer", "investment-performance-consumer")
                .register(meterRegistry);

        this.eventsFailedCounter = Counter.builder("investment_performance_events_failed_total")
                .description("Total number of investment performance events failed")
                .tag("consumer", "investment-performance-consumer")
                .register(meterRegistry);

        this.performanceUpdatesCounter = Counter.builder("investment_performance_updates_total")
                .description("Total investment performance updates recorded")
                .tag("consumer", "investment-performance-consumer")
                .register(meterRegistry);

        this.notificationsSentCounter = Counter.builder("investment_performance_notifications_sent_total")
                .description("Total notifications sent for investment performance")
                .tag("consumer", "investment-performance-consumer")
                .register(meterRegistry);

        this.eventProcessingTimer = Timer.builder("investment_performance_event_processing_duration")
                .description("Time taken to process investment performance events")
                .tag("consumer", "investment-performance-consumer")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "${kafka.topics.investment-performance-events:investment-performance-events}",
            groupId = "${kafka.consumer.group-id:reporting-investment-performance-consumer-group}",
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
    public void handleInvestmentPerformanceEvent(
            @Payload InvestmentPerformanceEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = event.getCorrelationId();

        try {
            log.info("Processing investment performance event: eventId={}, investmentId={}, userId={}, " +
                    "currentValue={}, returnRate={}, topic={}, partition={}, offset={}, correlationId={}",
                    event.getEventId(), event.getInvestmentId(), event.getUserId(), 
                    event.getCurrentValue(), event.getReturnRate(),
                    topic, partition, offset, correlationId);

            if (eventProcessingTrackingService.isEventAlreadyProcessed(event.getEventId(), "INVESTMENT_PERFORMANCE")) {
                log.warn("Duplicate investment performance event detected: eventId={}, correlationId={}. Skipping processing.",
                        event.getEventId(), correlationId);
                acknowledgment.acknowledge();
                return;
            }

            validateEvent(event);

            recordPerformanceUpdate(event, correlationId);

            createPortfolioSnapshot(event, correlationId);

            updateUserInvestmentMetrics(event, correlationId);

            if (shouldNotifyUser(event)) {
                sendPerformanceNotification(event, correlationId);
            }

            eventProcessingTrackingService.markEventAsProcessed(
                    event.getEventId(),
                    "INVESTMENT_PERFORMANCE",
                    "reporting-service",
                    correlationId
            );

            eventsProcessedCounter.increment();

            acknowledgment.acknowledge();

            log.info("Successfully processed investment performance event: eventId={}, investmentId={}, correlationId={}",
                    event.getEventId(), event.getInvestmentId(), correlationId);

        } catch (Exception e) {
            eventsFailedCounter.increment();
            log.error("Failed to process investment performance event: eventId={}, investmentId={}, correlationId={}, error={}",
                    event.getEventId(), event.getInvestmentId(), correlationId, e.getMessage(), e);
            throw new RuntimeException("Investment performance event processing failed", e);
        } finally {
            sample.stop(eventProcessingTimer);
        }
    }

    private void validateEvent(InvestmentPerformanceEvent event) {
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID is required");
        }

        if (event.getInvestmentId() == null || event.getInvestmentId().trim().isEmpty()) {
            throw new IllegalArgumentException("Investment ID is required");
        }

        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }

        if (event.getCurrentValue() == null || event.getCurrentValue().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Current value must be non-negative");
        }

        if (event.getInitialInvestment() == null || event.getInitialInvestment().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Initial investment must be positive");
        }
    }

    @CircuitBreaker(name = "reportingService", fallbackMethod = "recordPerformanceUpdateFallback")
    @Retry(name = "reportingService")
    @TimeLimiter(name = "reportingService")
    private void recordPerformanceUpdate(InvestmentPerformanceEvent event, String correlationId) {
        log.debug("Recording investment performance update: investmentId={}, currentValue={}, correlationId={}",
                event.getInvestmentId(), event.getCurrentValue(), correlationId);

        InvestmentPerformance performance = InvestmentPerformance.builder()
                .investmentId(event.getInvestmentId())
                .userId(event.getUserId())
                .investmentType(event.getInvestmentType())
                .assetClass(event.getAssetClass())
                .initialInvestment(event.getInitialInvestment())
                .currentValue(event.getCurrentValue())
                .totalGainLoss(event.getTotalGainLoss())
                .totalGainLossPercentage(event.getTotalGainLossPercentage())
                .dailyReturn(event.getDailyReturn())
                .dailyReturnPercentage(event.getDailyReturnPercentage())
                .returnRate(event.getReturnRate())
                .returnPeriod(event.getReturnPeriod())
                .currency(event.getCurrency())
                .performanceDate(event.getPerformanceDate())
                .yearToDateReturn(event.getYearToDateReturn())
                .allTimeReturn(event.getAllTimeReturn())
                .correlationId(correlationId)
                .createdAt(LocalDateTime.now())
                .build();

        investmentPerformanceRepository.save(performance);

        performanceUpdatesCounter.increment();
        Counter.builder("investment_performance_by_type")
                .tag("investment_type", event.getInvestmentType())
                .register(meterRegistry)
                .increment();

        log.debug("Investment performance recorded: investmentId={}, performanceId={}, correlationId={}",
                event.getInvestmentId(), performance.getId(), correlationId);
    }

    private void recordPerformanceUpdateFallback(InvestmentPerformanceEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for performance update recording: investmentId={}, correlationId={}, error={}",
                event.getInvestmentId(), correlationId, e.getMessage());
    }

    @CircuitBreaker(name = "reportingService", fallbackMethod = "createPortfolioSnapshotFallback")
    @Retry(name = "reportingService")
    @TimeLimiter(name = "reportingService")
    private void createPortfolioSnapshot(InvestmentPerformanceEvent event, String correlationId) {
        log.debug("Creating portfolio snapshot: userId={}, investmentId={}, correlationId={}",
                event.getUserId(), event.getInvestmentId(), correlationId);

        Optional<PortfolioSnapshot> latestSnapshot = portfolioSnapshotRepository
                .findLatestByUserId(event.getUserId());

        BigDecimal totalPortfolioValue = event.getCurrentValue();
        if (latestSnapshot.isPresent()) {
            totalPortfolioValue = latestSnapshot.get().getTotalValue()
                    .subtract(event.getInitialInvestment())
                    .add(event.getCurrentValue());
        }

        PortfolioSnapshot snapshot = PortfolioSnapshot.builder()
                .userId(event.getUserId())
                .snapshotDate(event.getPerformanceDate())
                .totalValue(totalPortfolioValue)
                .totalInvested(calculateTotalInvested(event, latestSnapshot))
                .totalGainLoss(calculatePortfolioGainLoss(totalPortfolioValue, 
                        calculateTotalInvested(event, latestSnapshot)))
                .currency(event.getCurrency())
                .investmentCount(countUserInvestments(event.getUserId()))
                .correlationId(correlationId)
                .createdAt(LocalDateTime.now())
                .build();

        portfolioSnapshotRepository.save(snapshot);

        log.debug("Portfolio snapshot created: userId={}, snapshotId={}, totalValue={}, correlationId={}",
                event.getUserId(), snapshot.getId(), totalPortfolioValue, correlationId);
    }

    private void createPortfolioSnapshotFallback(InvestmentPerformanceEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for portfolio snapshot creation: userId={}, correlationId={}, error={}",
                event.getUserId(), correlationId, e.getMessage());
    }

    @CircuitBreaker(name = "reportingService", fallbackMethod = "updateUserInvestmentMetricsFallback")
    @Retry(name = "reportingService")
    @TimeLimiter(name = "reportingService")
    private void updateUserInvestmentMetrics(InvestmentPerformanceEvent event, String correlationId) {
        log.debug("Updating user investment metrics: userId={}, correlationId={}",
                event.getUserId(), correlationId);

        Optional<UserInvestmentMetrics> metricsOpt = userInvestmentMetricsRepository
                .findByUserId(event.getUserId());

        UserInvestmentMetrics metrics;
        if (metricsOpt.isPresent()) {
            metrics = metricsOpt.get();
        } else {
            metrics = UserInvestmentMetrics.builder()
                    .userId(event.getUserId())
                    .totalInvestments(0)
                    .activeInvestments(0)
                    .totalInvestedAmount(BigDecimal.ZERO)
                    .currentPortfolioValue(BigDecimal.ZERO)
                    .totalReturns(BigDecimal.ZERO)
                    .averageReturnRate(BigDecimal.ZERO)
                    .bestPerformingAsset(null)
                    .worstPerformingAsset(null)
                    .riskScore(BigDecimal.ZERO)
                    .build();
        }

        metrics.setCurrentPortfolioValue(
                metrics.getCurrentPortfolioValue()
                        .subtract(event.getInitialInvestment())
                        .add(event.getCurrentValue())
        );

        metrics.setTotalReturns(
                metrics.getCurrentPortfolioValue().subtract(metrics.getTotalInvestedAmount())
        );

        if (metrics.getTotalInvestedAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal returnRate = metrics.getTotalReturns()
                    .divide(metrics.getTotalInvestedAmount(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            metrics.setAverageReturnRate(returnRate);
        }

        updateBestWorstPerformingAssets(metrics, event);

        metrics.setLastUpdatedAt(LocalDateTime.now());

        userInvestmentMetricsRepository.save(metrics);

        log.debug("User investment metrics updated: userId={}, portfolioValue={}, totalReturns={}, correlationId={}",
                event.getUserId(), metrics.getCurrentPortfolioValue(), metrics.getTotalReturns(), correlationId);
    }

    private void updateUserInvestmentMetricsFallback(InvestmentPerformanceEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for user metrics update: userId={}, correlationId={}, error={}",
                event.getUserId(), correlationId, e.getMessage());
    }

    @CircuitBreaker(name = "notificationService", fallbackMethod = "sendPerformanceNotificationFallback")
    @Retry(name = "notificationService")
    @TimeLimiter(name = "notificationService")
    private void sendPerformanceNotification(InvestmentPerformanceEvent event, String correlationId) {
        log.debug("Sending investment performance notification: userId={}, investmentId={}, correlationId={}",
                event.getUserId(), event.getInvestmentId(), correlationId);

        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("userId", event.getUserId());
        notificationData.put("investmentId", event.getInvestmentId());
        notificationData.put("investmentType", event.getInvestmentType());
        notificationData.put("assetClass", event.getAssetClass());
        notificationData.put("currentValue", event.getCurrentValue());
        notificationData.put("initialInvestment", event.getInitialInvestment());
        notificationData.put("totalGainLoss", event.getTotalGainLoss());
        notificationData.put("totalGainLossPercentage", event.getTotalGainLossPercentage());
        notificationData.put("returnRate", event.getReturnRate());
        notificationData.put("currency", event.getCurrency());
        notificationData.put("performanceDate", event.getPerformanceDate());
        notificationData.put("notificationType", determineNotificationType(event));
        notificationData.put("correlationId", correlationId);

        notificationServiceClient.sendNotification(notificationData, correlationId);

        notificationsSentCounter.increment();

        log.debug("Investment performance notification sent: userId={}, investmentId={}, correlationId={}",
                event.getUserId(), event.getInvestmentId(), correlationId);
    }

    private void sendPerformanceNotificationFallback(InvestmentPerformanceEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for performance notification: userId={}, correlationId={}, error={}",
                event.getUserId(), correlationId, e.getMessage());
    }

    private boolean shouldNotifyUser(InvestmentPerformanceEvent event) {
        if (event.getTotalGainLossPercentage() == null) {
            return false;
        }

        BigDecimal changePercentage = event.getTotalGainLossPercentage().abs();

        if (changePercentage.compareTo(new BigDecimal("10")) >= 0) {
            return true;
        }

        if (event.getDailyReturnPercentage() != null) {
            BigDecimal dailyChangePercentage = event.getDailyReturnPercentage().abs();
            if (dailyChangePercentage.compareTo(new BigDecimal("5")) >= 0) {
                return true;
            }
        }

        return false;
    }

    private String determineNotificationType(InvestmentPerformanceEvent event) {
        if (event.getTotalGainLossPercentage() == null) {
            return "INVESTMENT_PERFORMANCE_UPDATE";
        }

        BigDecimal changePercentage = event.getTotalGainLossPercentage();

        if (changePercentage.compareTo(new BigDecimal("10")) >= 0) {
            return "INVESTMENT_SIGNIFICANT_GAIN";
        } else if (changePercentage.compareTo(new BigDecimal("-10")) <= 0) {
            return "INVESTMENT_SIGNIFICANT_LOSS";
        } else if (changePercentage.compareTo(BigDecimal.ZERO) > 0) {
            return "INVESTMENT_POSITIVE_PERFORMANCE";
        } else if (changePercentage.compareTo(BigDecimal.ZERO) < 0) {
            return "INVESTMENT_NEGATIVE_PERFORMANCE";
        }

        return "INVESTMENT_PERFORMANCE_UPDATE";
    }

    private BigDecimal calculateTotalInvested(InvestmentPerformanceEvent event, 
                                             Optional<PortfolioSnapshot> latestSnapshot) {
        if (latestSnapshot.isEmpty()) {
            return event.getInitialInvestment();
        }

        return latestSnapshot.get().getTotalInvested().add(event.getInitialInvestment());
    }

    private BigDecimal calculatePortfolioGainLoss(BigDecimal totalValue, BigDecimal totalInvested) {
        return totalValue.subtract(totalInvested);
    }

    private int countUserInvestments(String userId) {
        return investmentPerformanceRepository.countActiveInvestmentsByUserId(userId);
    }

    private void updateBestWorstPerformingAssets(UserInvestmentMetrics metrics, 
                                                 InvestmentPerformanceEvent event) {
        if (event.getTotalGainLossPercentage() == null) {
            return;
        }

        if (metrics.getBestPerformingAsset() == null) {
            metrics.setBestPerformingAsset(event.getAssetClass());
            metrics.setBestPerformingAssetReturn(event.getTotalGainLossPercentage());
        } else if (event.getTotalGainLossPercentage().compareTo(metrics.getBestPerformingAssetReturn()) > 0) {
            metrics.setBestPerformingAsset(event.getAssetClass());
            metrics.setBestPerformingAssetReturn(event.getTotalGainLossPercentage());
        }

        if (metrics.getWorstPerformingAsset() == null) {
            metrics.setWorstPerformingAsset(event.getAssetClass());
            metrics.setWorstPerformingAssetReturn(event.getTotalGainLossPercentage());
        } else if (event.getTotalGainLossPercentage().compareTo(metrics.getWorstPerformingAssetReturn()) < 0) {
            metrics.setWorstPerformingAsset(event.getAssetClass());
            metrics.setWorstPerformingAssetReturn(event.getTotalGainLossPercentage());
        }
    }
}