package com.waqiti.common.fraud.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List; /**
 * Behavior patterns
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorPatterns {
    private List<String> identifiedPatterns;
    private double patternStrength;
    private LocalDateTime lastPatternDate;
}
