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
public class LeaderboardUpdatedEvent {
    private String userId;
    private String leaderboardType;
    private String category;
    private String periodType;
    private Integer newRank;
    private Integer previousRank;
    private Integer rankChange;
    private Long score;
    private LocalDateTime timestamp;
}