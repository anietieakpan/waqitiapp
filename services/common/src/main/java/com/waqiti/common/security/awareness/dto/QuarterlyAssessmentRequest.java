package com.waqiti.common.security.awareness.dto;

import com.waqiti.common.security.awareness.domain.QuarterlySecurityAssessment;
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
public class QuarterlyAssessmentRequest {
    @NotBlank(message = "Assessment name is required")
    private String assessmentName;

    @NotNull(message = "Quarter is required")
    @ValidQuarter
    private Integer quarter;

    @NotNull(message = "Year is required")
    @Min(value = 2020, message = "Year must be 2020 or later")
    private Integer year;

    @NotNull(message = "Assessment type is required")
    private QuarterlySecurityAssessment.AssessmentType assessmentType;

    @NotEmpty(message = "Target roles are required")
    private List<String> targetRoles;

    @NotNull(message = "Available from date is required")
    private LocalDateTime availableFrom;

    @NotNull(message = "Available until date is required")
    private LocalDateTime availableUntil;

    @NotEmpty(message = "Questions are required")
    private List<AssessmentQuestion> questions;

    @NotNull(message = "Passing score percentage is required")
    @Min(value = 0, message = "Passing score must be between 0 and 100")
    @Max(value = 100, message = "Passing score must be between 0 and 100")
    private Integer passingScorePercentage;

    private Integer timeLimitMinutes;

    private UUID createdBy;
}

