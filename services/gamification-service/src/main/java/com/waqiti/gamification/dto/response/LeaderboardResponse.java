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
public class LeaderboardResponse {
    
    private String userId;
    private String username;
    private String displayName;
    private Long totalPoints;
    private String currentLevel;
    private BigDecimal cashbackRate;
    private Integer streakDays;
    private LocalDateTime lastActivityDate;
    private Long rank;
    
    public static LeaderboardResponse from(UserPoints userPoints) {
        return LeaderboardResponse.builder()
                .userId(userPoints.getUserId())
                .totalPoints(userPoints.getTotalPoints())
                .currentLevel(userPoints.getCurrentLevel().name())
                .cashbackRate(userPoints.getCashbackRate())
                .streakDays(userPoints.getStreakDays())
                .lastActivityDate(userPoints.getLastActivityDate())
                .build();
    }
    
    public static LeaderboardResponse from(UserPoints userPoints, Long rank) {
        LeaderboardResponse response = from(userPoints);
        response.setRank(rank);
        return response;
    }
}