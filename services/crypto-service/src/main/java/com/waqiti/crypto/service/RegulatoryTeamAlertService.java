package com.waqiti.crypto.service;

import com.waqiti.common.notification.DlqNotificationAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Regulatory Team Alert Service
 * Specialized wrapper around DlqNotificationAdapter for regulatory team notifications
 * Sends targeted alerts to compliance team members with context and priority
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegulatoryTeamAlertService {

    private final DlqNotificationAdapter notificationAdapter;

    /**
     * Alert regulatory team about high-priority compliance issue
     */
    @Async
    public void alertRegulatoryTeamForHighPriorityIssue(
            String transactionId,
            String customerId,
            String issueType,
            String issueDetails,
            String correlationId) {

        log.warn("Alerting regulatory team for high-priority issue: transaction={} customer={} issue={} correlationId={}",
                transactionId, customerId, issueType, correlationId);

        Map<String, Object> alertData = buildTeamAlertPayload(
                transactionId,
                customerId,
                issueType,
                issueDetails,
                "HIGH",
                correlationId
        );

        try {
            notificationAdapter.sendNotification(
                    "REGULATORY_TEAM_HIGH_PRIORITY",
                    "High Priority Compliance Issue",
                    String.format("Regulatory team attention required - Transaction: %s - Issue: %s",
                            transactionId, issueType),
                    alertData,
                    correlationId
            );

            log.warn("Regulatory team alerted for high-priority issue: transaction={} correlationId={}",
                    transactionId, correlationId);

        } catch (Exception e) {
            log.error("Failed to alert regulatory team: transaction={} correlationId={}",
                    transactionId, correlationId, e);
        }
    }

    /**
     * Alert regulatory team about pending review queue buildup
     */
    @Async
    public void alertTeamAboutReviewBacklog(
            int pendingReviews,
            int overdueReviews,
            String correlationId) {

        log.warn("Alerting regulatory team about review backlog: pending={} overdue={} correlationId={}",
                pendingReviews, overdueReviews, correlationId);

        Map<String, Object> alertData = new HashMap<>();
        alertData.put("alertType", "REVIEW_BACKLOG");
        alertData.put("severity", overdueReviews > 0 ? "HIGH" : "MEDIUM");
        alertData.put("pendingReviews", pendingReviews);
        alertData.put("overdueReviews", overdueReviews);
        alertData.put("correlationId", correlationId);
        alertData.put("timestamp", Instant.now());

        try {
            notificationAdapter.sendNotification(
                    "REVIEW_BACKLOG",
                    "Compliance Review Backlog Alert",
                    String.format("Review backlog - Pending: %d - Overdue: %d", pendingReviews, overdueReviews),
                    alertData,
                    correlationId
            );

            log.warn("Regulatory team alerted about review backlog: correlationId={}", correlationId);

        } catch (Exception e) {
            log.error("Failed to alert team about review backlog: correlationId={}", correlationId, e);
        }
    }

    /**
     * Alert regulatory team about customer block action
     */
    @Async
    public void alertTeamAboutCustomerBlock(
            String customerId,
            String blockType,
            String blockReason,
            String correlationId) {

        log.warn("Alerting regulatory team about customer block: customer={} type={} reason={} correlationId={}",
                customerId, blockType, blockReason, correlationId);

        Map<String, Object> alertData = new HashMap<>();
        alertData.put("alertType", "CUSTOMER_BLOCKED");
        alertData.put("severity", blockType.equals("PERMANENT") ? "HIGH" : "MEDIUM");
        alertData.put("customerId", customerId);
        alertData.put("blockType", blockType);
        alertData.put("blockReason", blockReason);
        alertData.put("correlationId", correlationId);
        alertData.put("timestamp", Instant.now());

        try {
            notificationAdapter.sendNotification(
                    "CUSTOMER_BLOCKED",
                    "Customer Account Blocked",
                    String.format("Customer blocked - ID: %s - Type: %s - Reason: %s",
                            customerId, blockType, blockReason),
                    alertData,
                    correlationId
            );

            log.warn("Regulatory team alerted about customer block: customer={} correlationId={}",
                    customerId, correlationId);

        } catch (Exception e) {
            log.error("Failed to alert team about customer block: customer={} correlationId={}",
                    customerId, correlationId, e);
        }
    }

    /**
     * Alert regulatory team about SAR filing
     */
    @Async
    public void alertTeamAboutSARFiling(
            String transactionId,
            String customerId,
            String violationType,
            String correlationId) {

        log.info("Alerting regulatory team about SAR filing: transaction={} customer={} violation={} correlationId={}",
                transactionId, customerId, violationType, correlationId);

        Map<String, Object> alertData = new HashMap<>();
        alertData.put("alertType", "SAR_FILED");
        alertData.put("severity", "MEDIUM");
        alertData.put("transactionId", transactionId);
        alertData.put("customerId", customerId);
        alertData.put("violationType", violationType);
        alertData.put("correlationId", correlationId);
        alertData.put("timestamp", Instant.now());

        try {
            notificationAdapter.sendNotification(
                    "SAR_FILED",
                    "Suspicious Activity Report Filed",
                    String.format("SAR filed - Transaction: %s - Customer: %s - Violation: %s",
                            transactionId, customerId, violationType),
                    alertData,
                    correlationId
            );

            log.info("Regulatory team alerted about SAR filing: transaction={} correlationId={}",
                    transactionId, correlationId);

        } catch (Exception e) {
            log.error("Failed to alert team about SAR filing: transaction={} correlationId={}",
                    transactionId, correlationId, e);
        }
    }

    /**
     * Build team alert payload
     */
    private Map<String, Object> buildTeamAlertPayload(
            String transactionId,
            String customerId,
            String issueType,
            String issueDetails,
            String severity,
            String correlationId) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("alertType", "REGULATORY_TEAM_ALERT");
        payload.put("severity", severity);
        payload.put("transactionId", transactionId);
        payload.put("customerId", customerId);
        payload.put("issueType", issueType);
        payload.put("issueDetails", issueDetails);
        payload.put("correlationId", correlationId);
        payload.put("timestamp", Instant.now());
        payload.put("targetTeam", "REGULATORY_COMPLIANCE");

        return payload;
    }
}
