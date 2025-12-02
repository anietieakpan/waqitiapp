package com.waqiti.gamification.dto.event;

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
public class LevelUpEvent {
    private String userId;
    private String previousLevel;
    private String newLevel;
    private Long totalPoints;
    private BigDecimal newCashbackRate;
    private Long nextLevelThreshold;
    private LocalDateTime timestamp;
}