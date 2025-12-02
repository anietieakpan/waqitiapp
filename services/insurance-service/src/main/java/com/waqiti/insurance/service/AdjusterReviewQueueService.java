package com.waqiti.insurance.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Adjuster Review Queue Service
 * Manages adjuster review queue for insurance claims requiring human review
 * Production-ready implementation with SLA tracking and workload monitoring
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdjusterReviewQueueService {

    private final InsuranceRegulatoryAlertService regulatoryAlertService;
    private final MeterRegistry meterRegistry;

    // In-memory queue (for production, use database-backed queue)
    private final Map<String, AdjusterReviewRequest> reviewQueue = new ConcurrentHashMap<>();

    /**
     * Add claim to adjuster review queue
     */
    public void add(AdjusterReviewRequest request) {
        log.info("Adding claim to adjuster review queue: claimId={} policyId={} priority={} correlationId={}",
                request.getClaimId(), request.getPolicyId(), request.getPriority(), request.getCorrelationId());

        reviewQueue.put(request.getClaimId(), request);

        recordMetric("insurance_adjuster_review_queued_total",
                "priority", request.getPriority().toString(),
                "claim_type", request.getClaimType());

        log.info("Claim added to adjuster review queue: claimId={} queueSize={} correlationId={}",
                request.getClaimId(), reviewQueue.size(), request.getCorrelationId());
    }

    /**
     * Get pending reviews
     */
    public List<AdjusterReviewRequest> getPendingReviews() {
        return new ArrayList<>(reviewQueue.values());
    }

    /**
     * Get high priority reviews
     */
    public List<AdjusterReviewRequest> getHighPriorityReviews() {
        return reviewQueue.values().stream()
                .filter(r -> r.getPriority() == Priority.HIGH || r.getPriority() == Priority.CRITICAL)
                .collect(Collectors.toList());
    }

    /**
     * Get overdue reviews
     */
    public List<AdjusterReviewRequest> getOverdueReviews() {
        Instant now = Instant.now();
        return reviewQueue.values().stream()
                .filter(r -> r.getDeadline().isBefore(now))
                .collect(Collectors.toList());
    }

    /**
     * Get review by claim ID
     */
    public AdjusterReviewRequest getReview(String claimId) {
        return reviewQueue.get(claimId);
    }

    /**
     * Remove review from queue (after completion)
     */
    public void remove(String claimId, String correlationId) {
        log.info("Removing claim from adjuster review queue: claimId={} correlationId={}",
                claimId, correlationId);

        AdjusterReviewRequest removed = reviewQueue.remove(claimId);

        if (removed != null) {
            recordMetric("insurance_adjuster_review_completed_total",
                    "priority", removed.getPriority().toString());

            log.info("Claim removed from adjuster review queue: claimId={} queueSize={} correlationId={}",
                    claimId, reviewQueue.size(), correlationId);
        } else {
            log.warn("Claim not found in adjuster review queue: claimId={} correlationId={}",
                    claimId, correlationId);
        }
    }

    /**
     * Get queue size
     */
    public int getQueueSize() {
        return reviewQueue.size();
    }

    /**
     * Check if queue has overdue items
     */
    public boolean hasOverdueItems() {
        return !getOverdueReviews().isEmpty();
    }

    /**
     * Get reviews by specialty
     */
    public List<AdjusterReviewRequest> getReviewsBySpecialty(String specialty) {
        return reviewQueue.values().stream()
                .filter(r -> specialty.equals(r.getSpecialtyRequired()))
                .collect(Collectors.toList());
    }

    /**
     * Monitor queue and send alerts for overdue reviews
     * Runs every hour
     */
    @Scheduled(cron = "0 0 * * * *")
    public void monitorQueueAndAlert() {
        log.debug("Monitoring adjuster review queue: queueSize={}", reviewQueue.size());

        List<AdjusterReviewRequest> overdueReviews = getOverdueReviews();

        if (!overdueReviews.isEmpty()) {
            log.warn("Found {} overdue adjuster reviews", overdueReviews.size());

            // Alert insurance operations about overdue reviews
            regulatoryAlertService.alertAdjusterBacklog(
                    reviewQueue.size(),
                    overdueReviews.size(),
                    "adjuster-review-monitor"
            );

            recordMetric("insurance_adjuster_review_overdue_total");
        }

        // Alert for large queue size (>20 items)
        if (reviewQueue.size() > 20) {
            log.warn("Adjuster review queue is large: queueSize={}", reviewQueue.size());

            regulatoryAlertService.alertAdjusterBacklog(
                    reviewQueue.size(),
                    overdueReviews.size(),
                    "adjuster-review-monitor"
            );
        }

        // Update queue size metric
        Counter.builder("insurance_adjuster_review_queue_size")
                .tag("has_overdue", String.valueOf(!overdueReviews.isEmpty()))
                .register(meterRegistry)
                .increment(reviewQueue.size());
    }

    /**
     * Get workload metrics
     */
    public WorkloadMetrics getWorkloadMetrics() {
        List<AdjusterReviewRequest> all = getPendingReviews();
        List<AdjusterReviewRequest> overdue = getOverdueReviews();

        long totalInQueue = all.size();
        long overdueCount = overdue.size();
        double slaCompliance = totalInQueue > 0
                ? ((totalInQueue - overdueCount) * 100.0 / totalInQueue)
                : 100.0;

        // Calculate average claim amount
        double avgClaimAmount = all.stream()
                .map(AdjusterReviewRequest::getClaimAmount)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0.0);

        return WorkloadMetrics.builder()
                .totalInQueue(totalInQueue)
                .overdueCount(overdueCount)
                .slaComplianceRate(slaCompliance)
                .highPriorityCount(getHighPriorityReviews().size())
                .averageClaimAmount(BigDecimal.valueOf(avgClaimAmount))
                .build();
    }

    private void recordMetric(String metricName, String... tags) {
        Counter.Builder builder = Counter.builder(metricName);

        for (int i = 0; i < tags.length; i += 2) {
            if (i + 1 < tags.length) {
                builder.tag(tags[i], tags[i + 1]);
            }
        }

        builder.register(meterRegistry).increment();
    }

    // Inner classes

    @Data
    @Builder
    public static class AdjusterReviewRequest {
        private String claimId;
        private String policyId;
        private String policyHolderId;
        private String claimType;
        private BigDecimal claimAmount;
        private String reviewReason;
        private String correlationId;
        private Priority priority;
        private String assignedAdjuster;
        private String specialtyRequired;
        private Instant deadline;
        private Instant createdAt;
    }

    public enum Priority {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    @Data
    @Builder
    public static class WorkloadMetrics {
        private long totalInQueue;
        private long overdueCount;
        private double slaComplianceRate;
        private long highPriorityCount;
        private BigDecimal averageClaimAmount;
    }
}
