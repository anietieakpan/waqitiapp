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
public class TrainingModuleContent {
    private UUID moduleId;
    private String title;
    private List<Map<String, Object>> sections;
    private Integer totalQuestions;
    private Integer passingScore;
}