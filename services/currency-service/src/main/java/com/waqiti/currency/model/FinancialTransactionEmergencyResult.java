package com.waqiti.currency.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Financial Transaction Emergency Result
 */
@Data
@Builder
public class FinancialTransactionEmergencyResult {
    private boolean financialImpactMitigated;
    private List<String> affectedConversions;
    private int mitigatedConversions;
    private List<String> mitigationMeasures;
    private List<String> restoredChannels;
    private Instant executedAt;
    private String correlationId;

    public boolean isFinancialImpactMitigated() {
        return financialImpactMitigated;
    }

    public List<String> getAffectedConversions() {
        return affectedConversions;
    }

    public List<String> getMitigationMeasures() {
        return mitigationMeasures;
    }

    public int getMitigatedConversions() {
        return mitigatedConversions;
    }
}
