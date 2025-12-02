package com.waqiti.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

/**
 * Audit Statistics Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditStatisticsResponse {
    private Long totalEvents;
    private Map<String, Long> eventsByType;
    private Map<String, Long> eventsBySeverity;
    private Map<String, Long> eventsByEntityType;
    private LocalDate startDate;
    private LocalDate endDate;
}
