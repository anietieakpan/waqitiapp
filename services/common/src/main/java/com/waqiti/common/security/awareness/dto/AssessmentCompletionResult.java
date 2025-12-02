package com.waqiti.common.security.awareness.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Assessment Completion Result DTO
 *
 * @author Waqiti Platform Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentCompletionResult {

    private UUID resultId;
    private UUID assessmentId;
    private UUID employeeId;
    private BigDecimal score;
    private Boolean passed;
    private Integer questionsAnswered;
    private Integer correctAnswers;
    private List<QuestionResult> questionResults;
    private Integer durationMinutes;
    private LocalDateTime completedAt;
    private String certificateUrl;
}