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
public class BadgeUnlockedEvent {
    private String userId;
    private Long badgeId;
    private String badgeName;
    private String badgeCode;
    private String category;
    private String tier;
    private Long pointsRewarded;
    private String iconUrl;
    private LocalDateTime timestamp;
}