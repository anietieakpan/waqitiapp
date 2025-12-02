package com.waqiti.common.security.awareness.dto;

import lombok.*;

import java.util.List;

/**
 * Assessment Question DTO
 *
 * @author Waqiti Platform Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentQuestion {

    private String id;
    private String question;
    private List<String> options;
    private String correctAnswer;
    private String explanation;
    private Integer points;
}