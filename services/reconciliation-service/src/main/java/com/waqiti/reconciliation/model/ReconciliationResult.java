package com.waqiti.reconciliation.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Result of reconciliation operation
 */
@Data
public class ReconciliationResult {
    
    private final String reconciliationId;
    private final LocalDateTime startTime;
    private LocalDateTime endTime;
    
    private final Set<String> matchedTransactions;
    private final Set<String> pendingTransactions;
    private final List<Discrepancy> discrepancies;
    private final Map<String, String> errors;
    private final Map<String, Object> metrics;
    
    private BigDecimal totalDiscrepancyAmount;

    public ReconciliationResult(String reconciliationId) {
        this.reconciliationId = reconciliationId;
        this.startTime = LocalDateTime.now();
        this.matchedTransactions = new HashSet<>();
        this.pendingTransactions = new HashSet<>();
        this.discrepancies = new ArrayList<>();
        this.errors = new HashMap<>();
        this.metrics = new HashMap<>();
        this.totalDiscrepancyAmount = BigDecimal.ZERO;
    }

    // Helper methods for adding results
    public void addMatchedTransaction(String transactionId, String providerTransactionId) {
        matchedTransactions.add(transactionId);
        metrics.put(transactionId + "_matched_with", providerTransactionId);
    }

    public void addPendingTransaction(String transactionId) {
        pendingTransactions.add(transactionId);
    }

    public void addDiscrepancy(Discrepancy discrepancy) {
        discrepancies.add(discrepancy);
        if (discrepancy.getAmountDifference() != null) {
            totalDiscrepancyAmount = totalDiscrepancyAmount.add(discrepancy.getAmountDifference().abs());
        }
    }

    public void addError(String transactionId, String error) {
        errors.put(transactionId, error);
    }

    public void setCompleted() {
        this.endTime = LocalDateTime.now();
    }

    // Getter methods that match the service expectations
    public int getMatchedCount() {
        return matchedTransactions.size();
    }

    public int getPendingCount() {
        return pendingTransactions.size();
    }

    public int getDiscrepancyCount() {
        return discrepancies.size();
    }

    public BigDecimal getTotalDiscrepancyAmount() {
        return totalDiscrepancyAmount != null ? totalDiscrepancyAmount : BigDecimal.ZERO;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public Map<String, String> getErrors() {
        return new HashMap<>(errors);
    }

    // Summary methods
    public String getSummary() {
        return String.format(
            "Reconciliation %s completed - Matched: %d, Pending: %d, Discrepancies: %d, Errors: %d",
            reconciliationId, getMatchedCount(), getPendingCount(), getDiscrepancyCount(), errors.size()
        );
    }

    public boolean isSuccessful() {
        return !hasErrors();
    }

    public long getDurationMillis() {
        if (endTime != null) {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
        return java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
    }
}