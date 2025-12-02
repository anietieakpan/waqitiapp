package com.waqiti.user.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Interaction pattern analysis score
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractionPatternScore {
    private double score;
    private double mouseMovementPattern;
    private double clickPatternScore;
    private double touchPressureVariance;
}