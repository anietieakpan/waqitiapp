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
public class ChatbotResponse {
    private String sessionId;
    private String message;
    private List<String> quickReplies;
    private double confidence;
    private boolean needsHumanHandoff;
    private boolean requiresHumanAgent;
    private List<KnowledgeArticle> suggestedArticles;
    private List<KnowledgeArticle> relatedArticles;
    private boolean error;
    private String errorMessage;
    private Map<String, Object> metadata;
    private LocalDateTime timestamp;
    
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}