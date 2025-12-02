package com.waqiti.security.service;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Data class for time pattern analysis results
 */
@Data
@Builder
public class TimePatternAnalysis {
    private List<HourlyStats> hourlyDistribution;
    private List<WeeklyStats> weeklyDistribution;
    private boolean hasOffHoursActivity;
    private boolean hasWeekendActivity;
}