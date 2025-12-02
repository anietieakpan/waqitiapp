package com.waqiti.analytics.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Optimized query model for analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptimizedQuery {
    
    private Map<String, Object> filters;
    private String sortBy;
    private String sortOrder;
    private Integer limit;
    private Integer offset;
    private Map<String, String> hints;
    private Double estimatedCost;
    private String queryId;
    private Boolean useCache;
}