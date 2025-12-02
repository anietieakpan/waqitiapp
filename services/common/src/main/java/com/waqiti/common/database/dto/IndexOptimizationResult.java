package com.waqiti.common.database.dto;

import lombok.Data;
import java.time.Instant;
import java.util.List;

/**
 * Index optimization result.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
public class IndexOptimizationResult {
    private Instant timestamp;
    private List<IndexRecommendation> recommendations;
    private List<IndexCreationResult> indexCreationResults;
    private List<String> createdIndexes;
    private List<String> droppedIndexes;
    private int totalRecommendations;
    private int successfulCreations;
    private int analyzedTables;
    private double performanceImprovement;
    private long executionTimeMs;
}