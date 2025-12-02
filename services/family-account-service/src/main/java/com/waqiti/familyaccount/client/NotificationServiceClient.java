package com.waqiti.familyaccount.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * Notification Service Feign Client
 *
 * Feign client for interacting with the notification-service microservice.
 * Handles all notification delivery for family account events including:
 * - Push notifications
 * - Email notifications
 * - SMS notifications
 * - In-app notifications
 *
 * Circuit Breaker Configuration:
 * - Name: notification-service
 * - Failure Rate Threshold: 70%
 * - Sliding Window Size: 5 requests
 * - Wait Duration in Open State: 5 seconds
 *
 * Note: Notifications are non-critical operations. Failures are logged but
 * do not cause the primary operation to fail. Circuit breaker has higher
 * failure tolerance (70%) to avoid blocking critical business operations.
 *
 * @author Waqiti Family Account Team
 * @version 1.0.0
 * @since 2025-11-19
 */
@FeignClient(
    name = "notification-service",
    url = "${feign.client.config.notification-service.url}",
    configuration = NotificationServiceClientConfig.class
)
public interface NotificationServiceClient {

    /**
     * Send family account created notification
     *
     * Sends a welcome notification to the primary parent when a new family
     * account is successfully created. Includes information about:
     * - Family account setup completion
     * - Next steps (adding family members)
     * - Educational resources
     * - Support contact information
     *
     * Delivery Channels: Email + Push + In-App
     *
     * @param familyId The unique identifier of the created family account
     * @param primaryParentUserId The user ID of the primary parent
     * @param familyName The name of the family account
     * @throws feign.FeignException if the service call fails (logged, not thrown to caller)
     */
    @PostMapping("/api/v1/notifications/family-account/created")
    void sendFamilyAccountCreatedNotification(
        @RequestParam("familyId") String familyId,
        @RequestParam("primaryParentUserId") String primaryParentUserId,
        @RequestParam("familyName") String familyName
    );

    /**
     * Send member invitation notification
     *
     * Sends an invitation notification to a user who has been added to a family account.
     * The notification includes:
     * - Family name and primary parent info
     * - Instructions to accept invitation
     * - Link to family account setup
     * - Privacy and security information
     *
     * Delivery Channels: Email + SMS + Push (if registered)
     *
     * @param userId The user ID of the invited member
     * @param familyName The name of the family account
     * @param invitedBy The name of the parent who sent the invitation
     * @throws feign.FeignException if the service call fails (logged, not thrown to caller)
     */
    @PostMapping("/api/v1/notifications/family-account/member-invited")
    void sendMemberInvitationNotification(
        @RequestParam("userId") String userId,
        @RequestParam("familyName") String familyName,
        @RequestParam("invitedBy") String invitedBy
    );

    /**
     * Send transaction authorization notification
     *
     * Sends a notification to a family member about the result of their
     * transaction authorization request. Includes:
     * - Transaction amount and merchant
     * - Authorization status (approved/declined)
     * - Reason for decline (if applicable)
     * - Remaining spending limits
     * - Parent contact info (for declined transactions)
     *
     * Delivery Channels: Push + In-App (immediate), Email (summary)
     *
     * @param userId The user ID of the family member
     * @param transactionId The unique transaction attempt ID
     * @param amount The transaction amount
     * @param authorized true if authorized, false if declined
     * @throws feign.FeignException if the service call fails (logged, not thrown to caller)
     */
    @PostMapping("/api/v1/notifications/family-account/transaction-authorization")
    void sendTransactionAuthorizationNotification(
        @RequestParam("userId") String userId,
        @RequestParam("transactionId") String transactionId,
        @RequestParam("amount") BigDecimal amount,
        @RequestParam("authorized") boolean authorized
    );

    /**
     * Send parent approval request notification
     *
     * Sends an urgent notification to parent(s) when a child initiates a
     * transaction that requires parental approval. Includes:
     * - Child's name and transaction details
     * - Merchant/recipient information
     * - Transaction amount and category
     * - Quick approve/decline action buttons
     * - Approval timeout (24 hours)
     *
     * Delivery Channels: Push + SMS (high priority)
     *
     * @param parentUserId The user ID of the parent
     * @param memberId The family member ID requesting approval
     * @param transactionId The transaction attempt ID
     * @param amount The transaction amount
     * @throws feign.FeignException if the service call fails (logged, not thrown to caller)
     */
    @PostMapping("/api/v1/notifications/family-account/parent-approval-required")
    void sendParentApprovalRequestNotification(
        @RequestParam("parentUserId") String parentUserId,
        @RequestParam("memberId") String memberId,
        @RequestParam("transactionId") String transactionId,
        @RequestParam("amount") BigDecimal amount
    );

    /**
     * Send allowance payment notification
     *
     * Sends a notification to a family member when their allowance has been
     * paid into their wallet. Includes:
     * - Allowance amount
     * - New wallet balance
     * - Next allowance payment date
     * - Savings goal progress (if applicable)
     * - Motivational message for younger children
     *
     * Delivery Channels: Push + In-App + Email
     *
     * @param userId The user ID of the family member
     * @param amount The allowance amount paid
     * @throws feign.FeignException if the service call fails (logged, not thrown to caller)
     */
    @PostMapping("/api/v1/notifications/family-account/allowance-paid")
    void sendAllowancePaymentNotification(
        @RequestParam("userId") String userId,
        @RequestParam("amount") BigDecimal amount
    );

    /**
     * Send spending limit alert notification
     *
     * Sends a proactive notification when a family member is approaching
     * or has reached their spending limit (80% threshold). Includes:
     * - Limit type (daily/weekly/monthly)
     * - Current spending vs. limit
     * - Remaining balance
     * - Reset date for the limit
     * - Tips for budgeting
     *
     * Delivery Channels: Push + In-App
     *
     * @param userId The user ID of the family member
     * @param limitType The type of limit (DAILY, WEEKLY, MONTHLY)
     * @param currentSpent The amount already spent in this period
     * @param limit The total spending limit
     * @throws feign.FeignException if the service call fails (logged, not thrown to caller)
     */
    @PostMapping("/api/v1/notifications/family-account/spending-limit-alert")
    void sendSpendingLimitAlert(
        @RequestParam("userId") String userId,
        @RequestParam("limitType") String limitType,
        @RequestParam("currentSpent") BigDecimal currentSpent,
        @RequestParam("limit") BigDecimal limit
    );

    /**
     * Send member suspended notification
     *
     * Notifies a family member that their account has been temporarily suspended
     * by their parent(s). Includes suspension reason and parent contact info.
     *
     * Delivery Channels: Push + Email
     *
     * @param userId The user ID of the suspended member
     * @param familyName The family account name
     * @param reason The reason for suspension
     * @throws feign.FeignException if the service call fails (logged, not thrown to caller)
     */
    @PostMapping("/api/v1/notifications/family-account/member-suspended")
    void sendMemberSuspendedNotification(
        @RequestParam("userId") String userId,
        @RequestParam("familyName") String familyName,
        @RequestParam("reason") String reason
    );

    /**
     * Send member reactivated notification
     *
     * Notifies a family member that their suspension has been lifted and
     * their account is now active again.
     *
     * Delivery Channels: Push + Email
     *
     * @param userId The user ID of the reactivated member
     * @param familyName The family account name
     * @throws feign.FeignException if the service call fails (logged, not thrown to caller)
     */
    @PostMapping("/api/v1/notifications/family-account/member-reactivated")
    void sendMemberReactivatedNotification(
        @RequestParam("userId") String userId,
        @RequestParam("familyName") String familyName
    );

    /**
     * Send spending rule violation notification
     *
     * Notifies parents when a family member violates a spending rule
     * (e.g., attempts purchase from blocked merchant, during restricted hours).
     *
     * Delivery Channels: Push + In-App
     *
     * @param parentUserId The user ID of the parent
     * @param memberId The family member ID who violated the rule
     * @param ruleType The type of rule violated
     * @param details Additional violation details
     * @throws feign.FeignException if the service call fails (logged, not thrown to caller)
     */
    @PostMapping("/api/v1/notifications/family-account/rule-violation")
    void sendSpendingRuleViolationNotification(
        @RequestParam("parentUserId") String parentUserId,
        @RequestParam("memberId") String memberId,
        @RequestParam("ruleType") String ruleType,
        @RequestParam("details") String details
    );

    /**
     * Send daily spending summary notification
     *
     * Sends a daily summary to parents showing all family member transactions
     * for the day. Scheduled for evening delivery (configurable time).
     *
     * Delivery Channels: Email + In-App
     *
     * @param parentUserId The user ID of the parent
     * @param familyId The family account ID
     * @throws feign.FeignException if the service call fails (logged, not thrown to caller)
     */
    @PostMapping("/api/v1/notifications/family-account/daily-summary")
    void sendDailySpendingSummaryNotification(
        @RequestParam("parentUserId") String parentUserId,
        @RequestParam("familyId") String familyId
    );

    /**
     * Send educational milestone notification
     *
     * Celebrates when a family member completes an educational milestone
     * (e.g., completes financial literacy quiz, earns badge).
     *
     * Delivery Channels: Push + In-App
     *
     * @param userId The user ID of the family member
     * @param milestoneType The type of milestone achieved
     * @param milestoneTitle The title/name of the milestone
     * @throws feign.FeignException if the service call fails (logged, not thrown to caller)
     */
    @PostMapping("/api/v1/notifications/family-account/educational-milestone")
    void sendEducationalMilestoneNotification(
        @RequestParam("userId") String userId,
        @RequestParam("milestoneType") String milestoneType,
        @RequestParam("milestoneTitle") String milestoneTitle
    );

    /**
     * Send savings goal achieved notification
     *
     * Celebrates when a family member reaches their savings goal.
     *
     * Delivery Channels: Push + Email + In-App
     *
     * @param userId The user ID of the family member
     * @param goalName The name of the savings goal
     * @param goalAmount The amount saved
     * @throws feign.FeignException if the service call fails (logged, not thrown to caller)
     */
    @PostMapping("/api/v1/notifications/family-account/savings-goal-achieved")
    void sendSavingsGoalAchievedNotification(
        @RequestParam("userId") String userId,
        @RequestParam("goalName") String goalName,
        @RequestParam("goalAmount") BigDecimal goalAmount
    );
}
