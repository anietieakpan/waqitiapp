package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementReconciliationResult {

    private UUID settlementId;

    private boolean reconciled;

    private UUID breakId;

    private List<SettlementDiscrepancy> discrepancies;

    private String message;

    @Builder.Default
    private LocalDateTime reconciledAt = LocalDateTime.now();

    private String reconciledBy;

    private Long processingTimeMs;

    private ReconciliationStatus status;

    private SettlementMatchDetails matchDetails;

    private String externalConfirmationReference;

    public enum ReconciliationStatus {
        MATCHED,
        DISCREPANCY_DETECTED,
        CONFIRMATION_PENDING,
        TIMEOUT,
        ERROR,
        MANUAL_REVIEW_REQUIRED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettlementDiscrepancy {
        private String discrepancyType;
        private String fieldName;
        private String expectedValue;
        private String actualValue;
        private BigDecimal amountDifference;
        private String description;
        private String severity;
        private boolean autoResolvable;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettlementMatchDetails {
        private boolean amountMatched;
        private boolean dateMatched;
        private boolean currencyMatched;
        private boolean referenceMatched;
        private boolean counterpartyMatched;
        private double overallMatchScore;
        private String matchingAlgorithmUsed;
        private LocalDateTime matchedAt;
    }

    public boolean hasDiscrepancies() {
        return discrepancies != null && !discrepancies.isEmpty();
    }

    public boolean isSuccessful() {
        return reconciled && ReconciliationStatus.MATCHED.equals(status);
    }

    public boolean requiresManualReview() {
        return ReconciliationStatus.MANUAL_REVIEW_REQUIRED.equals(status) ||
               ReconciliationStatus.DISCREPANCY_DETECTED.equals(status);
    }

    public boolean hasAutoResolvableDiscrepancies() {
        return discrepancies != null && 
               discrepancies.stream().anyMatch(d -> d.autoResolvable);
    }

    public List<SettlementDiscrepancy> getCriticalDiscrepancies() {
        if (discrepancies == null) {
            return List.of();
        }
        return discrepancies.stream()
            .filter(d -> "CRITICAL".equalsIgnoreCase(d.severity) || "HIGH".equalsIgnoreCase(d.severity))
            .toList();
    }

    public static SettlementReconciliationResult success(UUID settlementId, String message) {
        return SettlementReconciliationResult.builder()
            .settlementId(settlementId)
            .reconciled(true)
            .status(ReconciliationStatus.MATCHED)
            .message(message)
            .build();
    }

    public static SettlementReconciliationResult discrepancyDetected(UUID settlementId, UUID breakId, 
                                                                   List<SettlementDiscrepancy> discrepancies, 
                                                                   String message) {
        return SettlementReconciliationResult.builder()
            .settlementId(settlementId)
            .reconciled(false)
            .breakId(breakId)
            .discrepancies(discrepancies)
            .status(ReconciliationStatus.DISCREPANCY_DETECTED)
            .message(message)
            .build();
    }

    public static SettlementReconciliationResult error(UUID settlementId, String message) {
        return SettlementReconciliationResult.builder()
            .settlementId(settlementId)
            .reconciled(false)
            .status(ReconciliationStatus.ERROR)
            .message(message)
            .build();
    }
}