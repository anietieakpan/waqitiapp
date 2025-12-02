package com.waqiti.arpayment.integration.client;

import com.waqiti.arpayment.domain.ARPaymentExperience;
import com.waqiti.arpayment.integration.dto.Achievement;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Feign client for Gamification Service integration
 * Provides achievements, points, and gamification features
 */
@FeignClient(
    name = "gamification-service",
    url = "${feign.client.config.gamification-service.url:http://gamification-service/api/v1}",
    fallback = GamificationServiceFallback.class
)
public interface GamificationServiceClient {

    /**
     * Check for achievements earned from AR payment experience
     * @param userId User ID
     * @param experience AR payment experience
     * @return List of achievements earned
     */
    @PostMapping("/gamification/achievements/check")
    List<Achievement> checkARPaymentAchievements(
        @RequestParam("userId") UUID userId,
        @RequestBody ARPaymentExperience experience
    );

    /**
     * Get AR payment streak for user
     * @param userId User ID
     * @return Number of consecutive days with AR payments
     */
    @GetMapping("/gamification/streaks/ar-payment/{userId}")
    int getARPaymentStreak(@PathVariable("userId") UUID userId);

    /**
     * Award points to user
     * @param userId User ID
     * @param points Points to award
     * @param reason Reason for awarding points
     * @param category Points category
     * @return Updated total points
     */
    @PostMapping("/gamification/points/award")
    int awardPoints(
        @RequestParam("userId") UUID userId,
        @RequestParam("points") int points,
        @RequestParam("reason") String reason,
        @RequestParam("category") String category
    );

    /**
     * Get user's total points
     * @param userId User ID
     * @return Total points
     */
    @GetMapping("/gamification/points/{userId}")
    int getUserPoints(@PathVariable("userId") UUID userId);

    /**
     * Check if user has unlocked specific achievement
     * @param userId User ID
     * @param achievementId Achievement ID
     * @return true if unlocked
     */
    @GetMapping("/gamification/achievements/{userId}/{achievementId}")
    boolean hasAchievement(
        @PathVariable("userId") UUID userId,
        @PathVariable("achievementId") String achievementId
    );

    /**
     * Get user's achievement progress
     * @param userId User ID
     * @return List of achievements with progress
     */
    @GetMapping("/gamification/achievements/progress/{userId}")
    List<Achievement> getAchievementProgress(@PathVariable("userId") UUID userId);
}
