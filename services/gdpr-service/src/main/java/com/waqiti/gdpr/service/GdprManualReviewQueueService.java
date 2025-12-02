package com.waqiti.gdpr.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * GDPR Manual Review Queue Service
 * Manages manual review queue for data export requests requiring human review
 * Production-ready implementation with SLA tracking and escalation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GdprManualReviewQueueService {

    private final DataProtectionOfficerAlertService dpoAlertService;
    private final MeterRegistry meterRegistry;

    // In-memory queue (for production, use database-backed queue)
    private final Map<String, GdprManualReviewRequest> reviewQueue = new ConcurrentHashMap<>();

    /**
     * Add request to manual review queue
     */
    public void add(GdprManualReviewRequest request) {
        log.info("Adding request to manual review queue: exportId={} subjectId={} priority={} correlationId={}",
                request.getExportId(), request.getSubjectId(), request.getPriority(), request.getCorrelationId());

        reviewQueue.put(request.getExportId(), request);

        recordMetric("gdpr_manual_review_queued_total",
                "priority", request.getPriority().toString(),
                "request_type", request.getRequestType());

        log.info("Request added to manual review queue: exportId={} queueSize={} correlationId={}",
                request.getExportId(), reviewQueue.size(), request.getCorrelationId());
    }

    /**
     * Get pending review requests
     */
    public List<GdprManualReviewRequest> getPendingReviews() {
        return new ArrayList<>(reviewQueue.values());
    }

    /**
     * Get high priority pending reviews
     */
    public List<GdprManualReviewRequest> getHighPriorityReviews() {
        return reviewQueue.values().stream()
                .filter(r -> r.getPriority() == Priority.HIGH || r.getPriority() == Priority.CRITICAL)
                .collect(Collectors.toList());
    }

    /**
     * Get overdue reviews
     */
    public List<GdprManualReviewRequest> getOverdueReviews() {
        Instant now = Instant.now();
        return reviewQueue.values().stream()
                .filter(r -> r.getDeadline().isBefore(now))
                .collect(Collectors.toList());
    }

    /**
     * Get review request by export ID
     */
    public GdprManualReviewRequest getReview(String exportId) {
        return reviewQueue.get(exportId);
    }

    /**
     * Remove review from queue (after completion)
     */
    public void remove(String exportId, String correlationId) {
        log.info("Removing request from manual review queue: exportId={} correlationId={}",
                exportId, correlationId);

        GdprManualReviewRequest removed = reviewQueue.remove(exportId);

        if (removed != null) {
            recordMetric("gdpr_manual_review_completed_total",
                    "priority", removed.getPriority().toString());

            log.info("Request removed from manual review queue: exportId={} queueSize={} correlationId={}",
                    exportId, reviewQueue.size(), correlationId);
        } else {
            log.warn("Request not found in manual review queue: exportId={} correlationId={}",
                    exportId, correlationId);
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
     * Monitor queue and send alerts for overdue reviews
     * Runs every hour
     */
    @Scheduled(cron = "0 0 * * * *")
    public void monitorQueueAndAlert() {
        log.debug("Monitoring GDPR manual review queue: queueSize={}", reviewQueue.size());

        List<GdprManualReviewRequest> overdueReviews = getOverdueReviews();

        if (!overdueReviews.isEmpty()) {
            log.warn("Found {} overdue GDPR manual reviews", overdueReviews.size());

            // Alert DPO about overdue reviews
            dpoAlertService.alertManualReviewBacklog(
                    reviewQueue.size(),
                    overdueReviews.size(),
                    "gdpr-manual-review-monitor"
            );

            recordMetric("gdpr_manual_review_overdue_total");
        }

        // Alert for large queue size (>10 items)
        if (reviewQueue.size() > 10) {
            log.warn("GDPR manual review queue is large: queueSize={}", reviewQueue.size());

            dpoAlertService.alertManualReviewBacklog(
                    reviewQueue.size(),
                    overdueReviews.size(),
                    "gdpr-manual-review-monitor"
            );
        }

        // Update queue size metric
        Counter.builder("gdpr_manual_review_queue_size")
                .tag("has_overdue", String.valueOf(!overdueReviews.isEmpty()))
                .register(meterRegistry)
                .increment(reviewQueue.size());
    }

    /**
     * Get SLA compliance metrics
     */
    public SlaMetrics getSlaMetrics() {
        List<GdprManualReviewRequest> all = getPendingReviews();
        List<GdprManualReviewRequest> overdue = getOverdueReviews();

        long totalInQueue = all.size();
        long overdueCount = overdue.size();
        double complianceRate = totalInQueue > 0
                ? ((totalInQueue - overdueCount) * 100.0 / totalInQueue)
                : 100.0;

        return SlaMetrics.builder()
                .totalInQueue(totalInQueue)
                .overdueCount(overdueCount)
                .complianceRate(complianceRate)
                .highPriorityCount(getHighPriorityReviews().size())
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
    public static class GdprManualReviewRequest {
        private String exportId;
        private String subjectId;
        private String requestType;
        private String reviewReason;
        private String correlationId;
        private Priority priority;
        private String assignedTo;
        private boolean requiresLegalReview;
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
    public static class SlaMetrics {
        private long totalInQueue;
        private long overdueCount;
        private double complianceRate;
        private long highPriorityCount;
    }
}
