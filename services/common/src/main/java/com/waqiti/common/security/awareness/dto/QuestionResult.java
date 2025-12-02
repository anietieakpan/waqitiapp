package com.waqiti.common.security.awareness.dto;

import lombok.*;

/**
 * Question Result DTO
 *
 * @author Waqiti Platform Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionResult {

    private String questionId;
    private String question;
    private String selectedAnswer;
    private String correctAnswer;
    private Boolean isCorrect;
    private String explanation;
}