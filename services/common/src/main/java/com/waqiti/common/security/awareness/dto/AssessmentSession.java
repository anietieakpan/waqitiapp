package com.waqiti.common.security.awareness.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Assessment Session DTO
 *
 * @author Waqiti Platform Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentSession {

    private UUID sessionId;
    private UUID assessmentId;
    private UUID employeeId;
    private List<AssessmentQuestion> questions;
    private LocalDateTime startedAt;
    private LocalDateTime expiresAt;
    private Integer timeLimitMinutes;
    private Integer questionsAnswered;
    private Integer totalQuestions;
}