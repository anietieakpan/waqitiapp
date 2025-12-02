package com.waqiti.rewards.service;

import com.waqiti.rewards.domain.ReferralMilestone;
import com.waqiti.rewards.domain.ReferralMilestoneAchievement;
import com.waqiti.rewards.repository.ReferralMilestoneRepository;
import com.waqiti.rewards.repository.ReferralMilestoneAchievementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing referral milestones and achievements
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReferralMilestoneService {

    private final ReferralMilestoneRepository milestoneRepository;
    private final ReferralMilestoneAchievementRepository achievementRepository;

    /**
     * Checks and processes milestone achievements for a user
     */
    @Transactional
    public List<ReferralMilestone> checkAndProcessMilestones(UUID userId, String programId,
                                                             int referralCount, int conversionCount,
                                                             BigDecimal revenue) {
        log.debug("Checking milestones: user={}, program={}, referrals={}, conversions={}",
                userId, programId, referralCount, conversionCount);

        List<ReferralMilestone> eligibleMilestones = milestoneRepository
                .findEligibleMilestones(programId, referralCount, conversionCount);

        List<ReferralMilestone> newAchievements = new java.util.ArrayList<>();

        for (ReferralMilestone milestone : eligibleMilestones) {
            if (milestone.isCriteriaMet(referralCount, conversionCount, revenue)) {
                boolean shouldAward = shouldAwardMilestone(userId, milestone);

                if (shouldAward) {
                    createAchievement(userId, milestone, referralCount, conversionCount, revenue);
                    newAchievements.add(milestone);
                    log.info("Milestone achieved: user={}, milestone={}", userId, milestone.getMilestoneName());
                }
            }
        }

        return newAchievements;
    }

    /**
     * Determines if milestone should be awarded
     */
    private boolean shouldAwardMilestone(UUID userId, ReferralMilestone milestone) {
        if (!milestone.getIsRepeatable()) {
            return !achievementRepository.existsByUserIdAndMilestone_MilestoneId(
                    userId, milestone.getMilestoneId());
        }

        if (milestone.getCooldownPeriodDays() != null) {
            List<ReferralMilestoneAchievement> history = achievementRepository
                    .findUserMilestoneHistory(userId, milestone.getMilestoneId());

            if (!history.isEmpty()) {
                LocalDateTime lastAchievement = history.get(0).getAchievedAt();
                return milestone.canAchieveAgain(lastAchievement);
            }
        }

        return true;
    }

    /**
     * Creates a milestone achievement
     */
    private ReferralMilestoneAchievement createAchievement(UUID userId, ReferralMilestone milestone,
                                                          int referralCount, int conversionCount,
                                                          BigDecimal revenue) {
        String achievementId = "ACH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("referrals", referralCount);
        data.put("conversions", conversionCount);
        data.put("revenue", revenue.toString());

        ReferralMilestoneAchievement achievement = ReferralMilestoneAchievement.builder()
                .achievementId(achievementId)
                .milestone(milestone)
                .userId(userId)
                .achievementData(data)
                .build();

        return achievementRepository.save(achievement);
    }

    /**
     * Gets active milestones for a program
     */
    public List<ReferralMilestone> getActiveMilestones(String programId) {
        return milestoneRepository.findActiveMilestonesOrdered(programId);
    }

    /**
     * Gets user achievements
     */
    public List<ReferralMilestoneAchievement> getUserAchievements(UUID userId) {
        return achievementRepository.findByUserId(userId);
    }

    /**
     * Gets pending reward issuances
     */
    public List<ReferralMilestoneAchievement> getPendingRewardIssuances() {
        return achievementRepository.findByRewardIssuedFalse();
    }

    /**
     * Marks reward as issued
     */
    @Transactional
    public void markRewardIssued(String achievementId, String rewardId) {
        ReferralMilestoneAchievement achievement = achievementRepository
                .findByAchievementId(achievementId)
                .orElseThrow(() -> new IllegalArgumentException("Achievement not found: " + achievementId));

        achievement.markRewardIssued(rewardId);
        achievementRepository.save(achievement);
        log.info("Marked reward issued: achievement={}, reward={}", achievementId, rewardId);
    }

    /**
     * Gets achievements needing notification
     */
    public List<ReferralMilestoneAchievement> getAchievementsNeedingNotification() {
        return achievementRepository.findByUserNotifiedFalse();
    }

    /**
     * Marks user as notified
     */
    @Transactional
    public void markUserNotified(String achievementId) {
        ReferralMilestoneAchievement achievement = achievementRepository
                .findByAchievementId(achievementId)
                .orElseThrow(() -> new IllegalArgumentException("Achievement not found: " + achievementId));

        achievement.markUserNotified();
        achievementRepository.save(achievement);
    }
}
