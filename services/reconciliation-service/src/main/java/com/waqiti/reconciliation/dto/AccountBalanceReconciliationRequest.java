package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountBalanceReconciliationRequest {

    @NotNull(message = "Account ID is required")
    private UUID accountId;

    @NotNull(message = "As of date is required")
    private LocalDateTime asOfDate;

    private String currency;

    private BigDecimal expectedBalance;

    private String reconciliationType;

    @Builder.Default
    private boolean includeUnauthorizedTransactions = false;

    @Builder.Default
    private boolean includePendingTransactions = true;

    private String initiatedBy;

    @Builder.Default
    private ReconciliationScope scope = ReconciliationScope.FULL;

    private BigDecimal toleranceAmount;

    private Double tolerancePercentage;

    private Map<String, Object> additionalFilters;

    private String businessDate;

    public enum ReconciliationScope {
        FULL("Full Reconciliation"),
        INCREMENTAL("Incremental Reconciliation"),
        BALANCE_ONLY("Balance Only"),
        TRANSACTION_LEVEL("Transaction Level");

        private final String description;

        ReconciliationScope(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public boolean hasToleranceAmount() {
        return toleranceAmount != null && toleranceAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean hasTolerancePercentage() {
        return tolerancePercentage != null && tolerancePercentage > 0.0;
    }

    public boolean isFullReconciliation() {
        return ReconciliationScope.FULL.equals(scope);
    }

    public boolean shouldIncludeUnauthorized() {
        return includeUnauthorizedTransactions;
    }

    public boolean shouldIncludePending() {
        return includePendingTransactions;
    }
}