package com.waqiti.accounting.exception;

import java.util.UUID;

/**
 * Exception thrown when attempting to post to a closed financial period
 */
public class FinancialPeriodClosedException extends AccountingException {

    private final UUID periodId;
    private final String periodName;

    public FinancialPeriodClosedException(UUID periodId, String periodName) {
        super("PERIOD_CLOSED",
            String.format("Financial period %s is closed and cannot accept new entries", periodName),
            periodId, periodName);
        this.periodId = periodId;
        this.periodName = periodName;
    }

    public UUID getPeriodId() {
        return periodId;
    }

    public String getPeriodName() {
        return periodName;
    }
}
