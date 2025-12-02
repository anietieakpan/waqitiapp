package com.waqiti.layer2.model;

public enum OptimisticTransactionStatus {
    PENDING,
    SUBMITTED,
    CHALLENGE_PERIOD,
    FINALIZED,
    CHALLENGED,
    FAILED
}
