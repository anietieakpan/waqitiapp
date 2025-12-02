package com.waqiti.risk.domain;

public enum RiskFactor {
    UNUSUAL_BEHAVIOR(1.2),
    SESSION_ANOMALY(1.1),
    HOURLY_LIMIT_EXCEEDED(1.3),
    DAILY_LIMIT_EXCEEDED(1.4),
    HIGH_TRANSACTION_FREQUENCY(1.2),
    HIGH_AMOUNT_VELOCITY(1.3),
    HIGH_RISK_COUNTRY(1.5),
    IMPOSSIBLE_TRAVEL(2.0),
    VPN_DETECTED(1.3),
    PROXY_DETECTED(1.3),
    NEW_DEVICE(1.1),
    JAILBROKEN_DEVICE(1.4),
    SHARED_DEVICE(1.2),
    EMULATOR_DETECTED(2.0),
    FRAUD_PATTERN_MATCH(1.8),
    UNUSUAL_TIME_PATTERN(1.2),
    AMOUNT_ANOMALY(1.3),
    HIGH_RISK_MERCHANT(1.3),
    NEW_MERCHANT(1.1),
    MERCHANT_FRAUD_HISTORY(1.5);
    
    private final double multiplier;
    
    RiskFactor(double multiplier) {
        this.multiplier = multiplier;
    }
    
    public double getMultiplier() {
        return multiplier;
    }
    
    public static RiskFactor fromString(String value) {
        try {
            return RiskFactor.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return UNUSUAL_BEHAVIOR; // default
        }
    }
}
