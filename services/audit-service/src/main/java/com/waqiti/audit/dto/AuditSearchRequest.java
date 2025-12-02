package com.waqiti.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Audit Search Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditSearchRequest {
    private String entityType;
    private String entityId;
    private String eventType;
    private String userId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String severity;
}
