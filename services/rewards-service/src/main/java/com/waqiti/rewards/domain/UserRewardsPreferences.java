package com.waqiti.rewards.domain;

import com.waqiti.rewards.enums.RedemptionMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRewardsPreferences {
    
    @Column(name = "cashback_enabled")
    @Builder.Default
    private boolean cashbackEnabled = true;
    
    @Column(name = "points_enabled")
    @Builder.Default
    private boolean pointsEnabled = true;
    
    @Column(name = "notifications_enabled")
    @Builder.Default
    private boolean notificationsEnabled = true;
    
    @Column(name = "auto_redeem_cashback")
    @Builder.Default
    private boolean autoRedeemCashback = false;
    
    @Column(name = "auto_redeem_threshold", precision = 19, scale = 4)
    @Builder.Default
    private String autoRedeemThreshold = "100.00";
    
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_redemption_method")
    @Builder.Default
    private RedemptionMethod preferredRedemptionMethod = RedemptionMethod.WALLET_CREDIT;
    
    @Column(name = "email_notifications")
    @Builder.Default
    private boolean emailNotifications = true;
    
    @Column(name = "push_notifications")
    @Builder.Default
    private boolean pushNotifications = true;
    
    @Column(name = "sms_notifications")
    @Builder.Default
    private boolean smsNotifications = false;
    
    @Column(name = "weekly_summary")
    @Builder.Default
    private boolean weeklySummary = true;
    
    @Column(name = "campaign_offers")
    @Builder.Default
    private boolean campaignOffers = true;
}