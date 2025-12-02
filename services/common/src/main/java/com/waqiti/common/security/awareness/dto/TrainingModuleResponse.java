package com.waqiti.common.security.awareness.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import com.waqiti.common.security.awareness.validation.ValidQuarter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

// ============================================================================
// TRAINING MODULE DTOs
// ============================================================================

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingModuleResponse {
    private UUID moduleId;
    private String title;
    private String description;
    private Integer estimatedDurationMinutes;
    private String status;
    private Integer scorePercentage;
    private LocalDateTime completedAt;
}