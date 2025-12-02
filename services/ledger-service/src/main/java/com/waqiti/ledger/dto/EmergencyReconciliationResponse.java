package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyReconciliationResponse {
    private UUID reconciliationId;
    private UUID accountId;
    private BigDecimal calculatedLedgerBalance;
    private BigDecimal recordedBalance;
    private BigDecimal discrepancyAmount;
    private String severity;
    private String status;
    private boolean requiresManualReview;
    private String escalationLevel;
    private UUID reportId;
    private LocalDateTime initiatedAt;
    private LocalDateTime completedAt;
    private String errorMessage;

    @Builder.Default
    private List<String> actionsTaken = new ArrayList<>();

    public void addAction(String action) {
        this.actionsTaken.add(action);
    }
}
