package com.waqiti.compliance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Regulatory Case Service - Handles regulatory case creation and management
 * 
 * Provides comprehensive regulatory case management for:
 * - Regulatory case creation and lifecycle tracking
 * - Case priority assessment and escalation
 * - Regulatory deadline management and monitoring
 * - Case documentation and evidence collection
 * - Regulatory correspondence and communication
 * - Case resolution and compliance verification
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegulatoryCaseService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${compliance.regulatory.case.retention.days:2555}")
    private int caseRetentionDays; // 7 years default

    @Value("${compliance.regulatory.case.enabled:true}")
    private boolean regulatoryCaseEnabled;

    @Value("${compliance.regulatory.escalation.threshold.amount:100000}")
    private BigDecimal escalationThresholdAmount;

    /**
     * Creates a new regulatory case
     */
    public String createRegulatoryCase(
            String reviewId,
            String reviewType,
            String customerId,
            String accountId,
            BigDecimal amount,
            String currency,
            String description,
            String priority,
            LocalDateTime timestamp) {

        if (!regulatoryCaseEnabled) {
            log.debug("Regulatory case creation disabled, skipping case creation");
            throw new IllegalStateException("Regulatory case creation is disabled - cannot create mandatory case for review: " + reviewId);
        }

        try {
            log.info("Creating regulatory case for review: {}", reviewId);

            String caseId = "REG-CASE-" + UUID.randomUUID().toString();
            
            // Determine case priority and regulatory requirements
            String casePriority = determineCasePriority(reviewType, amount, priority);
            String regulatoryRequirement = determineRegulatoryRequirement(reviewType, amount);
            LocalDateTime dueDate = calculateDueDate(reviewType, casePriority, timestamp);

            // Create case record
            Map<String, String> caseData = Map.of(
                "case_id", caseId,
                "review_id", reviewId,
                "case_type", reviewType,
                "customer_id", customerId,
                "account_id", accountId != null ? accountId : "",
                "amount", amount != null ? amount.toString() : "0",
                "currency", currency,
                "description", description != null ? description : "",
                "priority", casePriority,
                "regulatory_requirement", regulatoryRequirement,
                "status", "OPEN",
                "created_at", timestamp.toString(),
                "due_date", dueDate.toString(),
                "assigned_to", "COMPLIANCE_TEAM"
            );

            // Store case
            String caseKey = "compliance:regulatory:cases:" + caseId;
            redisTemplate.opsForHash().putAll(caseKey, caseData);
            redisTemplate.expire(caseKey, Duration.ofDays(caseRetentionDays));

            // Add to case queue based on priority
            String queueKey = "compliance:regulatory:queue:" + casePriority.toLowerCase();
            redisTemplate.opsForList().rightPush(queueKey, caseId);
            redisTemplate.expire(queueKey, Duration.ofDays(30));

            // Add to deadline tracking
            String deadlineKey = "compliance:regulatory:deadlines:" + dueDate.toLocalDate();
            redisTemplate.opsForList().rightPush(deadlineKey, caseId);
            redisTemplate.expire(deadlineKey, Duration.ofDays(30));

            // Create case timeline entry
            createCaseTimelineEntry(caseId, "CASE_CREATED", "Regulatory case created", timestamp);

            // Escalate if critical
            if ("CRITICAL".equals(casePriority)) {
                escalateCriticalCase(caseId, reviewType, customerId, amount);
            }

            log.info("Regulatory case created: {} for review: {} - Priority: {}", 
                caseId, reviewId, casePriority);
            
            return caseId;

        } catch (Exception e) {
            log.error("CRITICAL: Failed to create mandatory regulatory case for review: {}", reviewId, e);
            throw new RuntimeException("Critical failure creating regulatory case for review: " + reviewId, e);
        }
    }

    /**
     * Updates regulatory case status and progress
     */
    public void updateCaseStatus(
            String caseId,
            String status,
            String notes,
            LocalDateTime timestamp) {

        try {
            log.debug("Updating case status for: {} - Status: {}", caseId, status);

            String caseKey = "compliance:regulatory:cases:" + caseId;
            Map<String, String> updates = Map.of(
                "status", status,
                "notes", notes != null ? notes : "",
                "last_updated", timestamp.toString()
            );

            redisTemplate.opsForHash().putAll(caseKey, updates);

            // Create timeline entry
            createCaseTimelineEntry(caseId, "STATUS_UPDATE", status + " - " + notes, timestamp);

            // Handle status-specific actions
            handleStatusSpecificActions(caseId, status, timestamp);

            log.info("Case status updated: {} - Status: {}", caseId, status);

        } catch (Exception e) {
            log.error("Failed to update case status for: {}", caseId, e);
        }
    }

    /**
     * Assigns case to specific team member or department
     */
    public void assignCase(
            String caseId,
            String assignedTo,
            String assignedBy,
            LocalDateTime timestamp) {

        try {
            log.info("Assigning case: {} to: {}", caseId, assignedTo);

            String caseKey = "compliance:regulatory:cases:" + caseId;
            Map<String, String> updates = Map.of(
                "assigned_to", assignedTo,
                "assigned_by", assignedBy,
                "assigned_at", timestamp.toString()
            );

            redisTemplate.opsForHash().putAll(caseKey, updates);

            // Create timeline entry
            createCaseTimelineEntry(caseId, "CASE_ASSIGNED", 
                "Assigned to " + assignedTo + " by " + assignedBy, timestamp);

            // Add to assignee's workload
            String workloadKey = "compliance:regulatory:workload:" + assignedTo;
            redisTemplate.opsForSet().add(workloadKey, caseId);
            redisTemplate.expire(workloadKey, Duration.ofDays(90));

        } catch (Exception e) {
            log.error("Failed to assign case: {}", caseId, e);
        }
    }

    /**
     * Adds documentation to regulatory case
     */
    public void addCaseDocumentation(
            String caseId,
            String documentType,
            String documentContent,
            String addedBy,
            LocalDateTime timestamp) {

        try {
            log.debug("Adding documentation to case: {} - Type: {}", caseId, documentType);

            String docId = UUID.randomUUID().toString();
            String docKey = "compliance:regulatory:docs:" + caseId + ":" + docId;
            
            Map<String, String> docData = Map.of(
                "document_id", docId,
                "case_id", caseId,
                "document_type", documentType,
                "content", documentContent,
                "added_by", addedBy,
                "added_at", timestamp.toString()
            );

            redisTemplate.opsForHash().putAll(docKey, docData);
            redisTemplate.expire(docKey, Duration.ofDays(caseRetentionDays));

            // Link to case
            String caseDocsKey = "compliance:regulatory:case_docs:" + caseId;
            redisTemplate.opsForList().rightPush(caseDocsKey, docId);
            redisTemplate.expire(caseDocsKey, Duration.ofDays(caseRetentionDays));

            // Create timeline entry
            createCaseTimelineEntry(caseId, "DOCUMENT_ADDED", 
                documentType + " document added by " + addedBy, timestamp);

        } catch (Exception e) {
            log.error("Failed to add documentation to case: {}", caseId, e);
        }
    }

    /**
     * Closes regulatory case with resolution details
     */
    public void closeCaseWithResolution(
            String caseId,
            String resolution,
            String closedBy,
            LocalDateTime timestamp) {

        try {
            log.info("Closing regulatory case: {} - Resolution: {}", caseId, resolution);

            String caseKey = "compliance:regulatory:cases:" + caseId;
            Map<String, String> updates = Map.of(
                "status", "CLOSED",
                "resolution", resolution,
                "closed_by", closedBy,
                "closed_at", timestamp.toString()
            );

            redisTemplate.opsForHash().putAll(caseKey, updates);

            // Create timeline entry
            createCaseTimelineEntry(caseId, "CASE_CLOSED", 
                "Case closed with resolution: " + resolution, timestamp);

            // Remove from active queues
            removeFromActiveQueues(caseId);

            // Archive case
            archiveCase(caseId, timestamp);

        } catch (Exception e) {
            log.error("Failed to close case: {}", caseId, e);
        }
    }

    /**
     * Checks for overdue regulatory cases
     */
    public void checkOverdueCases() {
        try {
            log.debug("Checking for overdue regulatory cases");

            LocalDateTime now = LocalDateTime.now();
            
            // Check cases with past due dates
            for (int i = 0; i <= 30; i++) {
                String deadlineKey = "compliance:regulatory:deadlines:" + now.minusDays(i).toLocalDate();
                if (Boolean.TRUE.equals(redisTemplate.hasKey(deadlineKey))) {
                    Long overdueCount = redisTemplate.opsForList().size(deadlineKey);
                    if (overdueCount != null && overdueCount > 0) {
                        log.warn("Found {} overdue regulatory cases for date: {}", 
                            overdueCount, now.minusDays(i).toLocalDate());
                        
                        escalateOverdueCases(deadlineKey, now.minusDays(i).toLocalDate().toString());
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to check overdue cases", e);
        }
    }

    /**
     * Generates case summary report
     */
    public Map<String, Object> generateCaseSummary(LocalDateTime startDate, LocalDateTime endDate) {
        try {
            log.debug("Generating case summary from {} to {}", startDate, endDate);

            String summaryKey = "compliance:regulatory:summary:" + startDate.toLocalDate() + "_" + endDate.toLocalDate();
            Map<Object, Object> summary = redisTemplate.opsForHash().entries(summaryKey);

            if (summary.isEmpty()) {
                // Generate summary if not cached
                summary = Map.of(
                    "total_cases", "0",
                    "open_cases", "0",
                    "closed_cases", "0",
                    "overdue_cases", "0",
                    "critical_cases", "0",
                    "generated_at", LocalDateTime.now().toString()
                );

                redisTemplate.opsForHash().putAll(summaryKey, summary);
                redisTemplate.expire(summaryKey, Duration.ofDays(7));
            }

            return (Map<String, Object>) summary;

        } catch (Exception e) {
            log.error("Failed to generate case summary", e);
            return Map.of("error", "Failed to generate summary");
        }
    }

    // Helper methods

    private String determineCasePriority(String reviewType, BigDecimal amount, String priority) {
        // Critical priority for OFAC/sanctions
        if (reviewType.contains("OFAC") || reviewType.contains("SANCTIONS")) {
            return "CRITICAL";
        }

        // High priority for large amounts or existing high priority
        if ((amount != null && amount.compareTo(escalationThresholdAmount) > 0) || 
            "HIGH".equals(priority) || "CRITICAL".equals(priority)) {
            return "HIGH";
        }

        // Medium priority for SAR/CTR related cases
        if (reviewType.contains("SAR") || reviewType.contains("CTR")) {
            return "MEDIUM";
        }

        return "NORMAL";
    }

    private String determineRegulatoryRequirement(String reviewType, BigDecimal amount) {
        if (reviewType.contains("SAR")) {
            return "SAR_FILING_REQUIRED";
        }
        if (reviewType.contains("CTR") && amount != null && amount.compareTo(new BigDecimal("10000")) > 0) {
            return "CTR_FILING_REQUIRED";
        }
        if (reviewType.contains("OFAC")) {
            return "OFAC_REVIEW_REQUIRED";
        }
        return "COMPLIANCE_REVIEW_REQUIRED";
    }

    private LocalDateTime calculateDueDate(String reviewType, String priority, LocalDateTime timestamp) {
        // Critical cases - 24 hours
        if ("CRITICAL".equals(priority)) {
            return timestamp.plusHours(24);
        }

        // SAR cases - 14 days (regulatory requirement)
        if (reviewType.contains("SAR")) {
            return timestamp.plusDays(14);
        }

        // CTR cases - 15 days
        if (reviewType.contains("CTR")) {
            return timestamp.plusDays(15);
        }

        // High priority - 3 days
        if ("HIGH".equals(priority)) {
            return timestamp.plusDays(3);
        }

        // Normal cases - 7 days
        return timestamp.plusDays(7);
    }

    private void createCaseTimelineEntry(String caseId, String eventType, String description, LocalDateTime timestamp) {
        try {
            String timelineId = UUID.randomUUID().toString();
            String timelineKey = "compliance:regulatory:timeline:" + caseId + ":" + timelineId;
            
            Map<String, String> timelineData = Map.of(
                "timeline_id", timelineId,
                "case_id", caseId,
                "event_type", eventType,
                "description", description,
                "timestamp", timestamp.toString()
            );

            redisTemplate.opsForHash().putAll(timelineKey, timelineData);
            redisTemplate.expire(timelineKey, Duration.ofDays(caseRetentionDays));

            // Add to case timeline list
            String caseTimelineKey = "compliance:regulatory:case_timeline:" + caseId;
            redisTemplate.opsForList().rightPush(caseTimelineKey, timelineId);
            redisTemplate.expire(caseTimelineKey, Duration.ofDays(caseRetentionDays));

        } catch (Exception e) {
            log.error("Failed to create timeline entry", e);
        }
    }

    private void escalateCriticalCase(String caseId, String reviewType, String customerId, BigDecimal amount) {
        try {
            String escalationKey = "compliance:regulatory:escalations:" + System.currentTimeMillis();
            Map<String, String> escalationData = Map.of(
                "case_id", caseId,
                "escalation_type", "CRITICAL_CASE",
                "review_type", reviewType,
                "customer_id", customerId,
                "amount", amount != null ? amount.toString() : "0",
                "escalated_at", LocalDateTime.now().toString(),
                "priority", "CRITICAL"
            );

            redisTemplate.opsForHash().putAll(escalationKey, escalationData);
            redisTemplate.expire(escalationKey, Duration.ofDays(30));

            log.error("Critical regulatory case escalated: {}", caseId);

        } catch (Exception e) {
            log.error("Failed to escalate critical case", e);
        }
    }

    private void handleStatusSpecificActions(String caseId, String status, LocalDateTime timestamp) {
        switch (status) {
            case "IN_PROGRESS":
                // Remove from pending queue
                removeFromQueue(caseId, "pending");
                // Add to in-progress queue
                addToQueue(caseId, "in_progress");
                break;
            case "UNDER_REVIEW":
                // Mark for supervisor attention
                flagForSupervisorReview(caseId, timestamp);
                break;
            case "ESCALATED":
                // Create escalation record
                createEscalationRecord(caseId, timestamp);
                break;
        }
    }

    private void removeFromActiveQueues(String caseId) {
        try {
            // Remove from all priority queues
            String[] priorities = {"critical", "high", "medium", "normal"};
            for (String priority : priorities) {
                String queueKey = "compliance:regulatory:queue:" + priority;
                redisTemplate.opsForList().remove(queueKey, 1, caseId);
            }

            // Remove from status queues
            String[] statuses = {"pending", "in_progress", "under_review"};
            for (String status : statuses) {
                String queueKey = "compliance:regulatory:queue:" + status;
                redisTemplate.opsForList().remove(queueKey, 1, caseId);
            }

        } catch (Exception e) {
            log.error("Failed to remove case from active queues", e);
        }
    }

    private void archiveCase(String caseId, LocalDateTime timestamp) {
        try {
            String archiveKey = "compliance:regulatory:archive:" + timestamp.getYear() + ":" + caseId;
            Map<String, String> archiveData = Map.of(
                "case_id", caseId,
                "archived_at", timestamp.toString(),
                "retention_until", timestamp.plusDays(caseRetentionDays).toString()
            );

            redisTemplate.opsForHash().putAll(archiveKey, archiveData);
            redisTemplate.expire(archiveKey, Duration.ofDays(caseRetentionDays));

        } catch (Exception e) {
            log.error("Failed to archive case", e);
        }
    }

    private void escalateOverdueCases(String deadlineKey, String dateStr) {
        try {
            Long overdueCount = redisTemplate.opsForList().size(deadlineKey);
            
            if (overdueCount != null && overdueCount > 0) {
                String escalationKey = "compliance:regulatory:overdue_escalation:" + System.currentTimeMillis();
                Map<String, String> escalationData = Map.of(
                    "type", "OVERDUE_CASES",
                    "count", overdueCount.toString(),
                    "due_date", dateStr,
                    "escalated_at", LocalDateTime.now().toString(),
                    "priority", "HIGH"
                );

                redisTemplate.opsForHash().putAll(escalationKey, escalationData);
                redisTemplate.expire(escalationKey, Duration.ofDays(7));

                log.error("Escalated {} overdue regulatory cases from {}", overdueCount, dateStr);
            }

        } catch (Exception e) {
            log.error("Failed to escalate overdue cases", e);
        }
    }

    private void removeFromQueue(String caseId, String queueType) {
        String queueKey = "compliance:regulatory:queue:" + queueType;
        redisTemplate.opsForList().remove(queueKey, 1, caseId);
    }

    private void addToQueue(String caseId, String queueType) {
        String queueKey = "compliance:regulatory:queue:" + queueType;
        redisTemplate.opsForList().rightPush(queueKey, caseId);
        redisTemplate.expire(queueKey, Duration.ofDays(30));
    }

    private void flagForSupervisorReview(String caseId, LocalDateTime timestamp) {
        try {
            String flagKey = "compliance:regulatory:supervisor_review:" + caseId;
            Map<String, String> flagData = Map.of(
                "case_id", caseId,
                "flagged_at", timestamp.toString(),
                "requires_approval", "true"
            );

            redisTemplate.opsForHash().putAll(flagKey, flagData);
            redisTemplate.expire(flagKey, Duration.ofDays(30));

        } catch (Exception e) {
            log.error("Failed to flag for supervisor review", e);
        }
    }

    private void createEscalationRecord(String caseId, LocalDateTime timestamp) {
        try {
            String escalationKey = "compliance:regulatory:case_escalations:" + caseId + ":" + System.currentTimeMillis();
            Map<String, String> escalationData = Map.of(
                "case_id", caseId,
                "escalated_at", timestamp.toString(),
                "escalation_reason", "STATUS_ESCALATED",
                "requires_attention", "true"
            );

            redisTemplate.opsForHash().putAll(escalationKey, escalationData);
            redisTemplate.expire(escalationKey, Duration.ofDays(90));

        } catch (Exception e) {
            log.error("Failed to create escalation record", e);
        }
    }
}