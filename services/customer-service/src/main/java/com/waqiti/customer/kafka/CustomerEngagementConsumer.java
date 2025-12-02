package com.waqiti.customer.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.CustomerEngagementEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.customer.service.EngagementAnalyticsService;
import com.waqiti.customer.service.PersonalizationService;
import com.waqiti.customer.service.CampaignService;
import com.waqiti.customer.model.CustomerEngagement;
import com.waqiti.customer.model.EngagementPattern;
import com.waqiti.customer.repository.CustomerEngagementRepository;
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
 * Consumer for processing customer engagement events.
 * Tracks customer interactions, engagement patterns, and personalization opportunities.
 */
@Slf4j
@Component
public class CustomerEngagementConsumer extends BaseKafkaConsumer<CustomerEngagementEvent> {

    private static final String TOPIC = "customer-engagement-events";
    private static final Set<String> HIGH_VALUE_INTERACTIONS = Set.of(
        "TRANSACTION", "INVESTMENT", "LOAN_APPLICATION", "INSURANCE_PURCHASE"
    );
    private static final Set<String> ENGAGEMENT_CHANNELS = Set.of(
        "MOBILE_APP", "WEB", "EMAIL", "SMS", "PHONE", "BRANCH"
    );

    private final EngagementAnalyticsService engagementAnalyticsService;
    private final PersonalizationService personalizationService;
    private final CampaignService campaignService;
    private final CustomerEngagementRepository customerEngagementRepository;

    // Metrics
    private final Counter processedCounter;
    private final Counter highEngagementCounter;
    private final Counter lowEngagementCounter;
    private final Counter personalizationTriggeredCounter;
    private final Timer processingTimer;

    @Autowired
    public CustomerEngagementConsumer(
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            EngagementAnalyticsService engagementAnalyticsService,
            PersonalizationService personalizationService,
            CampaignService campaignService,
            CustomerEngagementRepository customerEngagementRepository) {
        super(objectMapper, TOPIC);
        this.engagementAnalyticsService = engagementAnalyticsService;
        this.personalizationService = personalizationService;
        this.campaignService = campaignService;
        this.customerEngagementRepository = customerEngagementRepository;

        this.processedCounter = Counter.builder("customer_engagement_processed_total")
                .description("Total customer engagement events processed")
                .register(meterRegistry);
        this.highEngagementCounter = Counter.builder("high_engagement_detected_total")
                .description("Total high engagement sessions detected")
                .register(meterRegistry);
        this.lowEngagementCounter = Counter.builder("low_engagement_detected_total")
                .description("Total low engagement sessions detected")
                .register(meterRegistry);
        this.personalizationTriggeredCounter = Counter.builder("personalization_triggered_total")
                .description("Total personalization events triggered")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("customer_engagement_processing_duration")
                .description("Time taken to process customer engagement events")
                .register(meterRegistry);
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @KafkaListener(topics = TOPIC, groupId = "customer-service-engagement-group")
    @Transactional
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment ack) {

        Timer.Sample sample = Timer.start();

        try {
            log.info("Processing customer engagement event - Key: {}, Partition: {}, Offset: {}",
                    key, partition, record.offset());

            CustomerEngagementEvent event = deserializeEvent(record.value(), CustomerEngagementEvent.class);

            // Validate required fields
            validateEvent(event);

            // Check for duplicate processing
            if (isAlreadyProcessed(event.getCustomerId(), event.getSessionId(), event.getEventId())) {
                log.info("Customer engagement event already processed: {} - {}",
                        event.getCustomerId(), event.getSessionId());
                ack.acknowledge();
                return;
            }

            // Process the customer engagement event
            processCustomerEngagementEvent(event);

            processedCounter.increment();
            log.info("Successfully processed customer engagement event: {} - {}",
                    event.getCustomerId(), event.getInteractionType());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing customer engagement event: {}", record.value(), e);
            throw new RuntimeException("Failed to process customer engagement event", e);
        } finally {
            sample.stop(processingTimer);
        }
    }

    private void processCustomerEngagementEvent(CustomerEngagementEvent event) {
        try {
            // Create customer engagement record
            CustomerEngagement engagement = createCustomerEngagement(event);

            // Analyze engagement level
            analyzeEngagementLevel(event, engagement);

            // Check for high-value interactions
            if (isHighValueInteraction(event)) {
                handleHighValueInteraction(event, engagement);
                highEngagementCounter.increment();
            }

            // Check for low engagement patterns
            if (isLowEngagement(event)) {
                handleLowEngagement(event, engagement);
                lowEngagementCounter.increment();
            }

            // Analyze engagement patterns
            analyzeEngagementPatterns(event, engagement);

            // Update customer preferences
            updateCustomerPreferences(event, engagement);

            // Trigger personalization
            if (shouldTriggerPersonalization(event, engagement)) {
                triggerPersonalization(event, engagement);
                personalizationTriggeredCounter.increment();
            }

            // Update engagement analytics
            updateEngagementAnalytics(event, engagement);

            // Save the engagement record
            customerEngagementRepository.save(engagement);

            // Trigger real-time actions
            triggerRealTimeActions(event, engagement);

            log.info("Processed customer engagement: {} - {} via {} (Score: {})",
                    event.getCustomerId(), event.getInteractionType(),
                    event.getChannel(), engagement.getEngagementScore());

        } catch (Exception e) {
            log.error("Error processing customer engagement event: {}", event.getCustomerId(), e);
            throw new RuntimeException("Failed to process customer engagement event", e);
        }
    }

    private CustomerEngagement createCustomerEngagement(CustomerEngagementEvent event) {
        return CustomerEngagement.builder()
                .customerId(event.getCustomerId())
                .sessionId(event.getSessionId())
                .interactionType(event.getInteractionType())
                .channel(event.getChannel())
                .deviceType(event.getDeviceType())
                .pageViews(event.getPageViews())
                .sessionDuration(event.getSessionDuration())
                .clickCount(event.getClickCount())
                .scrollDepth(event.getScrollDepth())
                .featureUsage(event.getFeatureUsage())
                .contentInteractions(event.getContentInteractions())
                .searchQueries(event.getSearchQueries())
                .errorEncountered(event.isErrorEncountered())
                .completedActions(event.getCompletedActions())
                .abandonedActions(event.getAbandonedActions())
                .timestamp(event.getTimestamp())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void analyzeEngagementLevel(CustomerEngagementEvent event, CustomerEngagement engagement) {
        try {
            // Calculate engagement score
            double engagementScore = engagementAnalyticsService.calculateEngagementScore(
                event.getSessionDuration(),
                event.getPageViews(),
                event.getClickCount(),
                event.getScrollDepth(),
                event.getCompletedActions().size(),
                event.getAbandonedActions().size()
            );

            engagement.setEngagementScore(engagementScore);

            // Determine engagement level
            String engagementLevel = determineEngagementLevel(engagementScore);
            engagement.setEngagementLevel(engagementLevel);

            // Calculate session quality
            double sessionQuality = engagementAnalyticsService.calculateSessionQuality(
                event.getSessionDuration(),
                event.getFeatureUsage(),
                event.isErrorEncountered()
            );

            engagement.setSessionQuality(sessionQuality);

        } catch (Exception e) {
            log.error("Error analyzing engagement level: {}", event.getCustomerId(), e);
            // Set default values
            engagement.setEngagementScore(0.0);
            engagement.setEngagementLevel("UNKNOWN");
        }
    }

    private boolean isHighValueInteraction(CustomerEngagementEvent event) {
        return HIGH_VALUE_INTERACTIONS.contains(event.getInteractionType().toUpperCase()) ||
               event.getSessionDuration() > 300 || // 5+ minutes
               event.getCompletedActions().size() > 3;
    }

    private void handleHighValueInteraction(CustomerEngagementEvent event, CustomerEngagement engagement) {
        try {
            engagement.setHighValueInteraction(true);

            // Create high engagement alert
            engagementAnalyticsService.createHighEngagementAlert(
                event.getCustomerId(), event.getInteractionType(), engagement.getEngagementScore()
            );

            // Trigger high-value customer actions
            campaignService.triggerHighValueCustomerActions(event.getCustomerId());

            // Update customer value tier
            engagementAnalyticsService.updateCustomerValueTier(
                event.getCustomerId(), engagement.getEngagementScore()
            );

            // Send real-time offers
            personalizationService.sendRealTimeOffers(
                event.getCustomerId(), event.getChannel(), event.getInteractionType()
            );

            log.info("High-value interaction detected: {} - {} (Score: {})",
                    event.getCustomerId(), event.getInteractionType(), engagement.getEngagementScore());

        } catch (Exception e) {
            log.error("Error handling high-value interaction: {}", event.getCustomerId(), e);
            throw new RuntimeException("Failed to handle high-value interaction", e);
        }
    }

    private boolean isLowEngagement(CustomerEngagementEvent event) {
        return event.getSessionDuration() < 30 || // Less than 30 seconds
               event.getPageViews() <= 1 ||
               event.getAbandonedActions().size() > event.getCompletedActions().size() ||
               event.isErrorEncountered();
    }

    private void handleLowEngagement(CustomerEngagementEvent event, CustomerEngagement engagement) {
        try {
            engagement.setLowEngagement(true);

            // Analyze abandonment reasons
            var abandonmentAnalysis = engagementAnalyticsService.analyzeAbandonmentReasons(
                event.getAbandonedActions(), event.isErrorEncountered()
            );

            engagement.setAbandonmentAnalysis(abandonmentAnalysis);

            // Trigger re-engagement campaigns
            campaignService.triggerReEngagementCampaign(
                event.getCustomerId(), event.getChannel()
            );

            // Create UX improvement alerts
            if (event.isErrorEncountered()) {
                engagementAnalyticsService.createUxImprovementAlert(
                    event.getChannel(), event.getDeviceType(), abandonmentAnalysis
                );
            }

            log.warn("Low engagement detected: {} - Duration: {}s, Errors: {}",
                    event.getCustomerId(), event.getSessionDuration(), event.isErrorEncountered());

        } catch (Exception e) {
            log.error("Error handling low engagement: {}", event.getCustomerId(), e);
            throw new RuntimeException("Failed to handle low engagement", e);
        }
    }

    private void analyzeEngagementPatterns(CustomerEngagementEvent event, CustomerEngagement engagement) {
        try {
            // Get historical engagement patterns
            var patterns = engagementAnalyticsService.getEngagementPatterns(event.getCustomerId());

            // Analyze current engagement against patterns
            EngagementPattern currentPattern = engagementAnalyticsService.analyzeCurrentEngagement(
                event, patterns
            );

            engagement.setEngagementPattern(currentPattern);

            // Check for pattern changes
            if (currentPattern.isPatternChange()) {
                engagementAnalyticsService.handlePatternChange(
                    event.getCustomerId(), currentPattern
                );
            }

            // Identify peak engagement times
            var peakTimes = engagementAnalyticsService.identifyPeakEngagementTimes(
                event.getCustomerId(), event.getTimestamp()
            );

            engagement.setPeakEngagementTimes(peakTimes);

        } catch (Exception e) {
            log.error("Error analyzing engagement patterns: {}", event.getCustomerId(), e);
            // Don't fail the processing for pattern analysis errors
        }
    }

    private void updateCustomerPreferences(CustomerEngagementEvent event, CustomerEngagement engagement) {
        try {
            // Update channel preferences
            personalizationService.updateChannelPreferences(
                event.getCustomerId(), event.getChannel(), engagement.getEngagementScore()
            );

            // Update content preferences
            if (event.getContentInteractions() != null && !event.getContentInteractions().isEmpty()) {
                personalizationService.updateContentPreferences(
                    event.getCustomerId(), event.getContentInteractions()
                );
            }

            // Update feature usage preferences
            if (event.getFeatureUsage() != null && !event.getFeatureUsage().isEmpty()) {
                personalizationService.updateFeaturePreferences(
                    event.getCustomerId(), event.getFeatureUsage()
                );
            }

            // Update timing preferences
            personalizationService.updateTimingPreferences(
                event.getCustomerId(), event.getTimestamp()
            );

        } catch (Exception e) {
            log.error("Error updating customer preferences: {}", event.getCustomerId(), e);
            // Don't fail the processing for preference update errors
        }
    }

    private boolean shouldTriggerPersonalization(CustomerEngagementEvent event, CustomerEngagement engagement) {
        return engagement.getEngagementScore() > 50 && // Engaged customer
               !event.isErrorEncountered() && // No errors
               event.getSessionDuration() > 60 && // Active session
               ENGAGEMENT_CHANNELS.contains(event.getChannel().toUpperCase());
    }

    private void triggerPersonalization(CustomerEngagementEvent event, CustomerEngagement engagement) {
        try {
            // Generate personalized recommendations
            var recommendations = personalizationService.generatePersonalizedRecommendations(
                event.getCustomerId(), event.getChannel(), event.getInteractionType()
            );

            engagement.setPersonalizedRecommendations(recommendations);

            // Send personalized content
            personalizationService.sendPersonalizedContent(
                event.getCustomerId(), event.getChannel(), recommendations
            );

            // Update personalization models
            personalizationService.updatePersonalizationModels(
                event.getCustomerId(), engagement
            );

            log.info("Personalization triggered: {} - {} recommendations",
                    event.getCustomerId(), recommendations.size());

        } catch (Exception e) {
            log.error("Error triggering personalization: {}", event.getCustomerId(), e);
            throw new RuntimeException("Failed to trigger personalization", e);
        }
    }

    private void updateEngagementAnalytics(CustomerEngagementEvent event, CustomerEngagement engagement) {
        try {
            // Update customer engagement metrics
            engagementAnalyticsService.updateCustomerEngagementMetrics(
                event.getCustomerId(), engagement
            );

            // Update channel analytics
            engagementAnalyticsService.updateChannelAnalytics(
                event.getChannel(), engagement
            );

            // Update cohort analytics
            engagementAnalyticsService.updateCohortAnalytics(
                event.getCustomerId(), engagement
            );

            // Update funnel analytics
            engagementAnalyticsService.updateFunnelAnalytics(
                event.getCompletedActions(), event.getAbandonedActions()
            );

        } catch (Exception e) {
            log.error("Error updating engagement analytics: {}", event.getCustomerId(), e);
            // Don't fail the processing for analytics update errors
        }
    }

    private void triggerRealTimeActions(CustomerEngagementEvent event, CustomerEngagement engagement) {
        try {
            // Trigger real-time notifications
            if (engagement.isHighValueInteraction()) {
                campaignService.sendRealTimeNotification(
                    event.getCustomerId(), "HIGH_ENGAGEMENT_DETECTED"
                );
            }

            // Trigger A/B tests
            campaignService.triggerABTests(
                event.getCustomerId(), event.getChannel()
            );

            // Update customer journey
            engagementAnalyticsService.updateCustomerJourney(
                event.getCustomerId(), event.getInteractionType(), engagement
            );

        } catch (Exception e) {
            log.error("Error triggering real-time actions: {}", event.getCustomerId(), e);
            // Don't fail the processing for real-time action errors
        }
    }

    private String determineEngagementLevel(double engagementScore) {
        if (engagementScore >= 80) return "VERY_HIGH";
        if (engagementScore >= 60) return "HIGH";
        if (engagementScore >= 40) return "MEDIUM";
        if (engagementScore >= 20) return "LOW";
        return "VERY_LOW";
    }

    private boolean isAlreadyProcessed(String customerId, String sessionId, String eventId) {
        return customerEngagementRepository.existsByCustomerIdAndSessionIdAndEventId(
            customerId, sessionId, eventId
        );
    }

    private void validateEvent(CustomerEngagementEvent event) {
        if (event.getCustomerId() == null || event.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        if (event.getSessionId() == null || event.getSessionId().trim().isEmpty()) {
            throw new IllegalArgumentException("Session ID cannot be null or empty");
        }
        if (event.getInteractionType() == null || event.getInteractionType().trim().isEmpty()) {
            throw new IllegalArgumentException("Interaction type cannot be null or empty");
        }
        if (event.getChannel() == null || event.getChannel().trim().isEmpty()) {
            throw new IllegalArgumentException("Channel cannot be null or empty");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }
    }

    @Override
    protected void handleProcessingError(String message, Exception error) {
        log.error("Customer engagement processing error: {}", message, error);
        // Additional error handling logic
    }

    @Override
    protected void logProcessingMetrics(String key, long processingTime) {
        log.debug("Processed customer engagement event - Key: {}, Time: {}ms", key, processingTime);
    }
}