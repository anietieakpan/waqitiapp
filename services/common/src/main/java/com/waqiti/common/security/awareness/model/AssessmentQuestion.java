package com.waqiti.common.security.awareness.model;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentQuestion {
    private UUID questionId;
    private String questionText;
    private List<String> options;
    private String correctAnswer;
    private String explanation;
}