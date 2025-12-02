package com.waqiti.common.database;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a predicted query pattern and its characteristics
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PredictedQuery {
    private String queryPattern;
    private double probability;
    private Instant predictedExecutionTime;
    private ResourceRequirements resourceRequirements;
    private Map<String, Object> optimizationHints;
    private String recommendedConnectionPool;
    private int recommendedFetchSize;
    private boolean shouldCache;
    private long expectedCacheTtl;
    private long estimatedDurationMs;
    private boolean readOnly;
    
    public double getProbability() {
        return probability;
    }
    
    public long getEstimatedDurationMs() {
        return estimatedDurationMs > 0 ? estimatedDurationMs : 
            (resourceRequirements != null ? resourceRequirements.getEstimatedDurationMs() : 100L);
    }
    
    public ResourceRequirements getResourceRequirements() {
        if (resourceRequirements == null) {
            resourceRequirements = ResourceRequirements.createDefault();
        }
        return resourceRequirements;
    }
    
    public long getEstimatedExecutionTime() {
        return getEstimatedDurationMs();
    }
    
    public String getQueryId() {
        return queryPattern != null ? queryPattern.hashCode() + "" : "unknown";
    }
    
    public double getImpactScore() {
        return probability * (getEstimatedDurationMs() / 1000.0);
    }
    
    public boolean isReadOnly() {
        return readOnly;
    }
}