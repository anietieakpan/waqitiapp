package com.waqiti.arpayment.integration;

import com.waqiti.arpayment.domain.ARPaymentExperience;
import com.waqiti.arpayment.integration.client.GamificationServiceClient;
import com.waqiti.arpayment.integration.dto.Achievement;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Gamification service wrapper with async processing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GamificationService {

    private final GamificationServiceClient gamificationServiceClient;
    private final MeterRegistry meterRegistry;

    /**
     * Check for achievements earned from AR payment (async)
     */
    @Async
    public CompletableFuture<List<Achievement>> checkARPaymentAchievementsAsync(
            UUID userId, ARPaymentExperience experience) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Checking achievements for AR payment experience: {}", experience.getExperienceId());

                List<Achievement> achievements = gamificationServiceClient.checkARPaymentAchievements(
                        userId, experience);

                if (!achievements.isEmpty()) {
                    log.info("User {} earned {} achievement(s)", userId, achievements.size());
                    meterRegistry.counter("gamification.achievements.earned",
                            "count", String.valueOf(achievements.size())).increment();
                }

                return achievements;

            } catch (Exception e) {
                log.error("Failed to check achievements for user: {}", userId, e);
                return Collections.emptyList();
            }
        });
    }

    /**
     * Award points to user with metrics
     */
    public int awardPoints(UUID userId, int points, String reason, String category) {
        try {
            log.info("Awarding {} points to user: {} for: {}", points, userId, reason);

            int totalPoints = gamificationServiceClient.awardPoints(userId, points, reason, category);

            meterRegistry.counter("gamification.points.awarded",
                    "category", category).increment(points);

            return totalPoints;

        } catch (Exception e) {
            log.error("Failed to award points to user: {}", userId, e);
            return 0;
        }
    }

    /**
     * Get user's AR payment streak
     */
    public int getARPaymentStreak(UUID userId) {
        try {
            return gamificationServiceClient.getARPaymentStreak(userId);
        } catch (Exception e) {
            log.error("Failed to get AR payment streak for user: {}", userId, e);
            return 0;
        }
    }
}
