package com.waqiti.support.dto;

import com.waqiti.support.domain.TicketCategory;
import com.waqiti.support.domain.TicketSubCategory;
import com.waqiti.support.domain.TicketPriority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategorizationResult {
    
    private TicketCategory suggestedCategory;
    private TicketSubCategory suggestedSubCategory;
    private TicketPriority suggestedPriority;
    
    private double categoryConfidence;
    private double subCategoryConfidence;
    private double priorityConfidence;
    
    private Set<String> extractedTags;
    private List<String> detectedKeywords;
    
    private boolean isUrgent;
    private boolean isSecurityRelated;
    private boolean requiresSpecialistAttention;
    
    private double escalationProbability;
    private double sentimentScore;
    
    private String reasoning;
    private List<String> alternativeCategories;
    
    private CategorizationMethod method;
    private String modelVersion;
    
    // Routing suggestions
    private List<String> suggestedAgentSkills;
    private String suggestedTeam;
    private Integer estimatedResolutionTimeHours;
    
    // Quality indicators
    private boolean highConfidence;
    private boolean requiresHumanReview;
    private List<String> uncertaintyReasons;
    
    public enum CategorizationMethod {
        RULE_BASED,
        MACHINE_LEARNING,
        HYBRID,
        MANUAL_OVERRIDE
    }
}