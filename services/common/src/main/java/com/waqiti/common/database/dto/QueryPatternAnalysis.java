package com.waqiti.common.database.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Query pattern analysis results for database optimization insights.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
public class QueryPatternAnalysis {
    private Map<String, Integer> frequentQueries;
    private List<String> slowQueries;
    private Map<String, Double> averageExecutionTimes;
    private List<String> missingIndexQueries;
    private long totalQueries;
    private double averageQueryTime;
}