package com.waqiti.support.dto;

import com.waqiti.support.domain.KnowledgeArticle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIResponse {
    private String response;
    private double confidence;
    private String intent;
    private List<KnowledgeArticle> suggestedArticles;
    private boolean needsHumanHandoff;
    private boolean error;
    private String errorMessage;
    private Map<String, Object> metadata;
    private LocalDateTime timestamp;
    
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}