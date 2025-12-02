package com.waqiti.audit.model;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Audit statistics response containing aggregated metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditStatisticsResponse {
    
    @JsonProperty("total_events")
    private Long totalEvents;
    
    @JsonProperty("events_by_type")
    private Map<String, Long> eventsByType;
    
    @JsonProperty("events_by_severity")
    private Map<String, Long> eventsBySeverity;
    
    @JsonProperty("events_by_entity_type")
    private Map<String, Long> eventsByEntityType;
    
    @JsonProperty("daily_event_counts")
    private List<DailyEventCount> dailyEventCounts;
    
    @JsonProperty("period_start")
    private LocalDate periodStart;
    
    @JsonProperty("period_end")
    private LocalDate periodEnd;
    
    @JsonProperty("events_by_result")
    private Map<String, Long> eventsByResult;
    
    @JsonProperty("events_by_service")
    private Map<String, Long> eventsByService;
    
    @JsonProperty("compliance_distribution")
    private Map<String, Long> complianceDistribution;
    
    @JsonProperty("risk_score_distribution")
    private Map<String, Long> riskScoreDistribution;
}