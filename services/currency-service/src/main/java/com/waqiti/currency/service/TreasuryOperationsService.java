package com.waqiti.currency.service;

import com.waqiti.currency.model.TreasuryOperationsReview;
import com.waqiti.currency.model.ReviewStatus;
import com.waqiti.currency.model.Priority;
import com.waqiti.currency.repository.TreasuryOperationsReviewRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Treasury Operations Service
 *
 * Handles treasury escalations and operations review:
 * - Failed conversion escalation
 * - Treasury team alerts
 * - Operations review queue management
 * - Financial impact assessment
 * - Refund processing coordination
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TreasuryOperationsService {

    private final TreasuryOperationsReviewRepository reviewRepository;
    private final MeterRegistry meterRegistry;

    private static final BigDecimal HIGH_VALUE_THRESHOLD = BigDecimal.valueOf(10000);
    private static final BigDecimal CRITICAL_VALUE_THRESHOLD = BigDecimal.valueOf(50000);

    /**
     * Escalate failed conversion to treasury operations
     */
    @Async
    public void escalate(String conversionId, String customerId, String sourceCurrency,
                        String targetCurrency, BigDecimal amount, String failureReason,
                        String correlationId) {

        log.info("Escalating conversion to treasury operations: conversionId={} customer={} " +
                "{}→{} amount={} reason={} correlationId={}",
                conversionId, customerId, sourceCurrency, targetCurrency, amount,
                failureReason, correlationId);

        try {
            // Determine priority based on amount
            Priority priority = determinePriority(amount);

            // Create review item
            TreasuryOperationsReview review = TreasuryOperationsReview.builder()
                    .conversionId(conversionId)
                    .customerId(customerId)
                    .sourceCurrency(sourceCurrency)
                    .targetCurrency(targetCurrency)
                    .amount(amount)
                    .failureReason(failureReason)
                    .status(ReviewStatus.PENDING_TREASURY_TEAM)
                    .priority(priority)
                    .escalatedAt(Instant.now())
                    .correlationId(correlationId)
                    .build();

            reviewRepository.save(review);

            incrementCounter("currency.treasury.escalation.created");

            // Send alert to treasury team
            sendTreasuryTeamAlert(conversionId, customerId, sourceCurrency, targetCurrency,
                    amount, failureReason, priority, correlationId);

            log.info("Treasury escalation created: conversionId={} priority={} correlationId={}",
                    conversionId, priority, correlationId);

        } catch (Exception e) {
            log.error("Failed to escalate to treasury: conversionId={} correlationId={}",
                    conversionId, correlationId, e);
            incrementCounter("currency.treasury.escalation.error");
        }
    }

    /**
     * Send alert to treasury team
     */
    @Async
    public void sendTreasuryTeamAlert(String conversionId, String customerId,
                                     String sourceCurrency, String targetCurrency,
                                     BigDecimal amount, String failureReason,
                                     Priority priority, String correlationId) {

        log.info("Sending treasury team alert: conversionId={} priority={} amount={} correlationId={}",
                conversionId, priority, amount, correlationId);

        try {
            String alertMessage = buildTreasuryAlert(conversionId, customerId, sourceCurrency,
                    targetCurrency, amount, failureReason, priority);

            // In production, send via Slack, email, or incident management system
            log.warn("TREASURY ALERT [{}]: {}", priority, alertMessage);

            incrementCounter("currency.treasury.alert.sent");

        } catch (Exception e) {
            log.error("Failed to send treasury alert: conversionId={} correlationId={}",
                    conversionId, correlationId, e);
            incrementCounter("currency.treasury.alert.error");
        }
    }

    /**
     * Create operations review for DLT events
     */
    public void createOperationsReview(String conversionId, String customerId,
                                      String sourceCurrency, String targetCurrency,
                                      BigDecimal amount, String eventData,
                                      String correlationId) {

        log.info("Creating treasury operations review: conversionId={} customer={} {}→{} correlationId={}",
                conversionId, customerId, sourceCurrency, targetCurrency, correlationId);

        try {
            Priority priority = amount.compareTo(CRITICAL_VALUE_THRESHOLD) >= 0
                    ? Priority.CRITICAL
                    : Priority.HIGH;

            TreasuryOperationsReview review = TreasuryOperationsReview.builder()
                    .conversionId(conversionId)
                    .customerId(customerId)
                    .sourceCurrency(sourceCurrency)
                    .targetCurrency(targetCurrency)
                    .amount(amount)
                    .failureReason("DLT_EVENT_REQUIRES_MANUAL_REVIEW")
                    .status(ReviewStatus.PENDING_TREASURY_TEAM)
                    .priority(priority)
                    .escalatedAt(Instant.now())
                    .eventData(eventData)
                    .correlationId(correlationId)
                    .build();

            reviewRepository.save(review);

            incrementCounter("currency.treasury.review.created");

            log.info("Treasury operations review created: conversionId={} priority={} correlationId={}",
                    conversionId, priority, correlationId);

        } catch (Exception e) {
            log.error("Failed to create operations review: conversionId={} correlationId={}",
                    conversionId, correlationId, e);
            incrementCounter("currency.treasury.review.error");
        }
    }

    /**
     * Assess financial impact of conversion failure
     */
    public FinancialImpact assessFinancialImpact(String conversionId, String customerId,
                                                BigDecimal amount, String sourceCurrency,
                                                String targetCurrency, String correlationId) {

        log.info("Assessing financial impact: conversionId={} amount={} {} correlationId={}",
                conversionId, amount, sourceCurrency, correlationId);

        try {
            // Determine impact level
            ImpactLevel impactLevel;
            if (amount.compareTo(CRITICAL_VALUE_THRESHOLD) >= 0) {
                impactLevel = ImpactLevel.CRITICAL;
            } else if (amount.compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
                impactLevel = ImpactLevel.HIGH;
            } else if (amount.compareTo(BigDecimal.valueOf(1000)) >= 0) {
                impactLevel = ImpactLevel.MEDIUM;
            } else {
                impactLevel = ImpactLevel.LOW;
            }

            // Calculate potential losses (spread, opportunity cost)
            BigDecimal potentialSpread = amount.multiply(BigDecimal.valueOf(0.0025));
            BigDecimal opportunityCost = amount.multiply(BigDecimal.valueOf(0.0001)); // Daily cost

            FinancialImpact impact = FinancialImpact.builder()
                    .conversionId(conversionId)
                    .impactLevel(impactLevel)
                    .impactedAmount(amount)
                    .potentialSpreadLoss(potentialSpread)
                    .opportunityCost(opportunityCost)
                    .currency(sourceCurrency)
                    .requiresImmediateAction(impactLevel == ImpactLevel.CRITICAL)
                    .assessedAt(Instant.now())
                    .build();

            incrementCounter("currency.treasury.impact.assessed");

            log.info("Financial impact assessed: conversionId={} level={} amount={} correlationId={}",
                    conversionId, impactLevel, amount, correlationId);

            return impact;

        } catch (Exception e) {
            log.error("Failed to assess financial impact: conversionId={} correlationId={}",
                    conversionId, correlationId, e);
            incrementCounter("currency.treasury.impact.error");

            return FinancialImpact.builder()
                    .conversionId(conversionId)
                    .impactLevel(ImpactLevel.UNKNOWN)
                    .build();
        }
    }

    /**
     * Get pending reviews for treasury team
     */
    public List<TreasuryOperationsReview> getPendingReviews(String correlationId) {
        log.info("Retrieving pending treasury reviews: correlationId={}", correlationId);
        return reviewRepository.findPendingReviews(correlationId);
    }

    /**
     * Mark review as resolved
     */
    public void resolveReview(String conversionId, String resolution, String resolvedBy,
                             String correlationId) {

        log.info("Resolving treasury review: conversionId={} resolvedBy={} correlationId={}",
                conversionId, resolvedBy, correlationId);

        try {
            reviewRepository.updateStatus(conversionId, ReviewStatus.RESOLVED, correlationId);
            incrementCounter("currency.treasury.review.resolved");

            log.info("Treasury review resolved: conversionId={} resolution={} correlationId={}",
                    conversionId, resolution, correlationId);

        } catch (Exception e) {
            log.error("Failed to resolve review: conversionId={} correlationId={}",
                    conversionId, correlationId, e);
            incrementCounter("currency.treasury.review.resolve_error");
        }
    }

    /**
     * Determine priority based on amount
     */
    private Priority determinePriority(BigDecimal amount) {
        if (amount.compareTo(CRITICAL_VALUE_THRESHOLD) >= 0) {
            return Priority.CRITICAL;
        } else if (amount.compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
            return Priority.HIGH;
        } else if (amount.compareTo(BigDecimal.valueOf(1000)) >= 0) {
            return Priority.MEDIUM;
        } else {
            return Priority.LOW;
        }
    }

    /**
     * Build treasury alert message
     */
    private String buildTreasuryAlert(String conversionId, String customerId,
                                     String sourceCurrency, String targetCurrency,
                                     BigDecimal amount, String failureReason,
                                     Priority priority) {

        return String.format(
            "🚨 TREASURY ESCALATION [%s]\n\n" +
            "Conversion ID: %s\n" +
            "Customer ID: %s\n" +
            "Currency Pair: %s → %s\n" +
            "Amount: %s %s\n" +
            "Failure Reason: %s\n\n" +
            "Action Required: Review and resolve failed currency conversion.\n" +
            "Refund may be necessary if conversion cannot be recovered.",
            priority, conversionId, customerId, sourceCurrency, targetCurrency,
            amount, sourceCurrency, failureReason
        );
    }

    /**
     * Increment counter metric
     */
    private void incrementCounter(String metricName) {
        Counter.builder(metricName)
                .register(meterRegistry)
                .increment();
    }

    // Inner classes

    @lombok.Data
    @lombok.Builder
    public static class FinancialImpact {
        private String conversionId;
        private ImpactLevel impactLevel;
        private BigDecimal impactedAmount;
        private BigDecimal potentialSpreadLoss;
        private BigDecimal opportunityCost;
        private String currency;
        private boolean requiresImmediateAction;
        private Instant assessedAt;
    }

    public enum ImpactLevel {
        LOW, MEDIUM, HIGH, CRITICAL, UNKNOWN
    }
}
