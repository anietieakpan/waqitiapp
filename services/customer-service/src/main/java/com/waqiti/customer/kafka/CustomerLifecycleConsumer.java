package com.waqiti.customer.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.CustomerLifecycleEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.customer.service.CustomerLifecycleService;
import com.waqiti.customer.service.CustomerAnalyticsService;
import com.waqiti.customer.service.RetentionService;
import com.waqiti.customer.model.CustomerLifecycle;
import com.waqiti.customer.model.LifecycleStage;
import com.waqiti.customer.repository.CustomerLifecycleRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Consumer for processing customer lifecycle events.
 * Tracks customer journey stages, lifecycle transitions, and retention metrics.
 */
@Slf4j
@Component
public class CustomerLifecycleConsumer extends BaseKafkaConsumer<CustomerLifecycleEvent> {

    private static final String TOPIC = "customer-lifecycle-events";
    private static final Set<String> CRITICAL_STAGES = Set.of(
        "ONBOARDING", "FIRST_TRANSACTION", "CHURNING", "REACTIVATION"
    );

    private final CustomerLifecycleService customerLifecycleService;
    private final CustomerAnalyticsService customerAnalyticsService;
    private final RetentionService retentionService;
    private final CustomerLifecycleRepository customerLifecycleRepository;

    // Metrics
    private final Counter processedCounter;
    private final Counter stageTransitionCounter;
    private final Counter churnRiskCounter;
    private final Counter reactivationCounter;
    private final Timer processingTimer;

    @Autowired
    public CustomerLifecycleConsumer(
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            CustomerLifecycleService customerLifecycleService,
            CustomerAnalyticsService customerAnalyticsService,
            RetentionService retentionService,
            CustomerLifecycleRepository customerLifecycleRepository) {
        super(objectMapper, TOPIC);
        this.customerLifecycleService = customerLifecycleService;
        this.customerAnalyticsService = customerAnalyticsService;
        this.retentionService = retentionService;
        this.customerLifecycleRepository = customerLifecycleRepository;

        this.processedCounter = Counter.builder("customer_lifecycle_processed_total")
                .description("Total customer lifecycle events processed")
                .register(meterRegistry);
        this.stageTransitionCounter = Counter.builder("lifecycle_stage_transition_total")
                .description("Total lifecycle stage transitions")
                .register(meterRegistry);
        this.churnRiskCounter = Counter.builder("churn_risk_detected_total")
                .description("Total churn risk cases detected")
                .register(meterRegistry);
        this.reactivationCounter = Counter.builder("customer_reactivation_total")
                .description("Total customer reactivations")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("customer_lifecycle_processing_duration")
                .description("Time taken to process customer lifecycle events")
                .register(meterRegistry);
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @KafkaListener(topics = TOPIC, groupId = "customer-service-lifecycle-group")
    @Transactional
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment ack) {

        Timer.Sample sample = Timer.start();

        try {
            log.info("Processing customer lifecycle event - Key: {}, Partition: {}, Offset: {}",
                    key, partition, record.offset());

            CustomerLifecycleEvent event = deserializeEvent(record.value(), CustomerLifecycleEvent.class);

            // Validate required fields
            validateEvent(event);

            // Check for duplicate processing
            if (isAlreadyProcessed(event.getCustomerId(), event.getLifecycleStage(), event.getEventId())) {
                log.info("Customer lifecycle event already processed: {} - {}",
                        event.getCustomerId(), event.getLifecycleStage());
                ack.acknowledge();
                return;
            }

            // Process the customer lifecycle event
            processCustomerLifecycleEvent(event);

            processedCounter.increment();
            log.info("Successfully processed customer lifecycle event: {} - {}",
                    event.getCustomerId(), event.getLifecycleStage());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing customer lifecycle event: {}", record.value(), e);
            throw new RuntimeException("Failed to process customer lifecycle event", e);
        } finally {
            sample.stop(processingTimer);
        }
    }

    private void processCustomerLifecycleEvent(CustomerLifecycleEvent event) {
        try {
            // Create customer lifecycle record
            CustomerLifecycle lifecycle = createCustomerLifecycle(event);

            // Get previous lifecycle stage
            LifecycleStage previousStage = customerLifecycleService.getPreviousLifecycleStage(event.getCustomerId());

            // Check for stage transition
            if (isStageTransition(previousStage, event.getLifecycleStage())) {
                handleStageTransition(event, lifecycle, previousStage);
                stageTransitionCounter.increment();
            }

            // Handle critical lifecycle stages
            if (isCriticalStage(event.getLifecycleStage())) {
                handleCriticalStage(event, lifecycle);
            }

            // Check for churn risk
            if (isChurnRisk(event)) {
                handleChurnRisk(event, lifecycle);
                churnRiskCounter.increment();
            }

            // Handle reactivation
            if (isReactivation(event, previousStage)) {
                handleReactivation(event, lifecycle);
                reactivationCounter.increment();
            }

            // Update customer analytics
            updateCustomerAnalytics(event, lifecycle);

            // Calculate lifecycle metrics
            calculateLifecycleMetrics(event, lifecycle);

            // Save the lifecycle record
            customerLifecycleRepository.save(lifecycle);

            // Trigger lifecycle-based actions
            triggerLifecycleActions(event, lifecycle);

            log.info("Processed customer lifecycle: {} - {} (Previous: {})",
                    event.getCustomerId(), event.getLifecycleStage(),
                    previousStage != null ? previousStage.getStageName() : "NONE");

        } catch (Exception e) {
            log.error("Error processing customer lifecycle event: {}", event.getCustomerId(), e);
            throw new RuntimeException("Failed to process customer lifecycle event", e);
        }
    }

    private CustomerLifecycle createCustomerLifecycle(CustomerLifecycleEvent event) {
        return CustomerLifecycle.builder()
                .customerId(event.getCustomerId())
                .lifecycleStage(event.getLifecycleStage())
                .previousStage(event.getPreviousStage())
                .stageEntryDate(event.getStageEntryDate())
                .daysSinceLastActivity(event.getDaysSinceLastActivity())
                .totalTransactions(event.getTotalTransactions())
                .totalValue(event.getTotalValue())
                .engagementScore(event.getEngagementScore())
                .riskScore(event.getRiskScore())
                .segmentId(event.getSegmentId())
                .channelPreference(event.getChannelPreference())
                .lastActivityType(event.getLastActivityType())
                .predictedNextStage(event.getPredictedNextStage())
                .timestamp(event.getTimestamp())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private boolean isStageTransition(LifecycleStage previousStage, String currentStage) {
        return previousStage != null && !previousStage.getStageName().equals(currentStage);
    }

    private void handleStageTransition(CustomerLifecycleEvent event,
                                     CustomerLifecycle lifecycle,
                                     LifecycleStage previousStage) {
        try {
            lifecycle.setStageTransition(true);

            // Calculate time in previous stage
            if (previousStage.getEntryDate() != null) {
                long daysInPreviousStage = java.time.temporal.ChronoUnit.DAYS.between(
                    previousStage.getEntryDate(), event.getStageEntryDate()
                );
                lifecycle.setDaysInPreviousStage((int) daysInPreviousStage);
            }

            // Analyze transition pattern
            var transitionAnalysis = customerLifecycleService.analyzeStageTransition(
                event.getCustomerId(), previousStage.getStageName(), event.getLifecycleStage()
            );

            lifecycle.setTransitionAnalysis(transitionAnalysis);

            // Check for unusual transitions
            if (transitionAnalysis.isUnusualTransition()) {
                customerLifecycleService.flagUnusualTransition(
                    event.getCustomerId(), transitionAnalysis
                );
            }

            // Update transition statistics
            customerAnalyticsService.updateTransitionStatistics(
                previousStage.getStageName(), event.getLifecycleStage()
            );

            log.info("Lifecycle stage transition: {} - {} -> {}",
                    event.getCustomerId(), previousStage.getStageName(), event.getLifecycleStage());

        } catch (Exception e) {
            log.error("Error handling stage transition: {}", event.getCustomerId(), e);
            throw new RuntimeException("Failed to handle stage transition", e);
        }
    }

    private boolean isCriticalStage(String lifecycleStage) {
        return CRITICAL_STAGES.contains(lifecycleStage.toUpperCase());
    }

    private void handleCriticalStage(CustomerLifecycleEvent event, CustomerLifecycle lifecycle) {
        try {
            lifecycle.setCriticalStage(true);

            switch (event.getLifecycleStage().toUpperCase()) {
                case "ONBOARDING":
                    handleOnboardingStage(event, lifecycle);
                    break;
                case "FIRST_TRANSACTION":
                    handleFirstTransactionStage(event, lifecycle);
                    break;
                case "CHURNING":
                    handleChurningStage(event, lifecycle);
                    break;
                case "REACTIVATION":
                    handleReactivationStage(event, lifecycle);
                    break;
            }

            // Create critical stage alerts
            customerLifecycleService.createCriticalStageAlert(
                event.getCustomerId(), event.getLifecycleStage()
            );

        } catch (Exception e) {
            log.error("Error handling critical stage: {} - {}",
                    event.getCustomerId(), event.getLifecycleStage(), e);
            throw new RuntimeException("Failed to handle critical stage", e);
        }
    }

    private void handleOnboardingStage(CustomerLifecycleEvent event, CustomerLifecycle lifecycle) {
        // Track onboarding progress
        customerLifecycleService.trackOnboardingProgress(event.getCustomerId());

        // Send onboarding welcome communications
        customerLifecycleService.sendOnboardingCommunications(event.getCustomerId());

        // Set up onboarding monitoring
        customerLifecycleService.setupOnboardingMonitoring(event.getCustomerId());
    }

    private void handleFirstTransactionStage(CustomerLifecycleEvent event, CustomerLifecycle lifecycle) {
        // Celebrate first transaction milestone
        customerLifecycleService.celebrateFirstTransaction(event.getCustomerId());

        // Analyze first transaction patterns
        customerAnalyticsService.analyzeFirstTransactionPattern(event.getCustomerId());

        // Optimize for next transaction
        customerLifecycleService.optimizeForNextTransaction(event.getCustomerId());
    }

    private void handleChurningStage(CustomerLifecycleEvent event, CustomerLifecycle lifecycle) {
        // Trigger retention campaigns
        retentionService.triggerRetentionCampaign(event.getCustomerId());

        // Escalate to retention team
        retentionService.escalateToRetentionTeam(event.getCustomerId());

        // Analyze churn reasons
        customerAnalyticsService.analyzeChurnReasons(event.getCustomerId());
    }

    private void handleReactivationStage(CustomerLifecycleEvent event, CustomerLifecycle lifecycle) {
        // Welcome back communications
        customerLifecycleService.sendWelcomeBackCommunications(event.getCustomerId());

        // Re-engage with personalized offers
        customerLifecycleService.reengageWithOffers(event.getCustomerId());

        // Monitor reactivation success
        retentionService.monitorReactivationSuccess(event.getCustomerId());
    }

    private boolean isChurnRisk(CustomerLifecycleEvent event) {
        return event.getRiskScore() > 70 || // High risk score
               event.getDaysSinceLastActivity() > 30 || // Inactive for 30+ days
               "CHURNING".equals(event.getLifecycleStage()) ||
               event.getEngagementScore() < 20; // Low engagement
    }

    private void handleChurnRisk(CustomerLifecycleEvent event, CustomerLifecycle lifecycle) {
        try {
            lifecycle.setChurnRisk(true);

            // Calculate churn probability
            double churnProbability = retentionService.calculateChurnProbability(event.getCustomerId());
            lifecycle.setChurnProbability(churnProbability);

            // Create churn risk alert
            retentionService.createChurnRiskAlert(event.getCustomerId(), churnProbability);

            // Trigger retention interventions
            if (churnProbability > 0.7) {
                retentionService.triggerHighRiskInterventions(event.getCustomerId());
            } else if (churnProbability > 0.4) {
                retentionService.triggerMediumRiskInterventions(event.getCustomerId());
            }

            log.warn("Churn risk detected: {} - Probability: {}%",
                    event.getCustomerId(), Math.round(churnProbability * 100));

        } catch (Exception e) {
            log.error("Error handling churn risk: {}", event.getCustomerId(), e);
            throw new RuntimeException("Failed to handle churn risk", e);
        }
    }

    private boolean isReactivation(CustomerLifecycleEvent event, LifecycleStage previousStage) {
        return previousStage != null &&
               ("CHURNED".equals(previousStage.getStageName()) || "INACTIVE".equals(previousStage.getStageName())) &&
               "ACTIVE".equals(event.getLifecycleStage());
    }

    private void handleReactivation(CustomerLifecycleEvent event, CustomerLifecycle lifecycle) {
        try {
            lifecycle.setReactivation(true);

            // Calculate reactivation metrics
            var reactivationMetrics = retentionService.calculateReactivationMetrics(event.getCustomerId());
            lifecycle.setReactivationMetrics(reactivationMetrics);

            // Update retention statistics
            retentionService.updateRetentionStatistics(event.getCustomerId(), true);

            // Celebrate successful reactivation
            customerLifecycleService.celebrateReactivation(event.getCustomerId());

            log.info("Customer reactivation successful: {} - Days inactive: {}",
                    event.getCustomerId(), reactivationMetrics.getDaysInactive());

        } catch (Exception e) {
            log.error("Error handling reactivation: {}", event.getCustomerId(), e);
            throw new RuntimeException("Failed to handle reactivation", e);
        }
    }

    private void updateCustomerAnalytics(CustomerLifecycleEvent event, CustomerLifecycle lifecycle) {
        try {
            // Update customer segment analytics
            customerAnalyticsService.updateSegmentAnalytics(
                event.getSegmentId(), event.getLifecycleStage()
            );

            // Update lifecycle stage statistics
            customerAnalyticsService.updateLifecycleStageStatistics(
                event.getLifecycleStage(), event.getEngagementScore()
            );

            // Update customer value metrics
            customerAnalyticsService.updateCustomerValueMetrics(
                event.getCustomerId(), event.getTotalValue()
            );

        } catch (Exception e) {
            log.error("Error updating customer analytics: {}", event.getCustomerId(), e);
            // Don't fail the processing for analytics update errors
        }
    }

    private void calculateLifecycleMetrics(CustomerLifecycleEvent event, CustomerLifecycle lifecycle) {
        try {
            // Calculate customer lifetime value
            double clv = customerAnalyticsService.calculateCustomerLifetimeValue(event.getCustomerId());
            lifecycle.setCustomerLifetimeValue(clv);

            // Calculate engagement trends
            var engagementTrend = customerAnalyticsService.calculateEngagementTrend(event.getCustomerId());
            lifecycle.setEngagementTrend(engagementTrend);

            // Calculate stage progression probability
            var stageProgression = customerLifecycleService.calculateStageProgressionProbability(
                event.getCustomerId(), event.getLifecycleStage()
            );
            lifecycle.setStageProgressionProbability(stageProgression);

        } catch (Exception e) {
            log.error("Error calculating lifecycle metrics: {}", event.getCustomerId(), e);
            // Don't fail the processing for metrics calculation errors
        }
    }

    private void triggerLifecycleActions(CustomerLifecycleEvent event, CustomerLifecycle lifecycle) {
        try {
            // Trigger personalized communications
            customerLifecycleService.triggerPersonalizedCommunications(
                event.getCustomerId(), event.getLifecycleStage()
            );

            // Update customer recommendations
            customerLifecycleService.updateCustomerRecommendations(
                event.getCustomerId(), lifecycle
            );

            // Trigger lifecycle-based campaigns
            customerLifecycleService.triggerLifecycleCampaigns(
                event.getCustomerId(), event.getLifecycleStage()
            );

        } catch (Exception e) {
            log.error("Error triggering lifecycle actions: {}", event.getCustomerId(), e);
            // Don't fail the processing for action triggering errors
        }
    }

    private boolean isAlreadyProcessed(String customerId, String lifecycleStage, String eventId) {
        return customerLifecycleRepository.existsByCustomerIdAndLifecycleStageAndEventId(
            customerId, lifecycleStage, eventId
        );
    }

    private void validateEvent(CustomerLifecycleEvent event) {
        if (event.getCustomerId() == null || event.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        if (event.getLifecycleStage() == null || event.getLifecycleStage().trim().isEmpty()) {
            throw new IllegalArgumentException("Lifecycle stage cannot be null or empty");
        }
        if (event.getStageEntryDate() == null) {
            throw new IllegalArgumentException("Stage entry date cannot be null");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }
    }

    @Override
    protected void handleProcessingError(String message, Exception error) {
        log.error("Customer lifecycle processing error: {}", message, error);
        // Additional error handling logic
    }

    @Override
    protected void logProcessingMetrics(String key, long processingTime) {
        log.debug("Processed customer lifecycle event - Key: {}, Time: {}ms", key, processingTime);
    }
}