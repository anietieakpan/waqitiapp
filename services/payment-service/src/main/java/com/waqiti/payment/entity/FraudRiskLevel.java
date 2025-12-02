package com.waqiti.payment.entity;

/**
 * Fraud risk level enumeration
 * Represents different levels of fraud risk for check deposits
 */
public enum FraudRiskLevel {
    LOW,         // Low fraud risk - minimal additional checks needed
    MEDIUM,      // Medium fraud risk - standard fraud checks
    HIGH,        // High fraud risk - enhanced verification required
    CRITICAL     // Critical fraud risk - immediate manual review required
}