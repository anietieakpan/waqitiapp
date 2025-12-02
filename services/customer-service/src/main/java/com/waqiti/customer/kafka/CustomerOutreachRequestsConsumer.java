package com.waqiti.customer.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.customer.service.CustomerOutreachService;
import com.waqiti.customer.service.CampaignManagementService;
import com.waqiti.customer.service.CustomerSegmentationService;
import com.waqiti.customer.service.OutreachTargetingService;
import com.waqiti.customer.service.CustomerEngagementService;
import com.waqiti.customer.service.OutreachMetricsService;
import com.waqiti.customer.entity.Customer;
import com.waqiti.customer.entity.OutreachRequest;
import com.waqiti.customer.entity.CustomerOutreachHistory;
import com.waqiti.customer.domain.OutreachType;
import com.waqiti.customer.domain.OutreachChannel;
import com.waqiti.customer.domain.OutreachStatus;
import com.waqiti.customer.domain.OutreachPriority;
import com.waqiti.customer.domain.CampaignType;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class CustomerOutreachRequestsConsumer {

    private final CustomerOutreachService outreachService;
    private final CampaignManagementService campaignService;
    private final CustomerSegmentationService segmentationService;
    private final OutreachTargetingService targetingService;
    private final CustomerEngagementService engagementService;
    private final OutreachMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("customer_outreach_requests_processed_total")
            .description("Total number of successfully processed customer outreach request events")
            .register(meterRegistry);
        errorCounter = Counter.builder("customer_outreach_requests_errors_total")
            .description("Total number of customer outreach request processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("customer_outreach_requests_processing_duration")
            .description("Time taken to process customer outreach request events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"customer-outreach-requests", "customer-campaign-triggers", "customer-retention-outreach"},
        groupId = "customer-outreach-requests-service-group",
        containerFactory = "customerOutreachKafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "customer-outreach-requests", fallbackMethod = "handleCustomerOutreachRequestEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCustomerOutreachRequestEvent(
            @Payload Map<String, Object> eventData,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String customerId = (String) eventData.get("customerId");
        String eventType = (String) eventData.get("eventType");
        String correlationId = String.format("outreach-%s-p%d-o%d", customerId, partition, offset);
        String eventKey = String.format("%s-%s-%s", customerId, eventType, eventData.get("timestamp"));

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Outreach request event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing customer outreach request: customerId={}, type={}, campaign={}",
                customerId, eventType, eventData.get("campaignId"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (eventType) {
                case "OUTREACH_REQUESTED":
                    processOutreachRequested(eventData, correlationId);
                    break;

                case "CAMPAIGN_TRIGGERED":
                    processCampaignTriggered(eventData, correlationId);
                    break;

                case "RETENTION_OUTREACH":
                    processRetentionOutreach(eventData, correlationId);
                    break;

                case "CROSS_SELL_OUTREACH":
                    processCrossSellOutreach(eventData, correlationId);
                    break;

                case "UPSELL_OUTREACH":
                    processUpsellOutreach(eventData, correlationId);
                    break;

                case "WINBACK_OUTREACH":
                    processWinbackOutreach(eventData, correlationId);
                    break;

                case "OUTREACH_SCHEDULED":
                    scheduleOutreach(eventData, correlationId);
                    break;

                case "OUTREACH_EXECUTED":
                    processOutreachExecuted(eventData, correlationId);
                    break;

                case "OUTREACH_RESPONDED":
                    processOutreachResponse(eventData, correlationId);
                    break;

                case "OUTREACH_COMPLETED":
                    processOutreachCompleted(eventData, correlationId);
                    break;

                case "OUTREACH_CANCELLED":
                    processOutreachCancelled(eventData, correlationId);
                    break;

                default:
                    log.warn("Unknown customer outreach request event type: {}", eventType);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logCustomerEvent("CUSTOMER_OUTREACH_EVENT_PROCESSED", customerId,
                Map.of("eventType", eventType, "campaignId", eventData.get("campaignId"),
                    "outreachType", eventData.get("outreachType"),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process customer outreach request event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("customer-outreach-requests-fallback-events", Map.of(
                "originalEvent", eventData, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCustomerOutreachRequestEventFallback(
            Map<String, Object> eventData,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String customerId = (String) eventData.get("customerId");
        String correlationId = String.format("outreach-fallback-%s-p%d-o%d", customerId, partition, offset);

        log.error("Circuit breaker fallback triggered for customer outreach request: customerId={}, error={}",
            customerId, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("customer-outreach-requests-dlq", Map.of(
            "originalEvent", eventData,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Customer Outreach Request Circuit Breaker Triggered",
                String.format("Customer %s outreach request failed: %s", customerId, ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCustomerOutreachRequestEvent(
            @Payload Map<String, Object> eventData,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String customerId = (String) eventData.get("customerId");
        String correlationId = String.format("dlt-outreach-%s-%d", customerId, System.currentTimeMillis());

        log.error("Dead letter topic handler - Customer outreach request permanently failed: customerId={}, topic={}, error={}",
            customerId, topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logCustomerEvent("CUSTOMER_OUTREACH_DLT_EVENT", customerId,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", eventData.get("eventType"), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Customer Outreach Request Dead Letter Event",
                String.format("Customer %s outreach request sent to DLT: %s", customerId, exceptionMessage),
                Map.of("customerId", customerId, "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        // Check if the entry has expired
        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }

        return true;
    }

    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) { // Only clean when we have many entries
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void processOutreachRequested(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String outreachType = (String) eventData.get("outreachType");
        String campaignId = (String) eventData.get("campaignId");
        String channel = (String) eventData.get("channel");

        // Validate customer eligibility for outreach
        if (!outreachService.isCustomerEligible(customerId, outreachType)) {
            log.info("Customer not eligible for outreach: customerId={}, type={}", customerId, outreachType);
            return;
        }

        // Check frequency limits and suppress if needed
        if (!outreachService.checkFrequencyLimits(customerId, outreachType, channel)) {
            log.info("Outreach suppressed due to frequency limits: customerId={}, type={}", customerId, outreachType);
            metricsService.recordOutreachSuppressed(outreachType, "FREQUENCY_LIMIT");
            return;
        }

        // Create outreach request
        OutreachRequest request = OutreachRequest.builder()
            .customerId(customerId)
            .campaignId(campaignId)
            .outreachType(OutreachType.valueOf(outreachType))
            .channel(OutreachChannel.valueOf(channel))
            .priority(OutreachPriority.valueOf((String) eventData.getOrDefault("priority", "MEDIUM")))
            .status(OutreachStatus.REQUESTED)
            .targetingCriteria((Map<String, Object>) eventData.get("targetingCriteria"))
            .scheduledFor(eventData.containsKey("scheduledFor") ?
                LocalDateTime.parse((String) eventData.get("scheduledFor")) : LocalDateTime.now())
            .correlationId(correlationId)
            .requestedAt(LocalDateTime.now())
            .build();

        outreachService.saveOutreachRequest(request);

        // Send to campaign management for processing
        kafkaTemplate.send("customer-campaign-triggers", Map.of(
            "customerId", customerId,
            "outreachRequestId", request.getId(),
            "campaignId", campaignId,
            "eventType", "CAMPAIGN_TRIGGERED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordOutreachRequested(outreachType, channel);

        log.info("Outreach request processed: customerId={}, type={}, campaign={}",
            customerId, outreachType, campaignId);
    }

    private void processCampaignTriggered(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String campaignId = (String) eventData.get("campaignId");
        String outreachRequestId = (String) eventData.get("outreachRequestId");

        OutreachRequest request = outreachService.getOutreachRequest(outreachRequestId);
        if (request == null) {
            log.error("Outreach request not found: outreachRequestId={}", outreachRequestId);
            return;
        }

        // Get campaign details and personalize content
        var campaign = campaignService.getCampaign(campaignId);
        var customer = outreachService.getCustomer(customerId);

        // Apply targeting and segmentation
        if (!targetingService.isCustomerInTarget(customer, campaign.getTargetingRules())) {
            log.info("Customer not in campaign target: customerId={}, campaignId={}", customerId, campaignId);
            request.setStatus(OutreachStatus.EXCLUDED);
            request.setExclusionReason("TARGET_CRITERIA_NOT_MET");
            outreachService.saveOutreachRequest(request);
            return;
        }

        // Update request status
        request.setStatus(OutreachStatus.APPROVED);
        request.setCampaignDetails(campaign);
        outreachService.saveOutreachRequest(request);

        // Schedule outreach execution
        kafkaTemplate.send("customer-outreach-requests", Map.of(
            "customerId", customerId,
            "outreachRequestId", outreachRequestId,
            "campaignId", campaignId,
            "eventType", "OUTREACH_SCHEDULED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordCampaignTriggered(campaignId, request.getOutreachType().toString());

        log.info("Campaign triggered: customerId={}, campaignId={}, outreachType={}",
            customerId, campaignId, request.getOutreachType());
    }

    private void processRetentionOutreach(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String retentionRisk = (String) eventData.get("retentionRisk");

        // Determine appropriate retention strategy
        String retentionStrategy = segmentationService.getRetentionStrategy(customerId, retentionRisk);

        // Create targeted retention outreach
        Map<String, Object> outreachData = Map.of(
            "customerId", customerId,
            "outreachType", "RETENTION",
            "campaignId", "RETENTION_" + retentionStrategy,
            "channel", determineOptimalChannel(customerId, "RETENTION"),
            "priority", "HIGH",
            "targetingCriteria", Map.of(
                "retentionRisk", retentionRisk,
                "strategy", retentionStrategy
            ),
            "correlationId", correlationId,
            "eventType", "OUTREACH_REQUESTED"
        );

        processOutreachRequested(outreachData, correlationId);

        // Update customer retention status
        engagementService.updateRetentionStatus(customerId, retentionRisk, retentionStrategy);

        log.info("Retention outreach processed: customerId={}, risk={}, strategy={}",
            customerId, retentionRisk, retentionStrategy);
    }

    private void processCrossSellOutreach(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        List<String> recommendedProducts = (List<String>) eventData.get("recommendedProducts");

        // Validate cross-sell opportunity
        var customer = outreachService.getCustomer(customerId);
        var eligibleProducts = targetingService.filterEligibleProducts(customer, recommendedProducts);

        if (eligibleProducts.isEmpty()) {
            log.info("No eligible cross-sell products for customer: customerId={}", customerId);
            return;
        }

        // Create personalized cross-sell campaign
        String campaignId = campaignService.createCrossSellCampaign(customerId, eligibleProducts);

        Map<String, Object> outreachData = Map.of(
            "customerId", customerId,
            "outreachType", "CROSS_SELL",
            "campaignId", campaignId,
            "channel", determineOptimalChannel(customerId, "CROSS_SELL"),
            "priority", "MEDIUM",
            "targetingCriteria", Map.of(
                "recommendedProducts", eligibleProducts,
                "crossSellScore", eventData.get("crossSellScore")
            ),
            "correlationId", correlationId,
            "eventType", "OUTREACH_REQUESTED"
        );

        processOutreachRequested(outreachData, correlationId);

        log.info("Cross-sell outreach processed: customerId={}, products={}",
            customerId, eligibleProducts);
    }

    private void processUpsellOutreach(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String currentProduct = (String) eventData.get("currentProduct");
        String recommendedUpgrade = (String) eventData.get("recommendedUpgrade");

        // Validate upsell eligibility
        if (!targetingService.isUpsellEligible(customerId, currentProduct, recommendedUpgrade)) {
            log.info("Customer not eligible for upsell: customerId={}, upgrade={}", customerId, recommendedUpgrade);
            return;
        }

        String campaignId = campaignService.createUpsellCampaign(customerId, currentProduct, recommendedUpgrade);

        Map<String, Object> outreachData = Map.of(
            "customerId", customerId,
            "outreachType", "UPSELL",
            "campaignId", campaignId,
            "channel", determineOptimalChannel(customerId, "UPSELL"),
            "priority", "MEDIUM",
            "targetingCriteria", Map.of(
                "currentProduct", currentProduct,
                "recommendedUpgrade", recommendedUpgrade,
                "upsellScore", eventData.get("upsellScore")
            ),
            "correlationId", correlationId,
            "eventType", "OUTREACH_REQUESTED"
        );

        processOutreachRequested(outreachData, correlationId);

        log.info("Upsell outreach processed: customerId={}, upgrade={}", customerId, recommendedUpgrade);
    }

    private void processWinbackOutreach(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String churnReason = (String) eventData.get("churnReason");
        Integer daysSinceChurn = (Integer) eventData.get("daysSinceChurn");

        // Determine winback strategy based on churn reason and time elapsed
        String winbackStrategy = segmentationService.getWinbackStrategy(customerId, churnReason, daysSinceChurn);

        if (winbackStrategy == null) {
            log.info("No suitable winback strategy for customer: customerId={}, reason={}",
                customerId, churnReason);
            return;
        }

        String campaignId = campaignService.createWinbackCampaign(customerId, churnReason, winbackStrategy);

        Map<String, Object> outreachData = Map.of(
            "customerId", customerId,
            "outreachType", "WINBACK",
            "campaignId", campaignId,
            "channel", determineOptimalChannel(customerId, "WINBACK"),
            "priority", "HIGH",
            "targetingCriteria", Map.of(
                "churnReason", churnReason,
                "daysSinceChurn", daysSinceChurn,
                "winbackStrategy", winbackStrategy
            ),
            "correlationId", correlationId,
            "eventType", "OUTREACH_REQUESTED"
        );

        processOutreachRequested(outreachData, correlationId);

        log.info("Winback outreach processed: customerId={}, strategy={}", customerId, winbackStrategy);
    }

    private void scheduleOutreach(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String outreachRequestId = (String) eventData.get("outreachRequestId");

        OutreachRequest request = outreachService.getOutreachRequest(outreachRequestId);
        if (request == null) {
            log.error("Outreach request not found: outreachRequestId={}", outreachRequestId);
            return;
        }

        request.setStatus(OutreachStatus.SCHEDULED);
        request.setScheduledAt(LocalDateTime.now());
        outreachService.saveOutreachRequest(request);

        // Determine optimal delivery time
        LocalDateTime optimalTime = engagementService.getOptimalOutreachTime(customerId, request.getChannel());

        if (optimalTime.isAfter(LocalDateTime.now())) {
            outreachService.scheduleOutreachExecution(outreachRequestId, optimalTime);
        } else {
            // Execute immediately
            kafkaTemplate.send("customer-outreach-requests", Map.of(
                "customerId", customerId,
                "outreachRequestId", outreachRequestId,
                "eventType", "OUTREACH_EXECUTED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Outreach scheduled: customerId={}, outreachRequestId={}, executionTime={}",
            customerId, outreachRequestId, optimalTime);
    }

    private void processOutreachExecuted(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String outreachRequestId = (String) eventData.get("outreachRequestId");

        OutreachRequest request = outreachService.getOutreachRequest(outreachRequestId);
        if (request == null) {
            log.error("Outreach request not found: outreachRequestId={}", outreachRequestId);
            return;
        }

        request.setStatus(OutreachStatus.EXECUTED);
        request.setExecutedAt(LocalDateTime.now());
        outreachService.saveOutreachRequest(request);

        // Record outreach history
        CustomerOutreachHistory history = CustomerOutreachHistory.builder()
            .customerId(customerId)
            .outreachRequestId(outreachRequestId)
            .campaignId(request.getCampaignId())
            .outreachType(request.getOutreachType())
            .channel(request.getChannel())
            .executedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        outreachService.saveOutreachHistory(history);

        metricsService.recordOutreachExecuted(
            request.getOutreachType().toString(),
            request.getChannel().toString());

        log.info("Outreach executed: customerId={}, outreachRequestId={}", customerId, outreachRequestId);
    }

    private void processOutreachResponse(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String outreachRequestId = (String) eventData.get("outreachRequestId");
        String responseType = (String) eventData.get("responseType");
        Map<String, Object> responseData = (Map<String, Object>) eventData.get("responseData");

        OutreachRequest request = outreachService.getOutreachRequest(outreachRequestId);
        if (request != null) {
            request.setResponseType(responseType);
            request.setResponseData(responseData);
            request.setRespondedAt(LocalDateTime.now());
            outreachService.saveOutreachRequest(request);
        }

        // Process response based on type
        switch (responseType) {
            case "INTERESTED":
                processInterestedResponse(customerId, request, responseData, correlationId);
                break;
            case "NOT_INTERESTED":
                processNotInterestedResponse(customerId, request, responseData, correlationId);
                break;
            case "REQUEST_INFO":
                processInfoRequest(customerId, request, responseData, correlationId);
                break;
            case "UNSUBSCRIBE":
                processUnsubscribeResponse(customerId, request, correlationId);
                break;
        }

        // Update engagement scoring
        engagementService.recordOutreachResponse(customerId, outreachRequestId, responseType);
        metricsService.recordOutreachResponse(request.getOutreachType().toString(), responseType);

        log.info("Outreach response processed: customerId={}, outreachRequestId={}, response={}",
            customerId, outreachRequestId, responseType);
    }

    private void processOutreachCompleted(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String outreachRequestId = (String) eventData.get("outreachRequestId");
        String outcome = (String) eventData.get("outcome");

        OutreachRequest request = outreachService.getOutreachRequest(outreachRequestId);
        if (request != null) {
            request.setStatus(OutreachStatus.COMPLETED);
            request.setOutcome(outcome);
            request.setCompletedAt(LocalDateTime.now());
            outreachService.saveOutreachRequest(request);

            metricsService.recordOutreachCompleted(
                request.getOutreachType().toString(),
                request.getChannel().toString(),
                outcome);
        }

        log.info("Outreach completed: customerId={}, outreachRequestId={}, outcome={}",
            customerId, outreachRequestId, outcome);
    }

    private void processOutreachCancelled(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String outreachRequestId = (String) eventData.get("outreachRequestId");
        String cancellationReason = (String) eventData.get("cancellationReason");

        OutreachRequest request = outreachService.getOutreachRequest(outreachRequestId);
        if (request != null) {
            request.setStatus(OutreachStatus.CANCELLED);
            request.setCancellationReason(cancellationReason);
            request.setCancelledAt(LocalDateTime.now());
            outreachService.saveOutreachRequest(request);

            metricsService.recordOutreachCancelled(
                request.getOutreachType().toString(), cancellationReason);
        }

        log.info("Outreach cancelled: customerId={}, outreachRequestId={}, reason={}",
            customerId, outreachRequestId, cancellationReason);
    }

    private String determineOptimalChannel(String customerId, String outreachType) {
        // Logic to determine the best channel based on customer preferences and engagement history
        var preferences = engagementService.getCustomerChannelPreferences(customerId);
        var engagement = engagementService.getChannelEngagementRates(customerId);

        return targetingService.selectOptimalChannel(preferences, engagement, outreachType);
    }

    private void processInterestedResponse(String customerId, OutreachRequest request,
            Map<String, Object> responseData, String correlationId) {
        // Customer showed interest - trigger follow-up workflow
        kafkaTemplate.send("customer-lead-management", Map.of(
            "customerId", customerId,
            "leadSource", "OUTREACH_RESPONSE",
            "outreachType", request.getOutreachType().toString(),
            "campaignId", request.getCampaignId(),
            "responseData", responseData,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void processNotInterestedResponse(String customerId, OutreachRequest request,
            Map<String, Object> responseData, String correlationId) {
        // Update customer preferences to reduce similar outreach
        String suppressionPeriod = (String) responseData.getOrDefault("suppressionPeriod", "30_DAYS");
        outreachService.addOutreachSuppression(customerId, request.getOutreachType().toString(), suppressionPeriod);
    }

    private void processInfoRequest(String customerId, OutreachRequest request,
            Map<String, Object> responseData, String correlationId) {
        // Customer requested more information - send to content delivery
        kafkaTemplate.send("customer-content-delivery", Map.of(
            "customerId", customerId,
            "contentType", "PRODUCT_INFORMATION",
            "requestSource", "OUTREACH_RESPONSE",
            "requestedInfo", responseData.get("requestedInfo"),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void processUnsubscribeResponse(String customerId, OutreachRequest request, String correlationId) {
        // Process unsubscribe request
        outreachService.processUnsubscribeRequest(customerId, request.getOutreachType().toString());

        // Update communication preferences
        kafkaTemplate.send("customer-communication-preferences", Map.of(
            "customerId", customerId,
            "action", "UNSUBSCRIBE",
            "outreachType", request.getOutreachType().toString(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }
}