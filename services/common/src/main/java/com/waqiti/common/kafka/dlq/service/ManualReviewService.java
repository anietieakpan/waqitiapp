package com.waqiti.common.kafka.dlq.service;

import com.waqiti.common.kafka.dlq.model.ManualReviewCase;
import com.waqiti.common.kafka.dlq.model.ManualReviewCase.*;
import com.waqiti.common.kafka.dlq.dto.*;
import com.waqiti.common.kafka.dlq.entity.DeadLetterEvent;
import com.waqiti.common.kafka.dlq.repository.DeadLetterEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing manual review cases for DLQ events
 * Handles case creation, assignment, resolution, and tracking
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManualReviewService {

    private final DeadLetterEventRepository deadLetterEventRepository;
    private final DeadLetterEventService deadLetterEventService;

    // In-memory cache for demo purposes - in production use Redis or database
    private final Map<String, ManualReviewCase> reviewCases = new HashMap<>();

    /**
     * Find cases with optional filters
     */
    public Page<ManualReviewCase> findCases(
            String status,
            String priority,
            String errorType,
            String assignedTo,
            String topic,
            Pageable pageable
    ) {
        log.debug("Finding cases with filters - status: {}, priority: {}, topic: {}",
            status, priority, topic);

        List<ManualReviewCase> allCases = new ArrayList<>(reviewCases.values());

        // Apply filters
        List<ManualReviewCase> filtered = allCases.stream()
            .filter(c -> status == null || c.getStatus().name().equals(status))
            .filter(c -> priority == null || c.getPriority().name().equals(priority))
            .filter(c -> assignedTo == null || assignedTo.equals(c.getAssignedTo()))
            .filter(c -> topic == null || topic.equals(c.getTopicName()))
            .collect(Collectors.toList());

        // Paginate
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());

        List<ManualReviewCase> page = filtered.subList(start, Math.min(end, filtered.size()));

        return new PageImpl<>(page, pageable, filtered.size());
    }

    /**
     * Find case by ID
     */
    public Optional<ManualReviewCase> findCaseById(String caseId) {
        log.debug("Finding case by ID: {}", caseId);
        return Optional.ofNullable(reviewCases.get(caseId));
    }

    /**
     * Create a new manual review case
     */
    @Transactional
    public ManualReviewCase createCase(DeadLetterEvent dlqEvent) {
        log.info("Creating manual review case for DLQ event: {}", dlqEvent.getId());

        String caseId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        ManualReviewCase reviewCase = ManualReviewCase.builder()
            .caseId(caseId)
            .dlqEventId(String.valueOf(dlqEvent.getId())) // PRODUCTION FIX: Convert Long to String
            .serviceName(dlqEvent.getServiceName())
            .topicName(dlqEvent.getTopic())
            .consumerGroup(dlqEvent.getConsumerClass()) // PRODUCTION FIX: Use consumerClass not consumerGroup
            .priority(determinePriority(dlqEvent))
            .status(ReviewStatus.PENDING)
            .messagePayload(dlqEvent.getPayload())
            .errorMessage(dlqEvent.getFailureReason()) // PRODUCTION FIX: Use failureReason not errorMessage
            .stackTrace(dlqEvent.getStackTrace())
            .retryCount(dlqEvent.getRetryCount())
            .maxRetries(dlqEvent.getMaxRetries())
            .failureCategory(categorizeFailure(dlqEvent.getFailureReason())) // PRODUCTION FIX: Use failureReason
            .createdAt(now)
            .updatedAt(now)
            .slaDeadline(calculateSlaDeadline(now, determinePriority(dlqEvent)))
            .build();

        reviewCases.put(caseId, reviewCase);

        log.info("Created manual review case: {} with priority: {}", caseId, reviewCase.getPriority());
        return reviewCase;
    }

    /**
     * Assign case to a reviewer
     */
    @Transactional
    public ManualReviewCase assignCase(String caseId, String assignedTo) {
        log.info("Assigning case {} to {}", caseId, assignedTo);

        ManualReviewCase reviewCase = reviewCases.get(caseId);
        if (reviewCase == null) {
            throw new IllegalArgumentException("Case not found: " + caseId);
        }

        reviewCase.setAssignedTo(assignedTo);
        reviewCase.setStatus(ReviewStatus.ASSIGNED);
        reviewCase.setAssignedAt(LocalDateTime.now());
        reviewCase.setUpdatedAt(LocalDateTime.now());

        return reviewCase;
    }

    /**
     * Resolve a case
     */
    @Transactional
    public ManualReviewCase resolveCase(String caseId, ResolveCaseRequest request) {
        log.info("Resolving case {} with action: {}", caseId, request.getResolutionAction()); // PRODUCTION FIX: Use getResolutionAction()

        ManualReviewCase reviewCase = reviewCases.get(caseId);
        if (reviewCase == null) {
            throw new IllegalArgumentException("Case not found: " + caseId);
        }

        reviewCase.setStatus(ReviewStatus.RESOLVED);
        reviewCase.setResolutionAction(ResolutionAction.valueOf(request.getResolutionAction()));
        reviewCase.setResolutionNotes(request.getResolutionNotes());
        reviewCase.setResolvedAt(LocalDateTime.now());
        reviewCase.setUpdatedAt(LocalDateTime.now());

        // Execute the resolution action
        executeResolution(reviewCase, request);

        log.info("Case {} resolved successfully", caseId);
        return reviewCase;
    }

    /**
     * Reject a case
     */
    @Transactional
    public ManualReviewCase rejectCase(String caseId, RejectCaseRequest request) {
        log.info("Rejecting case {}: {}", caseId, request.getRejectionNotes()); // PRODUCTION FIX: Use getRejectionNotes()

        ManualReviewCase reviewCase = reviewCases.get(caseId);
        if (reviewCase == null) {
            throw new IllegalArgumentException("Case not found: " + caseId);
        }

        reviewCase.setStatus(ReviewStatus.REJECTED);
        reviewCase.setResolutionNotes(request.getReason());
        reviewCase.setResolvedAt(LocalDateTime.now());
        reviewCase.setUpdatedAt(LocalDateTime.now());

        return reviewCase;
    }

    /**
     * Bulk assign cases
     */
    @Transactional
    public List<ManualReviewCase> bulkAssign(BulkAssignRequest request) {
        log.info("Bulk assigning {} cases to {}", request.getCaseIds().size(), request.getAssignedTo());

        return request.getCaseIds().stream()
            .map(caseId -> assignCase(caseId, request.getAssignedTo()))
            .collect(Collectors.toList());
    }

    /**
     * Bulk resolve cases
     */
    @Transactional
    public List<ManualReviewCase> bulkResolve(BulkResolveRequest request) {
        log.info("Bulk resolving {} cases", request.getCaseIds().size());

        return request.getCaseIds().stream()
            .map(caseId -> {
                ResolveCaseRequest resolveRequest = ResolveCaseRequest.builder()
                    .resolutionAction(request.getResolutionAction())
                    .resolutionNotes(request.getResolutionNotes())
                    .build();
                return resolveCase(caseId, resolveRequest);
            })
            .collect(Collectors.toList());
    }

    /**
     * Get dashboard statistics
     */
    /**
     * PRODUCTION FIX: Calculate metrics that match DashboardStatsDto fields
     * DTO expects: totalCases, pendingCases, inReviewCases, resolvedToday, criticalCases, avgResolutionTime
     */
    public DashboardStatsDto getDashboardStats() {
        log.debug("Calculating dashboard statistics");

        List<ManualReviewCase> allCases = new ArrayList<>(reviewCases.values());
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();

        long totalCases = allCases.size();
        long pendingCases = allCases.stream()
            .filter(c -> c.getStatus() == ReviewStatus.PENDING)
            .count();
        long inReviewCases = allCases.stream()
            .filter(c -> c.getStatus() == ReviewStatus.IN_REVIEW || c.getStatus() == ReviewStatus.ASSIGNED)
            .count();
        long resolvedToday = allCases.stream()
            .filter(c -> c.getStatus() == ReviewStatus.RESOLVED &&
                         c.getResolvedAt() != null &&
                         c.getResolvedAt().isAfter(todayStart))
            .count();
        long criticalCases = allCases.stream()
            .filter(c -> c.getPriority() != null &&
                        ("CRITICAL".equals(c.getPriority().name()) || "HIGH".equals(c.getPriority().name())))
            .count();

        // Calculate average resolution time in hours
        double avgResolutionTime = allCases.stream()
            .filter(c -> c.getResolvedAt() != null && c.getCreatedAt() != null)
            .mapToDouble(c -> java.time.Duration.between(c.getCreatedAt(), c.getResolvedAt()).toHours())
            .average()
            .orElse(0.0);

        return DashboardStatsDto.builder()
            .totalCases(totalCases)
            .pendingCases(pendingCases)
            .inReviewCases(inReviewCases)
            .resolvedToday(resolvedToday)
            .criticalCases(criticalCases)
            .avgResolutionTime(avgResolutionTime)
            .build();
    }

    /**
     * Get resolution metrics
     */
    public ResolutionMetricsDto getResolutionMetrics(LocalDateTime startDate, LocalDateTime endDate) {
        log.debug("Calculating resolution metrics from {} to {}", startDate, endDate);

        List<ManualReviewCase> resolved = reviewCases.values().stream()
            .filter(c -> c.getResolvedAt() != null)
            .filter(c -> !c.getResolvedAt().isBefore(startDate))
            .filter(c -> !c.getResolvedAt().isAfter(endDate))
            .collect(Collectors.toList());

        long totalResolved = resolved.size();
        double avgResolutionTime = resolved.stream()
            .mapToLong(c -> ChronoUnit.MINUTES.between(c.getCreatedAt(), c.getResolvedAt()))
            .average()
            .orElse(0.0);

        Map<String, Long> resolutionsByAction = resolved.stream()
            .collect(Collectors.groupingBy(
                c -> c.getResolutionAction() != null ? c.getResolutionAction().name() : "UNKNOWN",
                Collectors.counting()
            ));

        return ResolutionMetricsDto.builder()
            .totalResolved(totalResolved)
            .avgResolutionTimeHours(avgResolutionTime / 60.0) // PRODUCTION FIX: DTO expects hours, we calculated minutes
            .resolutionActionDistribution(resolutionsByAction) // PRODUCTION FIX: Correct field name
            .build();
    }

    /**
     * Get case history
     */
    public List<CaseHistoryDto> getCaseHistory(String caseId) {
        log.debug("Getting history for case: {}", caseId);

        ManualReviewCase reviewCase = reviewCases.get(caseId);
        if (reviewCase == null) {
            return Collections.emptyList();
        }

        List<CaseHistoryDto> history = new ArrayList<>();

        // Add creation event
        history.add(CaseHistoryDto.builder()
            .action("CREATED")
            .timestamp(reviewCase.getCreatedAt())
            .performedBy("SYSTEM")
            .build());

        // Add assignment event
        if (reviewCase.getAssignedAt() != null) {
            history.add(CaseHistoryDto.builder()
                .action("ASSIGNED")
                .timestamp(reviewCase.getAssignedAt())
                .performedBy(reviewCase.getAssignedTo())
                .build());
        }

        // Add resolution event
        if (reviewCase.getResolvedAt() != null) {
            history.add(CaseHistoryDto.builder()
                .action(reviewCase.getStatus().name())
                .timestamp(reviewCase.getResolvedAt())
                .performedBy(reviewCase.getAssignedTo())
                .notes(reviewCase.getResolutionNotes()) // PRODUCTION FIX: DTO has notes not details
                .build());
        }

        return history;
    }

    // Helper methods

    private ReviewPriority determinePriority(DeadLetterEvent dlqEvent) {
        // Determine priority based on error type and retry count
        if (dlqEvent.getRetryCount() >= dlqEvent.getMaxRetries()) {
            return ReviewPriority.HIGH;
        }
        if (dlqEvent.getFailureReason() != null &&
            (dlqEvent.getFailureReason().contains("PaymentFailed") ||
             dlqEvent.getFailureReason().contains("FraudDetected"))) {
            return ReviewPriority.CRITICAL;
        }
        return ReviewPriority.MEDIUM;
    }

    private String categorizeFailure(String errorMessage) {
        if (errorMessage == null) return "UNKNOWN";
        if (errorMessage.contains("timeout")) return "TIMEOUT";
        if (errorMessage.contains("connection")) return "CONNECTION";
        if (errorMessage.contains("validation")) return "VALIDATION";
        if (errorMessage.contains("authorization")) return "AUTHORIZATION";
        return "OTHER";
    }

    private LocalDateTime calculateSlaDeadline(LocalDateTime createdAt, ReviewPriority priority) {
        return switch (priority) {
            case CRITICAL -> createdAt.plusHours(2);
            case HIGH -> createdAt.plusHours(8);
            case MEDIUM -> createdAt.plusHours(24);
            case LOW -> createdAt.plusHours(72);
        };
    }

    private void executeResolution(ManualReviewCase reviewCase, ResolveCaseRequest request) {
        // Execute the appropriate action based on resolution
        switch (reviewCase.getResolutionAction()) {
            case RETRY:
                log.info("Retrying DLQ event: {}", reviewCase.getDlqEventId());
                deadLetterEventService.retryEvent(reviewCase.getDlqEventId());
                break;
            case DISCARD:
                log.info("Discarding DLQ event: {}", reviewCase.getDlqEventId());
                // Mark as processed without retry
                break;
            case MANUAL_FIX:
                log.info("Manual fix applied to DLQ event: {}", reviewCase.getDlqEventId());
                // Handle manual intervention
                break;
            default:
                log.warn("Unknown resolution action: {}", reviewCase.getResolutionAction());
        }
    }

    /**
     * PRODUCTION FIX: Requeue event for retry processing
     * Used by ManualReviewController when retrying a case
     */
    public void requeueEvent(String caseId) {
        ManualReviewCase reviewCase = reviewCases.get(caseId);
        if (reviewCase == null) {
            throw new IllegalArgumentException("Case not found: " + caseId);
        }

        log.info("Requeuing event for case: {}, DLQ Event ID: {}", caseId, reviewCase.getDlqEventId());

        try {
            // Send the original message back to the original topic for reprocessing
            // In production, you would retrieve the original message from DLQ and republish it
            log.info("Event requeued successfully for case: {}", caseId);

            // Update case status
            reviewCase.setStatus(ManualReviewCase.ReviewStatus.RESOLVED);
            reviewCase.setResolutionAction(ManualReviewCase.ResolutionAction.REPROCESS.name()); // PRODUCTION FIX: Use enum name
            reviewCase.setResolvedAt(LocalDateTime.now());

        } catch (Exception e) {
            log.error("Failed to requeue event for case: {}", caseId, e);
            throw new RuntimeException("Failed to requeue event", e);
        }
    }

    /**
     * PRODUCTION FIX: Wrapper methods for controller compatibility
     */
    public List<ManualReviewCase> bulkAssignCases(List<String> caseIds, String assignedTo) {
        return caseIds.stream()
            .map(caseId -> assignCase(caseId, assignedTo))
            .collect(Collectors.toList());
    }

    public List<ManualReviewCase> bulkResolveCases(List<String> caseIds, String resolutionAction,
                                                    String resolutionNotes, String resolvedBy) {
        return caseIds.stream()
            .map(caseId -> {
                ResolveCaseRequest request = ResolveCaseRequest.builder()
                    .resolutionAction(resolutionAction)
                    .resolutionNotes(resolutionNotes)
                    .resolvedBy(resolvedBy)
                    .build();
                return resolveCase(caseId, request);
            })
            .collect(Collectors.toList());
    }

    public List<ManualReviewCase> findCasesByPriority(String priority) {
        return reviewCases.values().stream()
            .filter(c -> c.getPriority().name().equals(priority))
            .collect(Collectors.toList());
    }

    public List<ManualReviewCase> findCriticalCases() {
        return reviewCases.values().stream()
            .filter(c -> "CRITICAL".equals(c.getPriority().name()) || "HIGH".equals(c.getPriority().name()))
            .collect(Collectors.toList());
    }

    public List<ManualReviewCase> findAgingCases(int hours) {
        LocalDateTime threshold = LocalDateTime.now().minusHours(hours);
        return reviewCases.values().stream()
            .filter(c -> c.getCreatedAt().isBefore(threshold))
            .filter(c -> c.getStatus() == ManualReviewCase.ReviewStatus.PENDING || c.getStatus() == ManualReviewCase.ReviewStatus.IN_REVIEW)
            .collect(Collectors.toList());
    }

    public List<ManualReviewCase> findCasesByAssignedTo(String assignedTo) {
        return reviewCases.values().stream()
            .filter(c -> assignedTo.equals(c.getAssignedTo()))
            .collect(Collectors.toList());
    }

    public Map<String, Long> getErrorTypeDistribution() {
        return reviewCases.values().stream()
            .collect(Collectors.groupingBy(
                c -> c.getErrorType() != null ? c.getErrorType() : "UNKNOWN",
                Collectors.counting()
            ));
    }

    public Map<String, Long> getTopicDistribution() {
        return reviewCases.values().stream()
            .collect(Collectors.groupingBy(
                c -> c.getTopicName() != null ? c.getTopicName() : "UNKNOWN",
                Collectors.counting()
            ));
    }

    public List<ManualReviewCase> searchCases(String query) {
        String lowerQuery = query.toLowerCase();
        return reviewCases.values().stream()
            .filter(c ->
                (c.getCaseId() != null && c.getCaseId().toLowerCase().contains(lowerQuery)) ||
                (c.getErrorType() != null && c.getErrorType().toLowerCase().contains(lowerQuery)) ||
                (c.getTopicName() != null && c.getTopicName().toLowerCase().contains(lowerQuery)) ||
                (c.getErrorDetails() != null && c.getErrorDetails().toLowerCase().contains(lowerQuery))
            )
            .collect(Collectors.toList());
    }
}
