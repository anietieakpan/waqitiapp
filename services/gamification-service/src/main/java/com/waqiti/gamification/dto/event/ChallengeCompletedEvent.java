package com.waqiti.gamification.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeCompletedEvent {
    private String userId;
    private Long challengeId;
    private String challengeTitle;
    private String challengeType;
    private String category;
    private Long pointsEarned;
    private Long bonusPointsEarned;
    private Integer completionPercentage;
    private Long completionTimeSeconds;
    private LocalDateTime timestamp;
}