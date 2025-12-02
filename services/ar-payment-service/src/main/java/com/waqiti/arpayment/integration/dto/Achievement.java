package com.waqiti.arpayment.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Achievement DTO from gamification service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Achievement {

    private String achievementId;
    private String name;
    private String description;
    private String category;
    private int points;
    private String iconUrl;
    private boolean unlocked;
    private Instant unlockedAt;
    private int progress; // 0-100
    private int targetProgress;
    private String rarity; // COMMON, RARE, EPIC, LEGENDARY
}
