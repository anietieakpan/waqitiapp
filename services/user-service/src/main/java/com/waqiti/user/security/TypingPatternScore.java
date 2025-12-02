package com.waqiti.user.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Typing pattern analysis score
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypingPatternScore {
    private double score;
    private int samplesAnalyzed;
    private double dwellTimeVariance;
    private double flightTimeVariance;
}