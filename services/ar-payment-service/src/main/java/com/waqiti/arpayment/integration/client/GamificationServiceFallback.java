package com.waqiti.arpayment.integration.client;

import com.waqiti.arpayment.domain.ARPaymentExperience;
import com.waqiti.arpayment.integration.dto.Achievement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Fallback implementation for Gamification Service client
 * Gracefully degrades gamification features when service unavailable
 */
@Slf4j
@Component
public class GamificationServiceFallback implements GamificationServiceClient {

    @Override
    public List<Achievement> checkARPaymentAchievements(UUID userId, ARPaymentExperience experience) {
        log.warn("Gamification service unavailable - achievements check skipped for user: {}", userId);
        return Collections.emptyList(); // Graceful degradation: no achievements awarded
    }

    @Override
    public int getARPaymentStreak(UUID userId) {
        log.warn("Gamification service unavailable - returning 0 streak for user: {}", userId);
        return 0; // Graceful degradation: return no streak
    }

    @Override
    public int awardPoints(UUID userId, int points, String reason, String category) {
        log.warn("Gamification service unavailable - points not awarded to user: {}", userId);
        // TODO: Queue points for awarding when service is available
        return 0; // Graceful degradation: return current points as 0
    }

    @Override
    public int getUserPoints(UUID userId) {
        log.warn("Gamification service unavailable - returning 0 points for user: {}", userId);
        return 0;
    }

    @Override
    public boolean hasAchievement(UUID userId, String achievementId) {
        log.warn("Gamification service unavailable - achievement check skipped");
        return false; // Graceful degradation: assume no achievement
    }

    @Override
    public List<Achievement> getAchievementProgress(UUID userId) {
        log.warn("Gamification service unavailable - returning empty progress for user: {}", userId);
        return Collections.emptyList();
    }
}
