package com.waqiti.common.kafka.dlq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseHistoryDto {
    private String action; // CREATED, ASSIGNED, RESOLVED, REJECTED, RETRIED
    private String performedBy;
    private String notes;
    private LocalDateTime timestamp;
}
