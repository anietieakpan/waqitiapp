package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementReconciliationRequest {

    @NotNull(message = "Settlement ID is required")
    private UUID settlementId;

    @NotNull(message = "Settlement date is required")
    private LocalDate settlementDate;

    private String externalReference;

    private String counterpartyId;

    private String initiatedBy;

    @Builder.Default
    private boolean forceReconciliation = false;

    @Builder.Default
    private ReconciliationMode reconciliationMode = ReconciliationMode.AUTOMATIC;

    private LocalDateTime reconciliationCutoffTime;

    private Map<String, Object> additionalValidations;

    public enum ReconciliationMode {
        AUTOMATIC("Automatic Reconciliation"),
        MANUAL("Manual Reconciliation"),
        SEMI_AUTOMATIC("Semi-Automatic Reconciliation");

        private final String description;

        ReconciliationMode(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public boolean isAutomaticMode() {
        return ReconciliationMode.AUTOMATIC.equals(reconciliationMode);
    }

    public boolean isManualMode() {
        return ReconciliationMode.MANUAL.equals(reconciliationMode);
    }

    public boolean shouldForceReconciliation() {
        return forceReconciliation;
    }

    public boolean hasCutoffTime() {
        return reconciliationCutoffTime != null;
    }
}