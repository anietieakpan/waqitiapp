package com.waqiti.analytics.kafka;

import com.waqiti.common.kafka.RetryableKafkaListener;
import com.waqiti.analytics.dto.BusinessTransactionEvent;
import com.waqiti.analytics.service.BusinessAnalyticsService;
import com.waqiti.analytics.service.RevenueAnalyticsService;
import com.waqiti.common.exception.KafkaRetryException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Business Transaction Events Consumer
 *
 * PURPOSE: Real-time analytics for business account transactions
 *
 * BUSINESS CRITICAL: Business accounts represent 40% of revenue ($8M-12M annually)
 * Missing this consumer means:
 * - No business analytics dashboard
 * - No revenue reporting for merchants
 * - Failed merchant onboarding (no transaction visibility)
 * - Lost merchant accounts to competitors
 *
 * IMPLEMENTATION PRIORITY: P0 CRITICAL
 *
 * @author Waqiti Analytics Team
 * @version 1.0.0
 * @since 2025-10-13
 */
@Service
@Slf4j
public class BusinessTransactionEventsConsumer {

    private final BusinessAnalyticsService analyticsService;
    private final RevenueAnalyticsService revenueService;
    private final Counter transactionsAnalyzedCounter;
    private final Counter transactionsFailedCounter;

    @Autowired
    public BusinessTransactionEventsConsumer(
            BusinessAnalyticsService analyticsService,
            RevenueAnalyticsService revenueService,
            MeterRegistry meterRegistry) {

        this.analyticsService = analyticsService;
        this.revenueService = revenueService;

        this.transactionsAnalyzedCounter = Counter.builder("business.transactions.analyzed")
                .description("Number of business transactions analyzed")
                .register(meterRegistry);

        this.transactionsFailedCounter = Counter.builder("business.transactions.failed")
                .description("Number of business transaction analytics that failed")
                .register(meterRegistry);
    }

    /**
     * Process business transaction event for analytics
     */
    @RetryableKafkaListener(
        topics = "business-transaction-events",
        groupId = "analytics-service-business-transactions",
        containerFactory = "kafkaListenerContainerFactory",
        retries = 5,
        backoffMultiplier = 2.0,
        initialBackoff = 1000L
    )
    @Transactional
    public void handleBusinessTransactionEvent(
            @Payload BusinessTransactionEvent event,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {

        Instant startTime = Instant.now();

        log.info("Processing business transaction analytics: transactionId={}, merchantId={}, amount={}",
                event.getTransactionId(),
                event.getMerchantId(),
                event.getAmount());

        try {
            // Step 1: Validate event
            validateEvent(event);

            // Step 2: Check idempotency
            if (analyticsService.isTransactionAlreadyAnalyzed(event.getTransactionId())) {
                log.info("Business transaction already analyzed (idempotent): transactionId={}",
                        event.getTransactionId());
                acknowledgment.acknowledge();
                return;
            }

            // Step 3: Update real-time analytics metrics
            analyticsService.updateTransactionMetrics(event);

            // Step 4: Update merchant revenue totals (daily/monthly/yearly)
            revenueService.updateMerchantRevenue(
                    event.getMerchantId(),
                    event.getAmount(),
                    event.getCurrency(),
                    event.getTransactionDate()
            );

            // Step 5: Update category analytics (e.g., retail, food, entertainment)
            analyticsService.updateCategoryAnalytics(
                    event.getMerchantCategory(),
                    event.getAmount(),
                    event.getTransactionDate()
            );

            // Step 6: Update transaction velocity metrics
            analyticsService.updateTransactionVelocity(
                    event.getMerchantId(),
                    event.getTransactionDate()
            );

            // Step 7: Calculate fees and commissions
            revenueService.calculatePlatformRevenue(event);

            // Step 8: Update merchant dashboard cache (Redis)
            analyticsService.updateMerchantDashboardCache(
                    event.getMerchantId(),
                    event
            );

            // Step 9: Check for anomalies (fraud detection)
            if (analyticsService.detectAnomalousPattern(event)) {
                log.warn("Anomalous business transaction detected: transactionId={}, merchantId={}",
                        event.getTransactionId(), event.getMerchantId());
                analyticsService.triggerMerchantReview(event);
            }

            // Step 10: Mark as analyzed
            analyticsService.markTransactionAnalyzed(event.getTransactionId());

            // Step 11: Acknowledge
            acknowledgment.acknowledge();

            // Metrics
            transactionsAnalyzedCounter.increment();

            long processingTime = Instant.now().toEpochMilli() - startTime.toEpochMilli();
            log.info("Business transaction analytics completed: transactionId={}, processingTime={}ms",
                    event.getTransactionId(), processingTime);

        } catch (Exception e) {
            log.error("Failed to process business transaction analytics: transactionId={}, will retry",
                    event.getTransactionId(), e);

            transactionsFailedCounter.increment();

            throw new KafkaRetryException(
                    "Failed to process business transaction analytics",
                    e,
                    event.getTransactionId().toString()
            );
        }
    }

    /**
     * Validate event
     */
    private void validateEvent(BusinessTransactionEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        if (event.getTransactionId() == null) {
            throw new IllegalArgumentException("Transaction ID cannot be null");
        }

        if (event.getMerchantId() == null) {
            throw new IllegalArgumentException("Merchant ID cannot be null");
        }

        if (event.getAmount() == null || event.getAmount().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must be non-negative");
        }

        if (event.getTransactionDate() == null) {
            throw new IllegalArgumentException("Transaction date cannot be null");
        }
    }

    /**
     * Handle DLQ messages
     */
    @KafkaListener(topics = "business-transaction-events-analytics-service-dlq")
    public void handleDLQMessage(@Payload BusinessTransactionEvent event) {
        log.error("Business transaction analytics in DLQ - merchant dashboard outdated: transactionId={}, merchantId={}",
                event.getTransactionId(), event.getMerchantId());

        try {
            // Log to persistent storage
            analyticsService.logDLQTransaction(
                    event.getTransactionId(),
                    event,
                    "Business transaction analytics failed permanently"
            );

            // Alert analytics team (MEDIUM priority - doesn't affect transaction processing)
            analyticsService.alertAnalyticsTeam(
                    "MEDIUM",
                    "Business transaction analytics stuck in DLQ - merchant reporting gap",
                    java.util.Map.of(
                            "transactionId", event.getTransactionId().toString(),
                            "merchantId", event.getMerchantId().toString(),
                            "amount", event.getAmount().toString(),
                            "transactionDate", event.getTransactionDate().toString()
                    )
            );

            // Create manual reconciliation task
            analyticsService.createManualReconciliationTask(event);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to process business analytics DLQ message: transactionId={}",
                    event.getTransactionId(), e);
        }
    }
}
