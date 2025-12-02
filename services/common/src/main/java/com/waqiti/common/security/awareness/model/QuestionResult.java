package com.waqiti.common.security.awareness.model;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionResult {
    private UUID questionId;
    private String employeeAnswer;
    private String correctAnswer;
    private Boolean isCorrect;
    private String explanation;
}