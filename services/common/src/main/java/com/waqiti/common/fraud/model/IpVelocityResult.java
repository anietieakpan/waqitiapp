package com.waqiti.common.fraud.model;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;

/**
 * IP velocity result with comprehensive velocity analysis
 */
@Data
@Builder
@Jacksonized
public class IpVelocityResult {
    private String ipAddress;
    private int transactionCount1h;
    private int transactionCount24h;
    private int transactionsLast1h;
    private int transactionsLastHour; // Alias for transactionsLast1h
    private int transactionsLast24h;
    private int uniqueUsersLast1h;
    private int uniqueUsersLastHour; // Alias for uniqueUsersLast1h
    private int uniqueUsersLast24h;
    private int uniqueAccountsLastHour; // PRODUCTION FIX: For FraudServiceHelper
    private BigDecimal transactionVolume24h;
    private boolean velocityExceeded;
    private double velocityScore;
    private double velocityRisk;
    private double riskScore; // PRODUCTION FIX: Alias for velocityRisk
    private int uniqueAccounts;
    private String riskLevel;

    /**
     * Get velocity risk score
     */
    public double getVelocityRisk() {
        if (velocityRisk > 0) {
            return velocityRisk;
        }
        return velocityScore;
    }
}