package com.waqiti.compliance.dlq;

import com.waqiti.compliance.entity.DLQRecord;
import com.waqiti.compliance.enums.DLQRecoveryStatus;
import com.waqiti.compliance.repository.DLQRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Manual Review Queue for DLQ Messages.
 *
 * Provides a priority-based queue for compliance officers to review and
 * manually resolve failed Kafka messages that could not be automatically recovered.
 *
 * Features:
 * - Priority-based queue (CRITICAL first, then HIGH, MEDIUM, LOW)
 * - Assignment to compliance officers
 * - Investigation notes and resolution tracking
 * - SLA monitoring (time in queue)
 * - Bulk operations for similar failures
 *
 * @author Waqiti Compliance Engineering
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DLQManualReviewQueue {

    private final DLQRecordRepository dlqRecordRepository;

    // In-memory queue for real-time notifications (backed by database)
    private final ConcurrentLinkedQueue<String> criticalQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> highPriorityQueue = new ConcurrentLinkedQueue<>();

    /**
     * Add DLQ record to manual review queue.
     */
    @Transactional
    public void add(DLQRecord record) {
        log.info("Adding DLQ record {} to manual review queue with priority {}",
            record.getId(), record.getPriority());

        record.setStatus(DLQRecoveryStatus.MANUAL_REVIEW_REQUIRED);
        record.setReviewQueuedAt(LocalDateTime.now());
        dlqRecordRepository.save(record);

        // Add to in-memory queue for real-time notifications
        if (record.getPriority() == DLQPriority.CRITICAL) {
            criticalQueue.add(record.getId());
        } else if (record.getPriority() == DLQPriority.HIGH) {
            highPriorityQueue.add(record.getId());
        }
    }

    /**
     * Get next DLQ record for review (priority-based).
     */
    @Transactional(readOnly = true)
    public Optional<DLQRecord> getNext() {
        // Try critical queue first
        String criticalId = criticalQueue.poll();
        if (criticalId != null) {
            return dlqRecordRepository.findById(criticalId);
        }

        // Then high priority queue
        String highId = highPriorityQueue.poll();
        if (highId != null) {
            return dlqRecordRepository.findById(highId);
        }

        // Fall back to database query for MEDIUM and LOW
        Pageable pageable = PageRequest.of(0, 1,
            Sort.by(Sort.Direction.DESC, "priority")
                .and(Sort.by(Sort.Direction.ASC, "reviewQueuedAt"))
        );

        Page<DLQRecord> page = dlqRecordRepository.findByStatus(
            DLQRecoveryStatus.MANUAL_REVIEW_REQUIRED,
            pageable
        );

        return page.hasContent() ? Optional.of(page.getContent().get(0)) : Optional.empty();
    }

    /**
     * Get all pending DLQ records for review.
     */
    @Transactional(readOnly = true)
    public List<DLQRecord> getPendingReviews(int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
            Sort.by(Sort.Direction.DESC, "priority")
                .and(Sort.by(Sort.Direction.ASC, "reviewQueuedAt"))
        );

        Page<DLQRecord> result = dlqRecordRepository.findByStatus(
            DLQRecoveryStatus.MANUAL_REVIEW_REQUIRED,
            pageable
        );

        return result.getContent();
    }

    /**
     * Get pending reviews by priority.
     */
    @Transactional(readOnly = true)
    public List<DLQRecord> getPendingReviewsByPriority(DLQPriority priority) {
        return dlqRecordRepository.findByStatusAndPriorityOrderByReviewQueuedAtAsc(
            DLQRecoveryStatus.MANUAL_REVIEW_REQUIRED,
            priority
        );
    }

    /**
     * Get pending reviews by topic (for batch resolution).
     */
    @Transactional(readOnly = true)
    public List<DLQRecord> getPendingReviewsByTopic(String topic) {
        return dlqRecordRepository.findByStatusAndTopicOrderByReviewQueuedAtAsc(
            DLQRecoveryStatus.MANUAL_REVIEW_REQUIRED,
            topic
        );
    }

    /**
     * Assign DLQ record to compliance officer.
     */
    @Transactional
    public DLQRecord assign(String recordId, String complianceOfficer) {
        DLQRecord record = dlqRecordRepository.findById(recordId)
            .orElseThrow(() -> new IllegalArgumentException("DLQ record not found: " + recordId));

        record.setAssignedTo(complianceOfficer);
        record.setAssignedAt(LocalDateTime.now());
        record.setStatus(DLQRecoveryStatus.UNDER_INVESTIGATION);

        return dlqRecordRepository.save(record);
    }

    /**
     * Add investigation notes to DLQ record.
     */
    @Transactional
    public DLQRecord addInvestigationNotes(String recordId, String notes, String officer) {
        DLQRecord record = dlqRecordRepository.findById(recordId)
            .orElseThrow(() -> new IllegalArgumentException("DLQ record not found: " + recordId));

        String existingNotes = record.getInvestigationNotes() != null
            ? record.getInvestigationNotes()
            : "";

        String newNotes = String.format("[%s] %s: %s\n%s",
            LocalDateTime.now(),
            officer,
            notes,
            existingNotes
        );

        record.setInvestigationNotes(newNotes);
        record.setLastInvestigationUpdate(LocalDateTime.now());

        return dlqRecordRepository.save(record);
    }

    /**
     * Resolve DLQ record (successful manual recovery).
     */
    @Transactional
    public DLQRecord resolve(String recordId, String resolution, String officer) {
        DLQRecord record = dlqRecordRepository.findById(recordId)
            .orElseThrow(() -> new IllegalArgumentException("DLQ record not found: " + recordId));

        record.setStatus(DLQRecoveryStatus.RESOLVED);
        record.setResolvedAt(LocalDateTime.now());
        record.setResolvedBy(officer);
        record.setResolution(resolution);

        log.info("DLQ record {} resolved by {}: {}", recordId, officer, resolution);

        return dlqRecordRepository.save(record);
    }

    /**
     * Mark DLQ record as unrecoverable (permanent failure).
     */
    @Transactional
    public DLQRecord markUnrecoverable(String recordId, String reason, String officer) {
        DLQRecord record = dlqRecordRepository.findById(recordId)
            .orElseThrow(() -> new IllegalArgumentException("DLQ record not found: " + recordId));

        record.setStatus(DLQRecoveryStatus.UNRECOVERABLE);
        record.setResolvedAt(LocalDateTime.now());
        record.setResolvedBy(officer);
        record.setResolution("UNRECOVERABLE: " + reason);

        log.warn("DLQ record {} marked as unrecoverable by {}: {}", recordId, officer, reason);

        return dlqRecordRepository.save(record);
    }

    /**
     * Retry DLQ record manually.
     */
    @Transactional
    public DLQRecord retryManually(String recordId, String officer) {
        DLQRecord record = dlqRecordRepository.findById(recordId)
            .orElseThrow(() -> new IllegalArgumentException("DLQ record not found: " + recordId));

        record.setStatus(DLQRecoveryStatus.MANUAL_RETRY_SCHEDULED);
        record.setLastAttemptAt(LocalDateTime.now());
        record.setRetryCount(record.getRetryCount() + 1);

        String notes = String.format("Manual retry initiated by %s", officer);
        addInvestigationNotes(recordId, notes, officer);

        log.info("Manual retry scheduled for DLQ record {} by {}", recordId, officer);

        return dlqRecordRepository.save(record);
    }

    /**
     * Get queue statistics for dashboard.
     */
    @Transactional(readOnly = true)
    public DLQQueueStatistics getStatistics() {
        DLQQueueStatistics stats = new DLQQueueStatistics();

        stats.setTotalPending(dlqRecordRepository.countByStatus(DLQRecoveryStatus.MANUAL_REVIEW_REQUIRED));
        stats.setCriticalCount(dlqRecordRepository.countByStatusAndPriority(
            DLQRecoveryStatus.MANUAL_REVIEW_REQUIRED, DLQPriority.CRITICAL));
        stats.setHighCount(dlqRecordRepository.countByStatusAndPriority(
            DLQRecoveryStatus.MANUAL_REVIEW_REQUIRED, DLQPriority.HIGH));
        stats.setMediumCount(dlqRecordRepository.countByStatusAndPriority(
            DLQRecoveryStatus.MANUAL_REVIEW_REQUIRED, DLQPriority.MEDIUM));
        stats.setLowCount(dlqRecordRepository.countByStatusAndPriority(
            DLQRecoveryStatus.MANUAL_REVIEW_REQUIRED, DLQPriority.LOW));

        stats.setUnderInvestigation(dlqRecordRepository.countByStatus(DLQRecoveryStatus.UNDER_INVESTIGATION));
        stats.setResolvedToday(dlqRecordRepository.countByStatusAndResolvedAtAfter(
            DLQRecoveryStatus.RESOLVED,
            LocalDateTime.now().minusDays(1)
        ));

        // Calculate average time in queue
        List<DLQRecord> pending = dlqRecordRepository.findByStatus(
            DLQRecoveryStatus.MANUAL_REVIEW_REQUIRED,
            PageRequest.of(0, 100)
        ).getContent();

        if (!pending.isEmpty()) {
            double avgHours = pending.stream()
                .mapToLong(r -> java.time.Duration.between(r.getReviewQueuedAt(), LocalDateTime.now()).toHours())
                .average()
                .orElse(0.0);
            stats.setAverageTimeInQueueHours(avgHours);
        }

        return stats;
    }

    /**
     * Get overdue DLQ records (in queue > 24 hours for CRITICAL, > 72 hours for HIGH).
     */
    @Transactional(readOnly = true)
    public List<DLQRecord> getOverdueRecords() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime criticalThreshold = now.minusHours(24);
        LocalDateTime highThreshold = now.minusDays(3);

        List<DLQRecord> critical = dlqRecordRepository.findByStatusAndPriorityAndReviewQueuedAtBefore(
            DLQRecoveryStatus.MANUAL_REVIEW_REQUIRED,
            DLQPriority.CRITICAL,
            criticalThreshold
        );

        List<DLQRecord> high = dlqRecordRepository.findByStatusAndPriorityAndReviewQueuedAtBefore(
            DLQRecoveryStatus.MANUAL_REVIEW_REQUIRED,
            DLQPriority.HIGH,
            highThreshold
        );

        return List.of(critical, high).stream()
            .flatMap(List::stream)
            .collect(Collectors.toList());
    }

    /**
     * Statistics for DLQ queue dashboard.
     */
    public static class DLQQueueStatistics {
        private long totalPending;
        private long criticalCount;
        private long highCount;
        private long mediumCount;
        private long lowCount;
        private long underInvestigation;
        private long resolvedToday;
        private double averageTimeInQueueHours;

        // Getters and setters
        public long getTotalPending() { return totalPending; }
        public void setTotalPending(long totalPending) { this.totalPending = totalPending; }
        public long getCriticalCount() { return criticalCount; }
        public void setCriticalCount(long criticalCount) { this.criticalCount = criticalCount; }
        public long getHighCount() { return highCount; }
        public void setHighCount(long highCount) { this.highCount = highCount; }
        public long getMediumCount() { return mediumCount; }
        public void setMediumCount(long mediumCount) { this.mediumCount = mediumCount; }
        public long getLowCount() { return lowCount; }
        public void setLowCount(long lowCount) { this.lowCount = lowCount; }
        public long getUnderInvestigation() { return underInvestigation; }
        public void setUnderInvestigation(long underInvestigation) { this.underInvestigation = underInvestigation; }
        public long getResolvedToday() { return resolvedToday; }
        public void setResolvedToday(long resolvedToday) { this.resolvedToday = resolvedToday; }
        public double getAverageTimeInQueueHours() { return averageTimeInQueueHours; }
        public void setAverageTimeInQueueHours(double averageTimeInQueueHours) {
            this.averageTimeInQueueHours = averageTimeInQueueHours;
        }
    }
}
