package com.waqiti.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.Map;

/**
 * Request DTO for analytics search
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsSearchRequest {
    
    @NotBlank
    private String searchType; // USER, MERCHANT, TRANSACTION, ANOMALY
    
    private String query;
    private Instant startDate;
    private Instant endDate;
    private Map<String, Object> filters;
    private String sortBy;
    private String sortOrder;
    private Integer page;
    private Integer size;
}