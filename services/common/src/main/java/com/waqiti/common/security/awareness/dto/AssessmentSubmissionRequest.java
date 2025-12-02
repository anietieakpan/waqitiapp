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
public class AssessmentSubmissionRequest {
    @NotNull(message = "Answers are required")
    @NotEmpty(message = "At least one answer is required")
    private Map<UUID, String> answers;
}