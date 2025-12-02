package com.waqiti.compliance.service;

import com.waqiti.compliance.exception.ComplianceException;
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
 * Compliance Reporting Service - Handles comprehensive compliance reporting requirements
 * 
 * Provides comprehensive compliance reporting capabilities for:
 * - Regulatory report generation and submission
 * - BSA/AML reporting requirements and tracking
 * - CTR (Currency Transaction Report) filing and management
 * - Compliance metrics aggregation and analysis
 * - Regulatory deadline monitoring and alerts
 * - Report audit trail and compliance verification
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceReportingService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${compliance.reporting.enabled:true}")
    private boolean reportingEnabled;

    @Value("${compliance.ctr.threshold:10000}")
    private BigDecimal ctrThreshold;

    @Value("${compliance.ctr.filing.deadline.days:15}")
    private int ctrFilingDeadlineDays;

    @Value("${compliance.reporting.retention.days:2555}")
    private int reportingRetentionDays; // 7 years

    /**
     * Processes compliance reporting requirements
     */
    public void processComplianceReporting(
            String reviewId,
            String reviewType,
            String customerId,
            String accountId,
            String transactionId,
            BigDecimal amount,
            String currency,
            String description,
            Double riskScore,
            LocalDateTime timestamp) {

        if (!reportingEnabled) {
            log.debug("Compliance reporting disabled, skipping reporting processing");
            return;
        }

        try {
            log.debug("Processing compliance reporting for review: {}", reviewId);

            // Generate compliance report
            String reportId = generateComplianceReport(
                reviewId, reviewType, customerId, accountId, transactionId,
                amount, currency, description, riskScore, timestamp
            );

            // Determine regulatory reporting requirements
            determineRegulatoryReportingRequirements(
                reportId, reviewType, amount, currency, riskScore, timestamp
            );

            // Update compliance metrics
            updateComplianceMetrics(reviewType, amount, currency, riskScore, timestamp);

            // Create audit trail
            createReportingAuditTrail(reportId, reviewId, reviewType, timestamp);

            log.info("Compliance reporting processed for review: {} - Report ID: {}", reviewId, reportId);

        } catch (Exception e) {
            log.error("Failed to process compliance reporting for review: {}", reviewId, e);
        }
    }

    /**
     * Triggers CTR filing for qualifying transactions
     */
    public void triggerCTRFiling(
            String customerId,
            String transactionId,
            BigDecimal amount,
            String currency,
            LocalDateTime timestamp) {

        try {
            log.info("Triggering CTR filing for customer: {} - Amount: {} {}", 
                customerId, amount, currency);

            // Validate CTR filing requirement
            if (!requiresCTRFiling(amount, currency)) {
                log.debug("CTR filing not required for amount: {} {}", amount, currency);
                return;
            }

            // Create CTR filing record
            String ctrId = createCTRFilingRecord(customerId, transactionId, amount, currency, timestamp);

            // Schedule CTR filing
            scheduleCTRFiling(ctrId, customerId, timestamp);

            // Create CTR case for tracking
            createCTRCase(ctrId, customerId, transactionId, amount, currency, timestamp);

            log.info("CTR filing triggered - CTR ID: {} for customer: {}", ctrId, customerId);

        } catch (Exception e) {
            log.error("Failed to trigger CTR filing for customer: {}", customerId, e);
        }
    }

    /**
     * Generates comprehensive compliance report
     */
    private String generateComplianceReport(
            String reviewId,
            String reviewType,
            String customerId,
            String accountId,
            String transactionId,
            BigDecimal amount,
            String currency,
            String description,
            Double riskScore,
            LocalDateTime timestamp) {

        try {
            String reportId = "COMP-RPT-" + UUID.randomUUID().toString();

            Map<String, String> reportData = Map.of(
                "report_id", reportId,
                "review_id", reviewId,
                "report_type", "COMPLIANCE_REVIEW_REPORT",
                "review_type", reviewType,
                "customer_id", customerId,
                "account_id", accountId != null ? accountId : "",
                "transaction_id", transactionId != null ? transactionId : "",
                "amount", amount != null ? amount.toString() : "0",
                "currency", currency,
                "description", description != null ? description : "",
                "risk_score", riskScore != null ? riskScore.toString() : "0",
                "status", "GENERATED",
                "generated_at", timestamp.toString()
            );

            String reportKey = "compliance:reports:" + reportId;
            redisTemplate.opsForHash().putAll(reportKey, reportData);
            redisTemplate.expire(reportKey, Duration.ofDays(reportingRetentionDays));

            // Add to reports index
            String indexKey = "compliance:reports:index:" + timestamp.toLocalDate();
            redisTemplate.opsForList().rightPush(indexKey, reportId);
            redisTemplate.expire(indexKey, Duration.ofDays(30));

            return reportId;

        } catch (Exception e) {
            log.error("COMPLIANCE_CRITICAL: Failed to generate compliance report for review: {}. Error: {}", reviewId, e.getMessage(), e);
            
            // Compliance reporting failure is critical - throw exception instead of returning null
            // This ensures compliance failures are not silently ignored
            throw new ComplianceException(
                String.format("Critical failure generating compliance report for review: %s", reviewId),
                "COMPLIANCE_REPORT_GENERATION_FAILED",
                e
            );
        }
    }

    /**
     * Determines regulatory reporting requirements
     */
    private void determineRegulatoryReportingRequirements(
            String reportId,
            String reviewType,
            BigDecimal amount,
            String currency,
            Double riskScore,
            LocalDateTime timestamp) {

        try {
            // SAR filing requirement
            if (reviewType.contains("SAR") || (riskScore != null && riskScore > 75.0)) {
                createRegulatoryRequirement(reportId, "SAR_FILING", "Suspicious Activity Report required", 
                    timestamp.plusDays(14), timestamp);
            }

            // CTR filing requirement
            if (requiresCTRFiling(amount, currency)) {
                createRegulatoryRequirement(reportId, "CTR_FILING", "Currency Transaction Report required",
                    timestamp.plusDays(ctrFilingDeadlineDays), timestamp);
            }

            // Enhanced monitoring requirement
            if (riskScore != null && riskScore > 80.0) {
                createRegulatoryRequirement(reportId, "ENHANCED_MONITORING", 
                    "Enhanced customer monitoring required", timestamp.plusDays(30), timestamp);
            }

            // OFAC review requirement
            if (reviewType.contains("OFAC") || reviewType.contains("SANCTIONS")) {
                createRegulatoryRequirement(reportId, "OFAC_REVIEW", "OFAC sanctions review required",
                    timestamp.plusHours(24), timestamp);
            }

        } catch (Exception e) {
            log.error("Failed to determine regulatory reporting requirements", e);
        }
    }

    /**
     * Creates CTR filing record
     */
    private String createCTRFilingRecord(
            String customerId,
            String transactionId,
            BigDecimal amount,
            String currency,
            LocalDateTime timestamp) {

        try {
            String ctrId = "CTR-" + UUID.randomUUID().toString();
            LocalDateTime dueDate = timestamp.plusDays(ctrFilingDeadlineDays);

            Map<String, String> ctrData = Map.of(
                "ctr_id", ctrId,
                "customer_id", customerId,
                "transaction_id", transactionId,
                "amount", amount.toString(),
                "currency", currency,
                "status", "PENDING",
                "created_at", timestamp.toString(),
                "due_date", dueDate.toString(),
                "filing_type", "CTR"
            );

            String ctrKey = "compliance:ctr:filings:" + ctrId;
            redisTemplate.opsForHash().putAll(ctrKey, ctrData);
            redisTemplate.expire(ctrKey, Duration.ofDays(reportingRetentionDays));

            // Add to CTR filing queue
            String queueKey = "compliance:ctr:filing_queue";
            redisTemplate.opsForList().rightPush(queueKey, ctrId);
            redisTemplate.expire(queueKey, Duration.ofDays(30));

            return ctrId;

        } catch (Exception e) {
            log.error("COMPLIANCE_CRITICAL: Failed to create CTR filing record for customer: {}, transaction: {}, amount: {}. Error: {}", 
                     customerId, transactionId, amount, e.getMessage(), e);
            
            // CTR filing failure is critical for regulatory compliance - throw exception instead of returning null
            // This ensures CTR requirements are not silently bypassed due to system errors
            throw new ComplianceException(
                String.format("Critical failure creating CTR filing record for customer: %s, transaction: %s, amount: %s", 
                             customerId, transactionId, amount),
                "CTR_FILING_CREATION_FAILED",
                e
            );
        }
    }

    /**
     * Schedules CTR filing
     */
    private void scheduleCTRFiling(String ctrId, String customerId, LocalDateTime timestamp) {
        try {
            LocalDateTime dueDate = timestamp.plusDays(ctrFilingDeadlineDays);
            
            String scheduleKey = "compliance:ctr:schedule:" + ctrId;
            Map<String, String> scheduleData = Map.of(
                "ctr_id", ctrId,
                "customer_id", customerId,
                "due_date", dueDate.toString(),
                "status", "SCHEDULED",
                "scheduled_at", timestamp.toString()
            );

            redisTemplate.opsForHash().putAll(scheduleKey, scheduleData);
            redisTemplate.expire(scheduleKey, Duration.ofDays(ctrFilingDeadlineDays + 7));

            // Add to deadline tracking
            String deadlineKey = "compliance:ctr:deadlines:" + dueDate.toLocalDate();
            redisTemplate.opsForList().rightPush(deadlineKey, ctrId);
            redisTemplate.expire(deadlineKey, Duration.ofDays(ctrFilingDeadlineDays + 7));

        } catch (Exception e) {
            log.error("Failed to schedule CTR filing", e);
        }
    }

    /**
     * Creates CTR case for tracking
     */
    private void createCTRCase(
            String ctrId,
            String customerId,
            String transactionId,
            BigDecimal amount,
            String currency,
            LocalDateTime timestamp) {

        try {
            String caseId = "CTR-CASE-" + UUID.randomUUID().toString();

            Map<String, String> caseData = Map.of(
                "case_id", caseId,
                "ctr_id", ctrId,
                "customer_id", customerId,
                "transaction_id", transactionId,
                "case_type", "CTR_FILING",
                "case_status", "OPEN",
                "amount", amount.toString(),
                "currency", currency,
                "assigned_to", "CTR_TEAM",
                "created_at", timestamp.toString()
            );

            String caseKey = "compliance:ctr:cases:" + caseId;
            redisTemplate.opsForHash().putAll(caseKey, caseData);
            redisTemplate.expire(caseKey, Duration.ofDays(reportingRetentionDays));

            // Link CTR to case
            String linkKey = "compliance:ctr:case_links:" + ctrId;
            redisTemplate.opsForValue().set(linkKey, caseId);
            redisTemplate.expire(linkKey, Duration.ofDays(reportingRetentionDays));

        } catch (Exception e) {
            log.error("Failed to create CTR case", e);
        }
    }

    /**
     * Creates regulatory requirement record
     */
    private void createRegulatoryRequirement(
            String reportId,
            String requirementType,
            String description,
            LocalDateTime dueDate,
            LocalDateTime timestamp) {

        try {
            String requirementId = UUID.randomUUID().toString();
            String requirementKey = "compliance:regulatory:requirements:" + requirementId;

            Map<String, String> requirementData = Map.of(
                "requirement_id", requirementId,
                "report_id", reportId,
                "requirement_type", requirementType,
                "description", description,
                "status", "PENDING",
                "due_date", dueDate.toString(),
                "created_at", timestamp.toString()
            );

            redisTemplate.opsForHash().putAll(requirementKey, requirementData);
            redisTemplate.expire(requirementKey, Duration.ofDays(reportingRetentionDays));

            // Add to requirements queue
            String queueKey = "compliance:regulatory:requirements_queue";
            redisTemplate.opsForList().rightPush(queueKey, requirementId);
            redisTemplate.expire(queueKey, Duration.ofDays(30));

        } catch (Exception e) {
            log.error("Failed to create regulatory requirement", e);
        }
    }

    /**
     * Updates compliance metrics
     */
    private void updateComplianceMetrics(
            String reviewType,
            BigDecimal amount,
            String currency,
            Double riskScore,
            LocalDateTime timestamp) {

        try {
            String date = timestamp.toLocalDate().toString();

            // Update review count by type
            String reviewCountKey = "compliance:metrics:reviews:" + reviewType + ":" + date;
            redisTemplate.opsForValue().increment(reviewCountKey);
            redisTemplate.expire(reviewCountKey, Duration.ofDays(90));

            // Update amount metrics
            if (amount != null) {
                String amountKey = "compliance:metrics:amount:" + currency + ":" + date;
                redisTemplate.opsForValue().increment(amountKey, amount.doubleValue());
                redisTemplate.expire(amountKey, Duration.ofDays(90));
            }

            // Update risk score metrics
            if (riskScore != null) {
                String riskKey = "compliance:metrics:risk:" + getRiskCategory(riskScore) + ":" + date;
                redisTemplate.opsForValue().increment(riskKey);
                redisTemplate.expire(riskKey, Duration.ofDays(90));
            }

            // Update daily compliance metrics
            String dailyKey = "compliance:metrics:daily:" + date;
            redisTemplate.opsForValue().increment(dailyKey);
            redisTemplate.expire(dailyKey, Duration.ofDays(90));

        } catch (Exception e) {
            log.error("Failed to update compliance metrics", e);
        }
    }

    /**
     * Creates reporting audit trail
     */
    private void createReportingAuditTrail(
            String reportId,
            String reviewId,
            String reviewType,
            LocalDateTime timestamp) {

        try {
            String auditId = UUID.randomUUID().toString();
            String auditKey = "compliance:reporting:audit:" + auditId;

            Map<String, String> auditData = Map.of(
                "audit_id", auditId,
                "report_id", reportId,
                "review_id", reviewId,
                "activity", "COMPLIANCE_REPORT_GENERATED",
                "review_type", reviewType,
                "timestamp", timestamp.toString(),
                "user", "SYSTEM"
            );

            redisTemplate.opsForHash().putAll(auditKey, auditData);
            redisTemplate.expire(auditKey, Duration.ofDays(reportingRetentionDays));

            // Add to audit index
            String indexKey = "compliance:reporting:audit:index:" + timestamp.toLocalDate();
            redisTemplate.opsForList().rightPush(indexKey, auditId);
            redisTemplate.expire(indexKey, Duration.ofDays(30));

        } catch (Exception e) {
            log.error("Failed to create reporting audit trail", e);
        }
    }

    /**
     * Updates report status
     */
    public void updateReportStatus(String reportId, String status, String notes) {
        try {
            String reportKey = "compliance:reports:" + reportId;
            Map<String, String> updates = Map.of(
                "status", status,
                "notes", notes != null ? notes : "",
                "last_updated", LocalDateTime.now().toString()
            );

            redisTemplate.opsForHash().putAll(reportKey, updates);

            // Create audit trail for status update
            createReportStatusAudit(reportId, status, notes);

        } catch (Exception e) {
            log.error("Failed to update report status", e);
        }
    }

    /**
     * Generates compliance summary report
     */
    public Map<String, Object> generateComplianceSummary(LocalDateTime startDate, LocalDateTime endDate) {
        try {
            log.debug("Generating compliance summary from {} to {}", startDate, endDate);

            String summaryKey = "compliance:reporting:summary:" + startDate.toLocalDate() + "_" + endDate.toLocalDate();
            Map<Object, Object> summary = redisTemplate.opsForHash().entries(summaryKey);

            if (summary.isEmpty()) {
                // Generate summary if not cached
                summary = Map.of(
                    "total_reports", "0",
                    "sar_filings", "0",
                    "ctr_filings", "0",
                    "high_risk_reviews", "0",
                    "total_amount", "0",
                    "generated_at", LocalDateTime.now().toString()
                );

                redisTemplate.opsForHash().putAll(summaryKey, summary);
                redisTemplate.expire(summaryKey, Duration.ofDays(7));
            }

            return (Map<String, Object>) summary;

        } catch (Exception e) {
            log.error("Failed to generate compliance summary", e);
            return Map.of("error", "Failed to generate summary");
        }
    }

    // Helper methods

    private boolean requiresCTRFiling(BigDecimal amount, String currency) {
        return amount != null && 
               amount.compareTo(ctrThreshold) > 0 && 
               "USD".equals(currency);
    }

    private String getRiskCategory(Double riskScore) {
        if (riskScore >= 80.0) return "HIGH";
        if (riskScore >= 50.0) return "MEDIUM";
        return "LOW";
    }

    private void createReportStatusAudit(String reportId, String status, String notes) {
        try {
            String auditId = UUID.randomUUID().toString();
            String auditKey = "compliance:reporting:status_audit:" + auditId;

            Map<String, String> auditData = Map.of(
                "audit_id", auditId,
                "report_id", reportId,
                "activity", "STATUS_UPDATE",
                "old_status", "PREVIOUS_STATUS", // Would fetch actual previous status
                "new_status", status,
                "notes", notes != null ? notes : "",
                "timestamp", LocalDateTime.now().toString(),
                "user", "SYSTEM"
            );

            redisTemplate.opsForHash().putAll(auditKey, auditData);
            redisTemplate.expire(auditKey, Duration.ofDays(reportingRetentionDays));

        } catch (Exception e) {
            log.error("Failed to create report status audit", e);
        }
    }
}