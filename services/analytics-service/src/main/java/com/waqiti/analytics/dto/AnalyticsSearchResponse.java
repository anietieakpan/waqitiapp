package com.waqiti.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for analytics search
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsSearchResponse {
    
    private Long totalResults;
    private List<Object> results;
    private String searchType;
    private Integer page;
    private Integer size;
}