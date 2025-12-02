package com.waqiti.rewards.service.impl;

import com.waqiti.rewards.domain.CashbackTransaction;
import com.waqiti.rewards.domain.PointsTransaction;
import com.waqiti.rewards.domain.RedemptionTransaction;
import com.waqiti.rewards.enums.LoyaltyTier;
import com.waqiti.rewards.repository.UserPreferencesRepository;
import com.waqiti.rewards.service.NotificationService;
import com.waqiti.common.events.EventPublisher;
import com.waqiti.common.events.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
    
    private final EventPublisher eventPublisher;
    private final UserPreferencesRepository preferencesRepository;
    
    @Override
    public void sendCashbackEarnedNotification(String userId, CashbackTransaction cashback) {
        if (!shouldSendNotification(userId)) {
            return;
        }
        
        try {
            String title = "Cashback Earned!";
            String message = String.format("You earned $%.2f cashback from %s", 
                cashback.getCashbackAmount(), 
                cashback.getMerchantName() != null ? cashback.getMerchantName() : "your purchase");
            
            Map<String, Object> data = Map.of(
                "type", "CASHBACK_EARNED",
                "cashbackAmount", cashback.getCashbackAmount(),
                "merchantName", cashback.getMerchantName() != null ? cashback.getMerchantName() : "",
                "transactionId", cashback.getTransactionId() != null ? cashback.getTransactionId() : ""
            );
            
            publishNotification(userId, title, message, data);
            
            log.debug("Sent cashback earned notification to user: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to send cashback earned notification to user {}: {}", 
                userId, e.getMessage(), e);
        }
    }
    
    @Override
    public void sendPointsEarnedNotification(String userId, PointsTransaction points) {
        if (!shouldSendNotification(userId)) {
            return;
        }
        
        try {
            String title = "Points Earned!";
            String message = String.format("You earned %d points: %s", 
                points.getPoints(), points.getDescription());
            
            Map<String, Object> data = Map.of(
                "type", "POINTS_EARNED",
                "points", points.getPoints(),
                "description", points.getDescription() != null ? points.getDescription() : "",
                "source", points.getSource().name()
            );
            
            publishNotification(userId, title, message, data);
            
            log.debug("Sent points earned notification to user: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to send points earned notification to user {}: {}", 
                userId, e.getMessage(), e);
        }
    }
    
    @Override
    public void sendRedemptionNotification(String userId, RedemptionTransaction redemption) {
        if (!shouldSendNotification(userId)) {
            return;
        }
        
        try {
            String title = "Redemption Successful!";
            String message = redemption.getDescription() != null ? 
                redemption.getDescription() : 
                String.format("Your %s redemption was successful", redemption.getType().name());
            
            Map<String, Object> data = Map.of(
                "type", "REDEMPTION_COMPLETED",
                "redemptionType", redemption.getType().name(),
                "amount", redemption.getAmount() != null ? redemption.getAmount() : BigDecimal.ZERO,
                "pointsAmount", redemption.getPoints() != null ? redemption.getPoints() : 0L,
                "method", redemption.getMethod().name()
            );
            
            publishNotification(userId, title, message, data);
            
            log.debug("Sent redemption notification to user: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to send redemption notification to user {}: {}", 
                userId, e.getMessage(), e);
        }
    }
    
    @Override
    public void sendTierUpgradeNotification(String userId, LoyaltyTier newTier) {
        if (!shouldSendNotification(userId)) {
            return;
        }
        
        try {
            String title = "Tier Upgrade!";
            String message = String.format("Congratulations! You've been upgraded to %s tier", 
                newTier.name());
            
            Map<String, Object> data = Map.of(
                "type", "TIER_UPGRADE",
                "newTier", newTier.name(),
                "benefits", getTierBenefits(newTier)
            );
            
            publishNotification(userId, title, message, data);
            
            log.debug("Sent tier upgrade notification to user: {} (new tier: {})", userId, newTier);
            
        } catch (Exception e) {
            log.error("Failed to send tier upgrade notification to user {}: {}", 
                userId, e.getMessage(), e);
        }
    }
    
    @Override
    public void sendPointsExpiryWarning(String userId, long pointsExpiring, int daysUntilExpiry) {
        if (!shouldSendNotification(userId)) {
            return;
        }
        
        try {
            String title = "Points Expiring Soon!";
            String message = String.format("%d points will expire in %d days. Use them before they're gone!", 
                pointsExpiring, daysUntilExpiry);
            
            Map<String, Object> data = Map.of(
                "type", "POINTS_EXPIRY_WARNING",
                "pointsExpiring", pointsExpiring,
                "daysUntilExpiry", daysUntilExpiry
            );
            
            publishNotification(userId, title, message, data);
            
            log.debug("Sent points expiry warning to user: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to send points expiry warning to user {}: {}", 
                userId, e.getMessage(), e);
        }
    }
    
    @Override
    public void sendPointsExpiryNotification(String userId, long pointsExpired) {
        if (!shouldSendNotification(userId)) {
            return;
        }
        
        try {
            String title = "Points Expired";
            String message = String.format("%d points have expired from your account", pointsExpired);
            
            Map<String, Object> data = Map.of(
                "type", "POINTS_EXPIRED",
                "pointsExpired", pointsExpired
            );
            
            publishNotification(userId, title, message, data);
            
            log.debug("Sent points expiry notification to user: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to send points expiry notification to user {}: {}", 
                userId, e.getMessage(), e);
        }
    }
    
    @Override
    public void sendWelcomeNotification(String userId) {
        try {
            String title = "Welcome to Waqiti Rewards!";
            String message = "Start earning cashback and points on every purchase. Check out your rewards dashboard!";
            
            Map<String, Object> data = Map.of(
                "type", "WELCOME",
                "action", "VIEW_DASHBOARD"
            );
            
            publishNotification(userId, title, message, data);
            
            log.debug("Sent welcome notification to user: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to send welcome notification to user {}: {}", 
                userId, e.getMessage(), e);
        }
    }
    
    @Override
    public void sendCampaignNotification(String userId, String campaignId, String campaignName) {
        if (!shouldSendNotification(userId)) {
            return;
        }
        
        try {
            String title = "New Rewards Campaign!";
            String message = String.format("Check out the new %s campaign and earn extra rewards!", 
                campaignName);
            
            Map<String, Object> data = Map.of(
                "type", "CAMPAIGN",
                "campaignId", campaignId,
                "campaignName", campaignName
            );
            
            publishNotification(userId, title, message, data);
            
            log.debug("Sent campaign notification to user: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to send campaign notification to user {}: {}", 
                userId, e.getMessage(), e);
        }
    }
    
    @Override
    public void sendRewardsSummaryNotification(String userId, String period) {
        if (!shouldSendNotification(userId)) {
            return;
        }
        
        try {
            String title = String.format("%s Rewards Summary", 
                period.substring(0, 1).toUpperCase() + period.substring(1));
            String message = String.format("Here's your %s rewards summary. Keep earning!", period);
            
            Map<String, Object> data = Map.of(
                "type", "REWARDS_SUMMARY",
                "period", period
            );
            
            publishNotification(userId, title, message, data);
            
            log.debug("Sent rewards summary notification to user: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to send rewards summary notification to user {}: {}", 
                userId, e.getMessage(), e);
        }
    }
    
    @Override
    public void sendPromotionalNotification(String userId, String title, String message) {
        if (!shouldSendNotification(userId) || !shouldSendMarketing(userId)) {
            return;
        }
        
        try {
            Map<String, Object> data = Map.of(
                "type", "PROMOTIONAL"
            );
            
            publishNotification(userId, title, message, data);
            
            log.debug("Sent promotional notification to user: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to send promotional notification to user {}: {}", 
                userId, e.getMessage(), e);
        }
    }
    
    @Override
    public void sendAutoRedemptionNotification(String userId, RedemptionTransaction redemption) {
        if (!shouldSendNotification(userId)) {
            return;
        }
        
        try {
            String title = "Auto-Redemption Completed";
            String message = String.format("We've automatically redeemed $%.2f to your wallet", 
                redemption.getAmount());
            
            Map<String, Object> data = Map.of(
                "type", "AUTO_REDEMPTION",
                "amount", redemption.getAmount()
            );
            
            publishNotification(userId, title, message, data);
            
            log.debug("Sent auto-redemption notification to user: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to send auto-redemption notification to user {}: {}", 
                userId, e.getMessage(), e);
        }
    }
    
    @Override
    public void sendAccountActivityNotification(String userId, String activity) {
        if (!shouldSendNotification(userId)) {
            return;
        }
        
        try {
            String title = "Account Activity";
            String message = activity;
            
            Map<String, Object> data = Map.of(
                "type", "ACCOUNT_ACTIVITY",
                "activity", activity
            );
            
            publishNotification(userId, title, message, data);
            
            log.debug("Sent account activity notification to user: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to send account activity notification to user {}: {}", 
                userId, e.getMessage(), e);
        }
    }
    
    @Override
    public void sendBonusNotification(String userId, String description, BigDecimal amount) {
        if (!shouldSendNotification(userId)) {
            return;
        }
        
        try {
            String title = "Bonus Received!";
            String message = String.format("%s: $%.2f", description, amount);
            
            Map<String, Object> data = Map.of(
                "type", "BONUS_RECEIVED",
                "description", description,
                "amount", amount
            );
            
            publishNotification(userId, title, message, data);
            
            log.debug("Sent bonus notification to user: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to send bonus notification to user {}: {}", 
                userId, e.getMessage(), e);
        }
    }
    
    private void publishNotification(String userId, String title, String message, Map<String, Object> data) {
        NotificationEvent event = NotificationEvent.builder()
            .userId(userId)
            .title(title)
            .message(message)
            .data(data)
            .build();
            
        eventPublisher.publish(event);
    }
    
    private boolean shouldSendNotification(String userId) {
        try {
            return preferencesRepository.findByUserId(userId)
                .map(prefs -> prefs.isNotificationsEnabled())
                .orElse(true); // Default to enabled
        } catch (Exception e) {
            log.warn("Failed to check notification preferences for user {}: {}", 
                userId, e.getMessage());
            return true; // Default to enabled on error
        }
    }
    
    private boolean shouldSendMarketing(String userId) {
        try {
            return preferencesRepository.findByUserId(userId)
                .map(prefs -> prefs.isMarketingOptIn())
                .orElse(false); // Default to disabled for marketing
        } catch (Exception e) {
            log.warn("Failed to check marketing preferences for user {}: {}", 
                userId, e.getMessage());
            return false; // Default to disabled on error
        }
    }
    
    private String getTierBenefits(LoyaltyTier tier) {
        switch (tier) {
            case SILVER:
                return "1.25x points multiplier, enhanced cashback rates";
            case GOLD:
                return "1.5x points multiplier, premium cashback rates, priority support";
            case PLATINUM:
                return "2x points multiplier, maximum cashback rates, VIP support, exclusive perks";
            default:
                return "Standard rewards program benefits";
        }
    }

    @Override
    public void sendRewardsWelcomeNotification(String userId, String message) {
        if (!shouldSendNotification(userId)) {
            return;
        }

        try {
            String title = "Welcome to Waqiti Rewards!";

            Map<String, Object> data = Map.of(
                "type", "REWARDS_WELCOME",
                "timestamp", java.time.Instant.now().toString()
            );

            publishNotification(userId, title, message, data);

            log.debug("Sent rewards welcome notification to user: {}", userId);

        } catch (Exception e) {
            log.error("Failed to send rewards welcome notification to user {}: {}",
                userId, e.getMessage(), e);
        }
    }
}"