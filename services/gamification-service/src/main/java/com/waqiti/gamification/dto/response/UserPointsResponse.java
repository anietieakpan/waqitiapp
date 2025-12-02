package com.waqiti.gamification.dto.response;

import com.waqiti.gamification.domain.UserPoints;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPointsResponse {
    
    private String userId;
    private Long totalPoints;
    private Long availablePoints;
    private Long redeemedPoints;
    private String currentLevel;
    private Long levelProgressPoints;
    private Long nextLevelThreshold;
    private BigDecimal cashbackRate;
    private Boolean multiplierActive;
    private BigDecimal currentMultiplier;
    private LocalDateTime multiplierExpiresAt;
    private Integer streakDays;
    private LocalDateTime lastActivityDate;
    private Long monthlyPoints;
    private Long weeklyPoints;
    private Long dailyPoints;
    
    public static UserPointsResponse from(UserPoints userPoints) {
        return UserPointsResponse.builder()
                .userId(userPoints.getUserId())
                .totalPoints(userPoints.getTotalPoints())
                .availablePoints(userPoints.getAvailablePoints())
                .redeemedPoints(userPoints.getRedeemedPoints())
                .currentLevel(userPoints.getCurrentLevel().name())
                .levelProgressPoints(userPoints.getLevelProgressPoints())
                .nextLevelThreshold(userPoints.getNextLevelThreshold())
                .cashbackRate(userPoints.getCashbackRate())
                .multiplierActive(userPoints.getMultiplierActive())
                .currentMultiplier(userPoints.getCurrentMultiplier())
                .multiplierExpiresAt(userPoints.getMultiplierExpiresAt())
                .streakDays(userPoints.getStreakDays())
                .lastActivityDate(userPoints.getLastActivityDate())
                .monthlyPoints(userPoints.getMonthlyPoints())
                .weeklyPoints(userPoints.getWeeklyPoints())
                .dailyPoints(userPoints.getDailyPoints())
                .build();
    }
}