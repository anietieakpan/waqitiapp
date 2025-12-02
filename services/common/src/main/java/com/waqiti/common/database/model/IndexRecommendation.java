package com.waqiti.common.database.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class IndexRecommendation {
    private String tableName;
    private List<String> columnNames;
    private String recommendationType; // CREATE, DROP, MODIFY
    private String reason;
    private double potentialPerformanceGain;
    private String sqlStatement;
    private int priority;
    private String sql;
    private Long executionTime;
    private Double score;
    private String indexName;
}