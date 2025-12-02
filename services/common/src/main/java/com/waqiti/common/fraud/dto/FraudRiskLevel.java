package com.waqiti.common.fraud.dto;

public enum FraudRiskLevel {
    MINIMAL, LOW, MEDIUM, HIGH, CRITICAL, UNKNOWN;

    /**
     * PRODUCTION FIX: Convert from model package FraudRiskLevel
     */
    public static FraudRiskLevel fromModel(com.waqiti.common.fraud.model.FraudRiskLevel modelLevel) {
        if (modelLevel == null) return UNKNOWN;
        return switch (modelLevel) {
            case MINIMAL -> MINIMAL;
            case LOW -> LOW;
            case MEDIUM -> MEDIUM;
            case HIGH -> HIGH;
            case CRITICAL -> CRITICAL;
            case UNKNOWN -> UNKNOWN;
        };
    }

    /**
     * PRODUCTION FIX: Convert to model package FraudRiskLevel
     */
    public com.waqiti.common.fraud.model.FraudRiskLevel toModel() {
        return switch (this) {
            case MINIMAL -> com.waqiti.common.fraud.model.FraudRiskLevel.MINIMAL;
            case LOW -> com.waqiti.common.fraud.model.FraudRiskLevel.LOW;
            case MEDIUM -> com.waqiti.common.fraud.model.FraudRiskLevel.MEDIUM;
            case HIGH -> com.waqiti.common.fraud.model.FraudRiskLevel.HIGH;
            case CRITICAL -> com.waqiti.common.fraud.model.FraudRiskLevel.CRITICAL;
            case UNKNOWN -> com.waqiti.common.fraud.model.FraudRiskLevel.UNKNOWN;
        };
    }
}
