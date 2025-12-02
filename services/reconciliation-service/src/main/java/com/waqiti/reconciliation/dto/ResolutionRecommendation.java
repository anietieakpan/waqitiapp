package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolutionRecommendation {

    private String recommendationType;
    
    private String description;
    
    private String priority;
    
    private boolean autoExecutable;
    
    private String estimatedEffort;
    
    private String expectedBenefit;
    
    private BigDecimal estimatedCost;
    
    private String implementationTimeframe;
    
    private List<String> prerequisites;
    
    private List<String> risks;
    
    private double successProbability;
    
    private String responsibleParty;
    
    private Map<String, Object> parameters;
    
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum Priority {
        CRITICAL("Critical", 1),
        HIGH("High", 2),
        MEDIUM("Medium", 3),
        LOW("Low", 4);

        private final String description;
        private final int level;

        Priority(String description, int level) {
            this.description = description;
            this.level = level;
        }

        public String getDescription() {
            return description;
        }

        public int getLevel() {
            return level;
        }
    }

    public enum EffortLevel {
        MINIMAL("Minimal", "< 1 hour"),
        LOW("Low", "1-4 hours"),
        MEDIUM("Medium", "4-16 hours"),
        HIGH("High", "1-3 days"),
        EXTENSIVE("Extensive", "> 3 days");

        private final String description;
        private final String timeEstimate;

        EffortLevel(String description, String timeEstimate) {
            this.description = description;
            this.timeEstimate = timeEstimate;
        }

        public String getDescription() {
            return description;
        }

        public String getTimeEstimate() {
            return timeEstimate;
        }
    }

    public boolean isAutoExecutable() {
        return autoExecutable;
    }

    public boolean isCriticalPriority() {
        return "CRITICAL".equalsIgnoreCase(priority);
    }

    public boolean isHighPriority() {
        return "HIGH".equalsIgnoreCase(priority);
    }

    public boolean isLowEffort() {
        return "MINIMAL".equalsIgnoreCase(estimatedEffort) || 
               "LOW".equalsIgnoreCase(estimatedEffort);
    }

    public boolean hasRisks() {
        return risks != null && !risks.isEmpty();
    }

    public boolean hasPrerequisites() {
        return prerequisites != null && !prerequisites.isEmpty();
    }

    public boolean isHighSuccessProbability() {
        return successProbability >= 0.8;
    }

    public boolean isLowSuccessProbability() {
        return successProbability < 0.5;
    }

    public boolean isCostEffective() {
        // Simple cost-effectiveness check
        return estimatedCost != null && 
               estimatedCost.compareTo(new BigDecimal("1000.00")) <= 0;
    }
}