package com.waqiti.rewards.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for sending referral-related notifications
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReferralNotificationService {

    // TODO: Inject NotificationService from common or create integration

    /**
     * Notifies user of reward earned
     */
    public void notifyRewardEarned(UUID userId, String rewardId, String rewardType, String amount) {
        log.info("Sending reward notification: user={}, reward={}, type={}, amount={}",
                userId, rewardId, rewardType, amount);

        // TODO: Implement notification sending
        // notificationService.send(userId, "REWARD_EARNED", buildMessage(rewardType, amount));
    }

    /**
     * Notifies user of milestone achievement
     */
    public void notifyMilestoneAchieved(UUID userId, String milestoneName, String rewardDescription) {
        log.info("Sending milestone notification: user={}, milestone={}",
                userId, milestoneName);

        // TODO: Implement notification sending
    }

    /**
     * Notifies user of tier upgrade
     */
    public void notifyTierUpgrade(UUID userId, String oldTier, String newTier) {
        log.info("Sending tier upgrade notification: user={}, {} -> {}",
                userId, oldTier, newTier);

        // TODO: Implement notification sending
    }

    /**
     * Notifies user of referral conversion
     */
    public void notifyReferralConverted(UUID referrerId, UUID refereeId) {
        log.info("Sending referral conversion notification: referrer={}, referee={}",
                referrerId, refereeId);

        // TODO: Implement notification sending
    }

    /**
     * Notifies user of expiring rewards
     */
    public void notifyRewardExpiringSoon(UUID userId, String rewardId, int daysRemaining) {
        log.info("Sending expiry warning: user={}, reward={}, days={}",
                userId, rewardId, daysRemaining);

        // TODO: Implement notification sending
    }
}
