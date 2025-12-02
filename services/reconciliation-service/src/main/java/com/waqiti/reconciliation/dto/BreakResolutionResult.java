package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BreakResolutionResult {

    private UUID breakId;

    private boolean resolved;

    private String resolutionMethod;

    private String resolutionNotes;

    @Builder.Default
    private LocalDateTime resolvedAt = LocalDateTime.now();

    private String resolvedBy;

    private ResolutionType resolutionType;

    private List<ResolutionAction> actionsPerformed;

    private Map<String, Object> resolutionDetails;

    private BigDecimal financialImpact;

    private String rootCause;

    private List<PreventiveMeasure> preventiveMeasures;

    public enum ResolutionType {
        AUTOMATIC_CORRECTION,
        MANUAL_ADJUSTMENT,
        SYSTEM_UPDATE,
        DATA_CORRECTION,
        PROCESS_IMPROVEMENT,
        WRITE_OFF,
        TIMING_DIFFERENCE,
        LEGITIMATE_VARIANCE,
        ESCALATED_TO_MANAGEMENT,
        REQUIRES_FURTHER_INVESTIGATION
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResolutionAction {
        private String actionType;
        private String description;
        private String performedBy;
        private LocalDateTime performedAt;
        private String status;
        private Map<String, Object> actionDetails;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreventiveMeasure {
        private String measureType;
        private String description;
        private String responsibleParty;
        private LocalDateTime targetImplementationDate;
        private String priority;
        private String status;
    }

    public boolean isSuccessfullyResolved() {
        return resolved && resolutionType != null && 
               !ResolutionType.REQUIRES_FURTHER_INVESTIGATION.equals(resolutionType) &&
               !ResolutionType.ESCALATED_TO_MANAGEMENT.equals(resolutionType);
    }

    public boolean requiresFurtherAction() {
        return ResolutionType.REQUIRES_FURTHER_INVESTIGATION.equals(resolutionType) ||
               ResolutionType.ESCALATED_TO_MANAGEMENT.equals(resolutionType);
    }

    public boolean hasFinancialImpact() {
        return financialImpact != null && financialImpact.compareTo(BigDecimal.ZERO) != 0;
    }

    public boolean hasPreventiveMeasures() {
        return preventiveMeasures != null && !preventiveMeasures.isEmpty();
    }

    public static BreakResolutionResult success(UUID breakId, String method, String notes) {
        return BreakResolutionResult.builder()
            .breakId(breakId)
            .resolved(true)
            .resolutionMethod(method)
            .resolutionNotes(notes)
            .resolutionType(ResolutionType.AUTOMATIC_CORRECTION)
            .build();
    }

    public static BreakResolutionResult manualReviewRequired(UUID breakId, String method, String notes) {
        return BreakResolutionResult.builder()
            .breakId(breakId)
            .resolved(false)
            .resolutionMethod(method)
            .resolutionNotes(notes)
            .resolutionType(ResolutionType.REQUIRES_FURTHER_INVESTIGATION)
            .build();
    }

    public static BreakResolutionResult escalated(UUID breakId, String notes) {
        return BreakResolutionResult.builder()
            .breakId(breakId)
            .resolved(false)
            .resolutionMethod("ESCALATED")
            .resolutionNotes(notes)
            .resolutionType(ResolutionType.ESCALATED_TO_MANAGEMENT)
            .build();
    }
}