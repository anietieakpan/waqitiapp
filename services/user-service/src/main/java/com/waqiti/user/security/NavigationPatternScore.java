package com.waqiti.user.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Navigation pattern analysis score
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NavigationPatternScore {
    private double score;
    private double pageSequenceScore;
    private double timeOnPageVariance;
    private double navigationSpeedScore;
}