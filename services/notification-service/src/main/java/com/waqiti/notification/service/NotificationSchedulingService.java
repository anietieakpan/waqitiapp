package com.waqiti.notification.service;

import com.waqiti.notification.client.*;
import com.waqiti.notification.client.dto.*;
import com.waqiti.notification.domain.NotificationCategory;
import com.waqiti.notification.dto.SendNotificationRequest;
import com.waqiti.notification.repository.NotificationHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for scheduling and automating notifications
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationSchedulingService {
    
    private final NotificationService notificationService;
    private final NotificationPreferencesService preferencesService;
    private final NotificationHistoryRepository notificationHistoryRepository;
    private final PaymentServiceClient paymentServiceClient;
    private final WalletServiceClient walletServiceClient;
    private final UserServiceClient userServiceClient;
    private final AnalyticsServiceClient analyticsServiceClient;
    
    /**
     * Schedule daily payment reminders (runs at 9 AM daily)
     */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void sendPaymentReminders() {
        log.info("Starting daily payment reminders");
        
        try {
            List<PendingPaymentResponse> pendingPayments = paymentServiceClient.getUsersWithPendingPayments();
            Map<UUID, List<PendingPaymentResponse>> paymentsByUser = pendingPayments.stream()
                .collect(Collectors.groupingBy(PendingPaymentResponse::getUserId));
            
            int remindersSent = 0;
            
            for (Map.Entry<UUID, List<PendingPaymentResponse>> entry : paymentsByUser.entrySet()) {
                UUID userId = entry.getKey();
                List<PendingPaymentResponse> userPayments = entry.getValue();
                
                // Check if user wants payment reminders and hasn't received one recently
                if (preferencesService.isNotificationEnabled(userId, 
                        NotificationCategory.RECURRING_PAYMENT_EXECUTED.getCode(), null) &&
                    !hasRecentPaymentReminder(userId)) {
                    
                    // Get payment reminder data
                    List<PaymentReminderData> reminderData = paymentServiceClient.getUserPendingPayments(userId);
                    
                    if (!reminderData.isEmpty()) {
                        Map<String, Object> parameters = buildPaymentReminderParameters(reminderData);
                        
                        SendNotificationRequest request = SendNotificationRequest.builder()
                                .userId(userId)
                                .templateCode("payment_reminder")
                                .types(new String[]{"PUSH", "APP", "EMAIL"})
                                .parameters(parameters)
                                .build();
                        
                        notificationService.sendNotification(request);
                        remindersSent++;
                    }
                }
            }
            
            log.info("Completed daily payment reminders for {} users", remindersSent);
        } catch (Exception e) {
            log.error("Error sending daily payment reminders", e);
        }
    }
    
    /**
     * Send weekly account activity summaries (runs Sundays at 6 PM)
     */
    @Scheduled(cron = "0 0 18 * * SUN")
    @Transactional
    public void sendWeeklyActivitySummaries() {
        log.info("Starting weekly activity summaries");
        
        try {
            List<UserProfileResponse> activeUsers = userServiceClient.getActiveUsers(7);
            int summariesSent = 0;
            
            for (UserProfileResponse user : activeUsers) {
                UUID userId = user.getUserId();
                
                // Check if user wants weekly summaries
                if (preferencesService.isNotificationEnabled(userId, 
                        NotificationCategory.GENERAL.getCode(), null)) {
                    
                    // Get user's weekly activity data from analytics service
                    WeeklyActivitySummary activitySummary = analyticsServiceClient.getUserWeeklyActivity(userId);
                    
                    if (activitySummary != null && activitySummary.getTransactionCount() > 0) {
                        Map<String, Object> parameters = buildWeeklySummaryParameters(activitySummary, user);
                        
                        SendNotificationRequest request = SendNotificationRequest.builder()
                                .userId(userId)
                                .templateCode("weekly_summary")
                                .types(new String[]{"EMAIL", "APP"})
                                .parameters(parameters)
                                .build();
                        
                        notificationService.sendNotification(request);
                        summariesSent++;
                    }
                }
            }
            
            log.info("Completed weekly activity summaries for {} users", summariesSent);
        } catch (Exception e) {
            log.error("Error sending weekly activity summaries", e);
        }
    }
    
    /**
     * Send low balance alerts (runs every hour during business hours)
     */
    @Scheduled(cron = "0 0 8-20 * * *")
    @Transactional
    public void sendLowBalanceAlerts() {
        log.info("Checking for low balance alerts");
        
        try {
            List<LowBalanceUserResponse> usersWithLowBalance = walletServiceClient
                .getUsersWithLowBalance(new BigDecimal("50.00"), "USD");
            
            int alertsSent = 0;
            
            for (LowBalanceUserResponse lowBalanceUser : usersWithLowBalance) {
                UUID userId = lowBalanceUser.getUserId();
                
                // Check if we haven't sent a low balance alert in the last 24 hours
                if (!hasRecentLowBalanceAlert(userId) && 
                    preferencesService.isNotificationEnabled(userId, 
                        NotificationCategory.LOW_BALANCE.getCode(), null)) {
                    
                    Map<String, Object> parameters = buildLowBalanceParameters(lowBalanceUser);
                    
                    SendNotificationRequest request = SendNotificationRequest.builder()
                            .userId(userId)
                            .templateCode("low_balance_alert")
                            .types(new String[]{"PUSH", "APP", "EMAIL"})
                            .parameters(parameters)
                            .build();
                    
                    notificationService.sendNotification(request);
                    alertsSent++;
                }
            }
            
            log.info("Processed low balance alerts - sent {} alerts to {} users", 
                alertsSent, usersWithLowBalance.size());
        } catch (Exception e) {
            log.error("Error checking low balance alerts", e);
        }
    }
    
    /**
     * Send security check reminders (runs monthly on the 1st at 10 AM)
     */
    @Scheduled(cron = "0 0 10 1 * *")
    @Transactional
    public void sendSecurityCheckReminders() {
        log.info("Starting monthly security check reminders");
        
        try {
            List<UserSecurityStatusResponse> usersNeedingReview = userServiceClient
                .getUsersNeedingSecurityReview(3);
            
            int remindersSent = 0;
            
            for (UserSecurityStatusResponse securityStatus : usersNeedingReview) {
                UUID userId = securityStatus.getUserId();
                
                // Check if user wants security notifications
                if (preferencesService.isNotificationEnabled(userId, 
                        NotificationCategory.SECURITY_ALERT.getCode(), null)) {
                    
                    Map<String, Object> parameters = buildSecurityReminderParameters(securityStatus);
                    
                    SendNotificationRequest request = SendNotificationRequest.builder()
                            .userId(userId)
                            .templateCode("security_check_reminder")
                            .types(new String[]{"PUSH", "APP", "EMAIL"})
                            .parameters(parameters)
                            .build();
                    
                    notificationService.sendNotification(request);
                    remindersSent++;
                }
            }
            
            log.info("Completed security check reminders for {} users", remindersSent);
        } catch (Exception e) {
            log.error("Error sending security check reminders", e);
        }
    }
    
    /**
     * Send birthday and anniversary notifications (runs daily at 8 AM)
     */
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void sendSpecialDateNotifications() {
        log.info("Checking for special date notifications");
        
        try {
            int birthdayNotificationsSent = 0;
            int anniversaryNotificationsSent = 0;
            
            // Birthday notifications
            List<UserProfileResponse> birthdayUsers = userServiceClient.getUsersWithBirthdayToday();
            for (UserProfileResponse user : birthdayUsers) {
                UUID userId = user.getUserId();
                
                if (preferencesService.isNotificationEnabled(userId, 
                        NotificationCategory.PROMOTIONAL_OFFER.getCode(), null)) {
                    
                    Map<String, Object> parameters = buildBirthdayParameters(user);
                    
                    SendNotificationRequest request = SendNotificationRequest.builder()
                            .userId(userId)
                            .templateCode("birthday_notification")
                            .types(new String[]{"PUSH", "APP"})
                            .parameters(parameters)
                            .build();
                    
                    notificationService.sendNotification(request);
                    birthdayNotificationsSent++;
                }
            }
            
            // Account anniversary notifications
            List<UserProfileResponse> anniversaryUsers = userServiceClient.getUsersWithAccountAnniversaryToday();
            for (UserProfileResponse user : anniversaryUsers) {
                UUID userId = user.getUserId();
                
                if (preferencesService.isNotificationEnabled(userId, 
                        NotificationCategory.ACHIEVEMENT_UNLOCKED.getCode(), null)) {
                    
                    // Get anniversary stats from analytics service
                    AnalyticsServiceClient.AnniversaryStats anniversaryStats = 
                        analyticsServiceClient.getUserAnniversaryStats(userId, user.getAccountAgeInYears());
                    
                    Map<String, Object> parameters = buildAnniversaryParameters(user, anniversaryStats);
                    
                    SendNotificationRequest request = SendNotificationRequest.builder()
                            .userId(userId)
                            .templateCode("account_anniversary")
                            .types(new String[]{"PUSH", "APP"})
                            .parameters(parameters)
                            .build();
                    
                    notificationService.sendNotification(request);
                    anniversaryNotificationsSent++;
                }
            }
            
            log.info("Processed special date notifications: {} birthday, {} anniversary users", 
                    birthdayNotificationsSent, anniversaryNotificationsSent);
        } catch (Exception e) {
            log.error("Error sending special date notifications", e);
        }
    }
    
    /**
     * Send promotional campaign notifications (configurable timing)
     */
    public void sendPromotionalCampaign(String campaignId, List<String> targetUserIds, 
                                      Map<String, Object> campaignData) {
        log.info("Starting promotional campaign: {} for {} users", campaignId, targetUserIds.size());
        
        try {
            for (String userId : targetUserIds) {
                // Check if user opts in to promotional notifications
                if (preferencesService.isNotificationEnabled(UUID.fromString(userId), 
                        NotificationCategory.PROMOTIONAL_OFFER.getCode(), null)) {
                    
                    // Get user profile to check quiet hours
                    UserProfileResponse userProfile = userServiceClient.getUserProfile(UUID.fromString(userId));
                    if (isWithinQuietHours(userProfile)) {
                        log.debug("Skipping promotional notification for user {} due to quiet hours", userId);
                        continue;
                    }
                    
                    SendNotificationRequest request = SendNotificationRequest.builder()
                            .userId(UUID.fromString(userId))
                            .templateCode("promotional_campaign")
                            .types(new String[]{"PUSH", "APP"})
                            .parameters(Map.of(
                                "campaign_id", campaignId,
                                "campaign_title", campaignData.get("title"),
                                "campaign_description", campaignData.get("description"),
                                "offer_details", campaignData.get("offer_details"),
                                "expiry_date", campaignData.get("expiry_date"),
                                "action_url", campaignData.get("action_url")
                            ))
                            .build();
                    
                    notificationService.sendNotification(request);
                    
                    // Add small delay to avoid overwhelming the system
                    Thread.sleep(50);
                }
            }
            
            log.info("Completed promotional campaign: {} notifications", campaignId);
        } catch (Exception e) {
            log.error("Error sending promotional campaign: {}", campaignId, e);
        }
    }
    
    /**
     * Schedule a future notification
     */
    public void scheduleNotification(SendNotificationRequest request, LocalDateTime scheduledTime) {
        log.info("Scheduling notification for user: {} at {}", request.getUserId(), scheduledTime);
        
        try {
            // Store in notification history with scheduled status
            // This would be picked up by processScheduledNotifications()
            notificationService.scheduleNotificationForLater(request, scheduledTime);
            
            log.info("Notification scheduled successfully for user: {} at {}", 
                request.getUserId(), scheduledTime);
        } catch (Exception e) {
            log.error("Failed to schedule notification for user: {} at {}", 
                request.getUserId(), scheduledTime, e);
            throw new RuntimeException("Failed to schedule notification", e);
        }
    }
    
    /**
     * Process scheduled notifications that are due to be sent
     */
    @Scheduled(fixedRate = 60000) // Check every minute
    @Transactional
    public void processScheduledNotifications() {
        try {
            List<NotificationHistory> scheduledNotifications = notificationHistoryRepository
                .findScheduledNotificationsToSend(LocalDateTime.now());
            
            for (NotificationHistory scheduledNotification : scheduledNotifications) {
                try {
                    // Convert back to SendNotificationRequest and send
                    SendNotificationRequest request = convertToSendRequest(scheduledNotification);
                    notificationService.sendNotification(request);
                    
                    // Update status to sent
                    scheduledNotification.setStatus("SENT");
                    scheduledNotification.setSentAt(LocalDateTime.now());
                    notificationHistoryRepository.save(scheduledNotification);
                    
                    log.debug("Sent scheduled notification: {}", scheduledNotification.getId());
                    
                } catch (Exception e) {
                    log.error("Failed to send scheduled notification: {}", 
                        scheduledNotification.getId(), e);
                    
                    // Mark as failed
                    scheduledNotification.setStatus("FAILED");
                    scheduledNotification.setErrorMessage(e.getMessage());
                    notificationHistoryRepository.save(scheduledNotification);
                }
            }
            
            if (!scheduledNotifications.isEmpty()) {
                log.info("Processed {} scheduled notifications", scheduledNotifications.size());
            }
            
        } catch (Exception e) {
            log.error("Error processing scheduled notifications", e);
        }
    }
    
    private SendNotificationRequest convertToSendRequest(NotificationHistory notification) {
        return SendNotificationRequest.builder()
            .userId(notification.getUserId())
            .templateCode(notification.getTemplateCode())
            .types(notification.getDeliveryChannels().split(","))
            .parameters(notification.getTemplateParameters() != null ? 
                parseParameters(notification.getTemplateParameters()) : Map.of())
            .build();
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParameters(String parametersJson) {
        try {
            if (parametersJson == null || parametersJson.trim().isEmpty()) {
                return Map.of();
            }
            
            // Use Jackson ObjectMapper to parse JSON
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(parametersJson, Map.class);
            
        } catch (Exception e) {
            log.warn("Failed to parse notification parameters: {}", parametersJson, e);
            return Map.of();
        }
    }
    
    /**
     * Cleanup old notification history records
     */
    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    @Transactional
    public void cleanupOldNotificationHistory() {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);
            
            // Batch delete old notification history records
            log.info("Cleaning up notification history older than {}", cutoffDate);
            
            int deletedCount = deleteOldNotificationRecords(cutoffDate);
            log.info("Deleted {} old notification records", deletedCount);
            
            log.info("Completed notification history cleanup");
        } catch (Exception e) {
            log.error("Error during notification history cleanup", e);
        }
    }
    
    // Helper methods
    
    private boolean hasRecentPaymentReminder(UUID userId) {
        return notificationHistoryRepository.existsRecentNotification(
            userId, 
            "payment_reminder", 
            LocalDateTime.now().minusDays(1)
        );
    }
    
    private Map<String, Object> buildPaymentReminderParameters(List<PaymentReminderData> reminderData) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("payment_count", reminderData.size());
        parameters.put("timestamp", LocalDateTime.now().toString());
        parameters.put("action_url", "/payments/pending");
        
        if (!reminderData.isEmpty()) {
            PaymentReminderData firstPayment = reminderData.get(0);
            parameters.put("first_payment_amount", firstPayment.getFormattedAmount());
            parameters.put("first_payment_recipient", firstPayment.getRecipientName());
            parameters.put("first_payment_due_date", firstPayment.getDueDate().toString());
            
            if (reminderData.size() > 1) {
                parameters.put("additional_payments", reminderData.size() - 1);
            }
            
            // Calculate total amount
            BigDecimal totalAmount = reminderData.stream()
                .map(PaymentReminderData::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            parameters.put("total_amount", String.format("$%.2f", totalAmount));
        }
        
        return parameters;
    }
    
    private Map<String, Object> buildWeeklySummaryParameters(WeeklyActivitySummary summary, UserProfileResponse user) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("user_name", user.getFirstName() != null ? user.getFirstName() : "User");
        parameters.put("week_start", summary.getWeekStart().toString());
        parameters.put("week_end", summary.getWeekEnd().toString());
        parameters.put("transaction_count", summary.getTransactionCount());
        parameters.put("total_spent", summary.getFormattedTotalSpent());
        parameters.put("total_received", summary.getFormattedTotalReceived());
        parameters.put("primary_currency", summary.getPrimaryCurrency());
        
        // Add comparison data if available
        if (summary.getComparisonWithLastWeek() != null) {
            WeeklyActivitySummary.ComparisonData comparison = summary.getComparisonWithLastWeek();
            parameters.put("spending_trend", comparison.getTrend());
            parameters.put("spending_change_percent", comparison.getSpentChangePercent());
            parameters.put("comparison_summary", comparison.getSummary());
        }
        
        // Add top merchants
        if (summary.getTopMerchants() != null && !summary.getTopMerchants().isEmpty()) {
            parameters.put("top_merchant", summary.getTopMerchants().get(0).getMerchantName());
            parameters.put("top_merchant_amount", summary.getTopMerchants().get(0).getFormattedAmount());
        }
        
        // Add insights and recommendations
        if (summary.getInsights() != null && !summary.getInsights().isEmpty()) {
            parameters.put("key_insight", summary.getInsights().get(0));
        }
        
        if (summary.getRecommendations() != null && !summary.getRecommendations().isEmpty()) {
            parameters.put("recommendation", summary.getRecommendations().get(0));
        }
        
        parameters.put("action_url", "/analytics/weekly-report");
        return parameters;
    }
    
    private boolean hasRecentLowBalanceAlert(UUID userId) {
        return notificationHistoryRepository.findLastLowBalanceAlert(
            userId, 
            LocalDateTime.now().minusDays(1)
        ).isPresent();
    }
    
    private Map<String, Object> buildLowBalanceParameters(LowBalanceUserResponse lowBalanceUser) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("user_name", lowBalanceUser.getUserName());
        parameters.put("current_balance", lowBalanceUser.getFormattedBalance());
        parameters.put("currency", lowBalanceUser.getCurrency());
        parameters.put("threshold", lowBalanceUser.getFormattedThreshold());
        parameters.put("balance_deficit", 
            String.format("$%.2f", lowBalanceUser.getBalanceDeficit()));
        parameters.put("action_url", "/wallet/add-funds");
        parameters.put("preferred_method", "bank_transfer");
        
        // Add last top-up information if available
        if (lowBalanceUser.getLastTopUpDate() != null) {
            parameters.put("last_topup_date", 
                lowBalanceUser.getLastTopUpDate().toLocalDate().toString());
        }
        
        return parameters;
    }
    
    private Map<String, Object> buildSecurityReminderParameters(UserSecurityStatusResponse securityStatus) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("user_name", securityStatus.getUsername());
        parameters.put("days_since_review", securityStatus.getDaysSinceLastReview());
        parameters.put("security_level", securityStatus.getSecurityLevel());
        parameters.put("risk_score", securityStatus.getRiskScore());
        parameters.put("action_url", "/security/review");
        
        if (securityStatus.getLastSecurityReviewDate() != null) {
            parameters.put("last_security_review", 
                securityStatus.getLastSecurityReviewDate().toLocalDate().toString());
        }
        
        // Add security recommendations if available
        if (securityStatus.getSecurityRecommendations() != null && 
            !securityStatus.getSecurityRecommendations().isEmpty()) {
            parameters.put("primary_recommendation", 
                securityStatus.getSecurityRecommendations().get(0));
            parameters.put("recommendation_count", 
                securityStatus.getSecurityRecommendations().size());
        }
        
        // Mark urgency if immediate review required
        if (securityStatus.isRequiresImmediateReview()) {
            parameters.put("urgency_level", "HIGH");
            parameters.put("urgent_message", "Immediate attention required for account security");
        }
        
        return parameters;
    }
    
    private Map<String, Object> buildBirthdayParameters(UserProfileResponse user) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("user_name", user.getFirstName() != null ? user.getFirstName() : "User");
        parameters.put("special_offer", "20% cashback on next transaction");
        parameters.put("offer_code", "BIRTHDAY20");
        parameters.put("expiry_date", LocalDateTime.now().plusDays(7).toLocalDate().toString());
        parameters.put("action_url", "/offers/birthday-special");
        
        // Add personalized message based on user segment
        if (user.isPremiumUser()) {
            parameters.put("special_message", "As a valued premium member, enjoy double rewards!");
            parameters.put("premium_bonus", "Extra 10% bonus cashback");
        }
        
        return parameters;
    }
    
    private Map<String, Object> buildAnniversaryParameters(UserProfileResponse user, 
                                                           AnalyticsServiceClient.AnniversaryStats stats) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("user_name", user.getFirstName() != null ? user.getFirstName() : "User");
        parameters.put("years", stats.yearsSinceJoining);
        parameters.put("total_transactions", stats.totalTransactions);
        parameters.put("total_volume", stats.totalVolume);
        parameters.put("milestone_reward", stats.milestoneReward);
        parameters.put("most_used_feature", stats.mostUsedFeature);
        parameters.put("action_url", "/account/anniversary-celebration");
        
        // Add special anniversary milestone messages
        if (stats.yearsSinceJoining == 1) {
            parameters.put("milestone_message", "Congratulations on your first year with us!");
        } else if (stats.yearsSinceJoining == 5) {
            parameters.put("milestone_message", "5 years of trust - you're truly valued!");
        } else if (stats.yearsSinceJoining >= 10) {
            parameters.put("milestone_message", "A decade of partnership - you're a legend!");
        }
        
        return parameters;
    }
    
    
    private boolean isWithinQuietHours(UserProfileResponse userProfile) {
        if (userProfile == null || userProfile.getQuietHoursStart() == null || 
            userProfile.getQuietHoursEnd() == null) {
            // Default quiet hours if not set
            return isWithinDefaultQuietHours();
        }
        
        LocalTime now = LocalTime.now();
        LocalTime quietStart = userProfile.getQuietHoursStart();
        LocalTime quietEnd = userProfile.getQuietHoursEnd();
        
        if (quietStart.isBefore(quietEnd)) {
            return now.isAfter(quietStart) && now.isBefore(quietEnd);
        } else {
            // Quiet hours span midnight
            return now.isAfter(quietStart) || now.isBefore(quietEnd);
        }
    }
    
    private boolean isWithinDefaultQuietHours() {
        LocalTime now = LocalTime.now();
        LocalTime quietStart = LocalTime.of(22, 0);  // 10 PM
        LocalTime quietEnd = LocalTime.of(8, 0);     // 8 AM
        
        return now.isAfter(quietStart) || now.isBefore(quietEnd);
    }
    
    /**
     * Delete old notification records from database
     */
    private int deleteOldNotificationRecords(LocalDateTime cutoffDate) {
        try {
            // This would use a custom repository method to batch delete
            // For now, simulate the operation
            log.debug("Simulating deletion of notifications older than {}", cutoffDate);
            
            // In real implementation:
            // return notificationHistoryRepository.deleteByCreatedAtBefore(cutoffDate);
            
            // Return simulated count for now
            return 0;
            
        } catch (Exception e) {
            log.error("Failed to delete old notification records: {}", e.getMessage());
            return 0;
        }
    }
}