package com.waqiti.analytics.dto.pattern;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimingPattern {
    private String preferredTime; // MORNING, AFTERNOON, EVENING
    private Map<Integer, Double> hourlyDistribution; // Hour -> percentage
    private Map<String, Double> dayOfWeekDistribution; // Day -> percentage
    private String seasonality; // HIGH, MEDIUM, LOW
    private String consistency; // CONSISTENT, VARIABLE
}