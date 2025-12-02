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
public class PointsEarnedEvent {
    private String userId;
    private Long pointsEarned;
    private String eventType;
    private Long totalPoints;
    private String referenceId;
    private String sourceService;
    private LocalDateTime timestamp;
}