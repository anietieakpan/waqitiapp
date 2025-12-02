package com.waqiti.common.security.awareness.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import com.waqiti.common.security.awareness.validation.ValidQuarter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentStatistics {
    private UUID assessmentId;
    private String assessmentName;
    private Integer quarter;
    private Integer year;
    private Long totalAssigned;
    private Long totalCompleted;
    private Long totalPassed;
    private Long totalFailed;
    private BigDecimal completionRate;
    private BigDecimal passRate;
    private BigDecimal averageScore;
}
