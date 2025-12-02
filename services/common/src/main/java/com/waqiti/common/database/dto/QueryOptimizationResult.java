package com.waqiti.common.database.dto;

import lombok.Data;
import java.util.List;

/**
 * Query optimization result.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
public class QueryOptimizationResult {
    private String originalQuery;
    private String optimizedQuery;
    private double expectedImprovement;
    private List<String> optimizationSteps;
    private long estimatedExecutionTime;
}