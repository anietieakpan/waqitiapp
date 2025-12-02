package com.waqiti.common.database.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Index recommendation for database optimization.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexRecommendation {
    private String indexName;
    private String tableName;
    private List<String> columns;
    private String reason;
    private double expectedImprovement;
    private String sql;
    private long executionTime;
    private double score;
    private String query;
    private double averageExecutionTime;
    private long executionCount;
    private String recommendation;
    private int priority;
}