package com.waqiti.layer2.model;

/**
 * Status of Layer 2 transactions
 */
public enum Layer2Status {
    PENDING("Transaction submitted, awaiting processing"),
    PROVEN("ZK proof generated and verified"),
    INSTANT("State channel update, instant finality"),
    FINALIZED("Transaction finalized on L1"),
    CHALLENGED("Transaction challenged during fraud proof window"),
    FAILED("Transaction failed");

    private final String description;

    Layer2Status(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
