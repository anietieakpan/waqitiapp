package com.waqiti.audit.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Industrial-grade audit severity classification supporting regulatory compliance
 * and risk-based categorization for financial services.
 * 
 * Provides comprehensive severity levels with:
 * - Risk-based categorization aligned with regulatory frameworks
 * - Alert threshold configuration for real-time monitoring
 * - Compliance mapping for SOX, GDPR, PCI DSS, and other standards
 * - Integration with incident response and escalation procedures
 * 
 * This enum is designed to support high-volume audit environments processing
 * 1M+ events per hour with efficient categorization and alerting.
 */
@Getter
@RequiredArgsConstructor
public enum AuditSeverity {

    /**
     * LOW severity - Routine operational events with minimal risk impact.
     * Examples: Successful logins, data reads, routine transactions
     * 
     * Characteristics:
     * - Risk Score: 0-25
     * - Alert Threshold: No immediate alerting
     * - Retention: Standard retention policy
     * - Escalation: No escalation required
     */
    LOW(
        "LOW", 
        "Routine operational events", 
        0, 
        25, 
        false, 
        false, 
        "STANDARD",
        "INFORMATIONAL",
        Map.of(
            "alert_required", false,
            "escalation_level", 0,
            "sla_response_hours", 72,
            "monitoring_dashboard", "operational"
        )
    ),

    /**
     * MEDIUM severity - Events requiring attention but not immediate action.
     * Examples: Failed transactions, validation errors, permission denials
     * 
     * Characteristics:
     * - Risk Score: 26-50
     * - Alert Threshold: Batch alerting (hourly/daily)
     * - Retention: Standard retention policy
     * - Escalation: Level 1 support notification
     */
    MEDIUM(
        "MEDIUM", 
        "Events requiring monitoring attention", 
        26, 
        50, 
        true, 
        false, 
        "STANDARD",
        "WARNING",
        Map.of(
            "alert_required", true,
            "escalation_level", 1,
            "sla_response_hours", 24,
            "monitoring_dashboard", "security"
        )
    ),

    /**
     * HIGH severity - Events requiring immediate investigation and response.
     * Examples: Security violations, fraud indicators, system errors
     * 
     * Characteristics:
     * - Risk Score: 51-75
     * - Alert Threshold: Real-time alerting (within 5 minutes)
     * - Retention: Extended retention policy
     * - Escalation: Level 2 support and security team notification
     */
    HIGH(
        "HIGH", 
        "Events requiring immediate investigation", 
        51, 
        75, 
        true, 
        true, 
        "EXTENDED",
        "URGENT",
        Map.of(
            "alert_required", true,
            "escalation_level", 2,
            "sla_response_hours", 4,
            "monitoring_dashboard", "security",
            "incident_management", true
        )
    ),

    /**
     * CRITICAL severity - Events requiring immediate action and incident response.
     * Examples: Security breaches, data integrity violations, regulatory violations
     * 
     * Characteristics:
     * - Risk Score: 76-100
     * - Alert Threshold: Immediate alerting (within 1 minute)
     * - Retention: Maximum retention policy with legal hold
     * - Escalation: Executive level and regulatory notification
     */
    CRITICAL(
        "CRITICAL", 
        "Events requiring immediate action and incident response", 
        76, 
        100, 
        true, 
        true, 
        "MAXIMUM",
        "EMERGENCY",
        Map.of(
            "alert_required", true,
            "escalation_level", 3,
            "sla_response_minutes", 15,
            "monitoring_dashboard", "executive",
            "incident_management", true,
            "regulatory_notification", true,
            "legal_hold", true
        )
    ),

    /**
     * REGULATORY severity - Events with specific regulatory compliance implications.
     * Examples: SOX violations, GDPR breaches, PCI DSS compliance events
     * 
     * Characteristics:
     * - Risk Score: Variable based on violation type
     * - Alert Threshold: Immediate alerting with regulatory workflow
     * - Retention: Regulatory mandated retention periods
     * - Escalation: Compliance officer and regulatory bodies
     */
    REGULATORY(
        "REGULATORY", 
        "Events with regulatory compliance implications", 
        50, 
        100, 
        true, 
        true, 
        "REGULATORY",
        "COMPLIANCE",
        Map.of(
            "alert_required", true,
            "escalation_level", 4,
            "sla_response_minutes", 30,
            "monitoring_dashboard", "compliance",
            "incident_management", true,
            "regulatory_notification", true,
            "legal_hold", true,
            "audit_trail_required", true
        )
    ),

    /**
     * FRAUD severity - Events indicating potential fraudulent activity.
     * Examples: Fraud detection alerts, suspicious transaction patterns
     * 
     * Characteristics:
     * - Risk Score: 60-95
     * - Alert Threshold: Immediate alerting with fraud investigation
     * - Retention: Extended retention with investigation data
     * - Escalation: Fraud investigation team and law enforcement
     */
    FRAUD(
        "FRAUD", 
        "Events indicating potential fraudulent activity", 
        60, 
        95, 
        true, 
        true, 
        "FRAUD_INVESTIGATION",
        "FRAUD_ALERT",
        Map.of(
            "alert_required", true,
            "escalation_level", 3,
            "sla_response_minutes", 10,
            "monitoring_dashboard", "fraud",
            "incident_management", true,
            "fraud_investigation", true,
            "law_enforcement_notification", false,
            "account_protection", true
        )
    );

    private final String code;
    private final String description;
    private final int minRiskScore;
    private final int maxRiskScore;
    private final boolean alertRequired;
    private final boolean realTimeAlert;
    private final String retentionPolicy;
    private final String alertLevel;
    private final Map<String, Object> properties;

    // Static lookup map for efficient deserialization
    private static final Map<String, AuditSeverity> BY_CODE = Arrays.stream(values())
        .collect(Collectors.toMap(AuditSeverity::getCode, Function.identity()));

    /**
     * JSON serialization - returns the severity code
     */
    @JsonValue
    public String getCode() {
        return code;
    }

    /**
     * JSON deserialization - accepts severity code or name
     */
    @JsonCreator
    public static AuditSeverity fromCode(String code) {
        if (code == null) {
            return LOW; // Default to LOW severity
        }
        
        // Try exact match first
        AuditSeverity severity = BY_CODE.get(code.toUpperCase());
        if (severity != null) {
            return severity;
        }
        
        // Try enum name match
        try {
            return valueOf(code.toUpperCase());
        } catch (IllegalArgumentException e) {
            return LOW; // Default to LOW for unknown values
        }
    }

    /**
     * Determine severity based on risk score
     */
    public static AuditSeverity fromRiskScore(int riskScore) {
        for (AuditSeverity severity : values()) {
            if (riskScore >= severity.minRiskScore && riskScore <= severity.maxRiskScore) {
                return severity;
            }
        }
        return LOW; // Default fallback
    }

    /**
     * Get escalation level (0 = no escalation, 4 = maximum escalation)
     */
    public int getEscalationLevel() {
        return (Integer) properties.getOrDefault("escalation_level", 0);
    }

    /**
     * Get SLA response time in minutes
     */
    public int getSlaResponseMinutes() {
        // Convert hours to minutes if specified in hours
        if (properties.containsKey("sla_response_hours")) {
            return (Integer) properties.get("sla_response_hours") * 60;
        }
        return (Integer) properties.getOrDefault("sla_response_minutes", 4320); // 3 days default
    }

    /**
     * Check if incident management is required
     */
    public boolean requiresIncidentManagement() {
        return (Boolean) properties.getOrDefault("incident_management", false);
    }

    /**
     * Check if regulatory notification is required
     */
    public boolean requiresRegulatoryNotification() {
        return (Boolean) properties.getOrDefault("regulatory_notification", false);
    }

    /**
     * Check if legal hold is required
     */
    public boolean requiresLegalHold() {
        return (Boolean) properties.getOrDefault("legal_hold", false);
    }

    /**
     * Check if fraud investigation is required
     */
    public boolean requiresFraudInvestigation() {
        return (Boolean) properties.getOrDefault("fraud_investigation", false);
    }

    /**
     * Get monitoring dashboard assignment
     */
    public String getMonitoringDashboard() {
        return (String) properties.getOrDefault("monitoring_dashboard", "operational");
    }

    /**
     * Check if account protection measures should be triggered
     */
    public boolean requiresAccountProtection() {
        return (Boolean) properties.getOrDefault("account_protection", false);
    }

    /**
     * Check if audit trail documentation is required
     */
    public boolean requiresAuditTrail() {
        return (Boolean) properties.getOrDefault("audit_trail_required", false);
    }

    /**
     * Get compliance framework mappings
     */
    public ComplianceMapping getComplianceMapping() {
        return new ComplianceMapping(this);
    }

    /**
     * Check if severity level meets or exceeds threshold
     */
    public boolean meetsThreshold(AuditSeverity threshold) {
        return this.getMinRiskScore() >= threshold.getMinRiskScore();
    }

    /**
     * Get the next higher severity level
     */
    public AuditSeverity escalate() {
        switch (this) {
            case LOW -> {
                return MEDIUM;
            }
            case MEDIUM -> {
                return HIGH;
            }
            case HIGH -> {
                return CRITICAL;
            }
            case CRITICAL -> {
                return REGULATORY;
            }
            case REGULATORY, FRAUD -> {
                return this; // Already at maximum
            }
            default -> {
                return MEDIUM;
            }
        }
    }

    /**
     * Get the next lower severity level
     */
    public AuditSeverity deEscalate() {
        switch (this) {
            case REGULATORY, FRAUD -> {
                return CRITICAL;
            }
            case CRITICAL -> {
                return HIGH;
            }
            case HIGH -> {
                return MEDIUM;
            }
            case MEDIUM -> {
                return LOW;
            }
            case LOW -> {
                return this; // Already at minimum
            }
            default -> {
                return MEDIUM;
            }
        }
    }

    /**
     * Inner class for compliance framework mapping
     */
    @Getter
    @RequiredArgsConstructor
    public static class ComplianceMapping {
        private final AuditSeverity severity;

        /**
         * Get SOX (Sarbanes-Oxley) compliance level
         */
        public String getSoxLevel() {
            return switch (severity) {
                case LOW -> "ROUTINE";
                case MEDIUM -> "SIGNIFICANT";
                case HIGH -> "MATERIAL";
                case CRITICAL, REGULATORY -> "CRITICAL_DEFICIENCY";
                case FRAUD -> "MATERIAL_WEAKNESS";
            };
        }

        /**
         * Get GDPR compliance impact level
         */
        public String getGdprImpact() {
            return switch (severity) {
                case LOW -> "LOW_RISK";
                case MEDIUM -> "MODERATE_RISK";
                case HIGH -> "HIGH_RISK";
                case CRITICAL, REGULATORY -> "BREACH_NOTIFICATION";
                case FRAUD -> "DATA_PROTECTION_VIOLATION";
            };
        }

        /**
         * Get PCI DSS compliance level
         */
        public String getPciDssLevel() {
            return switch (severity) {
                case LOW -> "INFORMATIONAL";
                case MEDIUM -> "LOW_RISK";
                case HIGH -> "MEDIUM_RISK";
                case CRITICAL, REGULATORY -> "HIGH_RISK";
                case FRAUD -> "SECURITY_INCIDENT";
            };
        }

        /**
         * Get FFIEC (Federal Financial Institutions Examination Council) rating
         */
        public String getFfiecRating() {
            return switch (severity) {
                case LOW -> "SATISFACTORY";
                case MEDIUM -> "NEEDS_IMPROVEMENT";
                case HIGH -> "UNSATISFACTORY";
                case CRITICAL, REGULATORY -> "CRITICAL";
                case FRAUD -> "ENFORCEMENT_ACTION";
            };
        }

        /**
         * Get Basel III operational risk classification
         */
        public String getBaselRiskClass() {
            return switch (severity) {
                case LOW -> "BASIC_INDICATOR";
                case MEDIUM -> "STANDARDIZED";
                case HIGH -> "ADVANCED";
                case CRITICAL, REGULATORY -> "OPERATIONAL_LOSS";
                case FRAUD -> "CONDUCT_RISK";
            };
        }
    }

    @Override
    public String toString() {
        return String.format("%s (%s) - Risk: %d-%d", code, description, minRiskScore, maxRiskScore);
    }
}