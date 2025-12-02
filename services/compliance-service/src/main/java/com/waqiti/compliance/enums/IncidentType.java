package com.waqiti.compliance.enums;

/**
 * Compliance Incident Type Enumeration
 *
 * Defines all types of compliance incidents tracked by the platform
 * for SLA management, regulatory reporting, and audit compliance.
 *
 * Compliance: SOX 404, PCI DSS 10.x, GDPR Article 33, Reg E, GLBA
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
public enum IncidentType {
    /**
     * Security incident - unauthorized access, breach attempt
     * Compliance: PCI DSS 10.x, GDPR Article 33
     * SLA: 24 hours for critical, 72 hours for high
     */
    SECURITY_INCIDENT,

    /**
     * Data breach - confirmed unauthorized data access
     * Compliance: GDPR Article 33 (72-hour reporting requirement)
     * SLA: 1 hour for critical
     */
    DATA_BREACH,

    /**
     * Privacy violation - PII/GDPR violation
     * Compliance: GDPR, GLBA
     * SLA: 24 hours
     */
    PRIVACY_VIOLATION,

    /**
     * Fraud complaint - customer-reported fraud
     * Compliance: Reg E, EFTA
     * SLA: 10 business days (Reg E requirement)
     */
    FRAUD_COMPLAINT,

    /**
     * Unauthorized transaction - disputed transaction
     * Compliance: Reg E
     * SLA: 10 business days
     */
    UNAUTHORIZED_TRANSACTION,

    /**
     * Payment dispute - chargeback, payment error
     * Compliance: PCI DSS, Reg E
     * SLA: 45 days (chargeback window)
     */
    PAYMENT_DISPUTE,

    /**
     * Regulatory inquiry - regulator request for information
     * Compliance: SOX, BSA/AML, SEC
     * SLA: Per regulator deadline (typically 5-10 business days)
     */
    REGULATORY_INQUIRY,

    /**
     * Audit finding - internal or external audit issue
     * Compliance: SOX 404
     * SLA: 30 days for remediation plan
     */
    AUDIT_FINDING,

    /**
     * Policy violation - internal policy breach
     * Compliance: SOX 404, internal controls
     * SLA: 7 business days
     */
    POLICY_VIOLATION,

    /**
     * System outage - service disruption affecting compliance
     * Compliance: Business continuity requirements
     * SLA: 4 hours for critical systems
     */
    SYSTEM_OUTAGE,

    /**
     * Control failure - internal control breakdown
     * Compliance: SOX 404
     * SLA: 24 hours
     */
    CONTROL_FAILURE,

    /**
     * Suspicious activity - AML/BSA trigger
     * Compliance: BSA/AML, FinCEN
     * SLA: 30 days for SAR filing
     */
    SUSPICIOUS_ACTIVITY,

    /**
     * Customer complaint - general customer issue with compliance implications
     * Compliance: Consumer protection regulations
     * SLA: 30 days
     */
    CUSTOMER_COMPLAINT,

    /**
     * Third-party incident - vendor/partner compliance issue
     * Compliance: Third-party risk management
     * SLA: 7 business days
     */
    THIRD_PARTY_INCIDENT,

    /**
     * Regulatory change - new regulation requiring action
     * Compliance: Proactive compliance management
     * SLA: Per regulation effective date
     */
    REGULATORY_CHANGE;

    /**
     * Check if incident type requires regulatory reporting
     *
     * @return true if regulatory reporting required
     */
    public boolean requiresRegulatoryReporting() {
        return this == DATA_BREACH ||
               this == REGULATORY_INQUIRY ||
               this == SUSPICIOUS_ACTIVITY ||
               this == PRIVACY_VIOLATION;
    }

    /**
     * Check if incident type is customer-facing
     *
     * @return true if customer-facing
     */
    public boolean isCustomerFacing() {
        return this == FRAUD_COMPLAINT ||
               this == UNAUTHORIZED_TRANSACTION ||
               this == PAYMENT_DISPUTE ||
               this == CUSTOMER_COMPLAINT;
    }

    /**
     * Check if incident type is security-related
     *
     * @return true if security-related
     */
    public boolean isSecurityRelated() {
        return this == SECURITY_INCIDENT ||
               this == DATA_BREACH ||
               this == PRIVACY_VIOLATION;
    }

    /**
     * Check if incident type requires immediate escalation
     *
     * @return true if immediate escalation required
     */
    public boolean requiresImmediateEscalation() {
        return this == DATA_BREACH ||
               this == REGULATORY_INQUIRY ||
               this == CONTROL_FAILURE;
    }

    /**
     * Get default SLA in hours
     *
     * @return SLA in hours
     */
    public int getDefaultSLAHours() {
        switch (this) {
            case DATA_BREACH:
                return 1; // Critical - 1 hour
            case SECURITY_INCIDENT:
            case CONTROL_FAILURE:
            case PRIVACY_VIOLATION:
                return 24; // 1 day
            case SYSTEM_OUTAGE:
                return 4; // 4 hours
            case POLICY_VIOLATION:
            case THIRD_PARTY_INCIDENT:
                return 168; // 7 days
            case FRAUD_COMPLAINT:
            case UNAUTHORIZED_TRANSACTION:
                return 240; // 10 business days
            case AUDIT_FINDING:
            case SUSPICIOUS_ACTIVITY:
            case CUSTOMER_COMPLAINT:
                return 720; // 30 days
            case PAYMENT_DISPUTE:
                return 1080; // 45 days
            case REGULATORY_INQUIRY:
            case REGULATORY_CHANGE:
            default:
                return 120; // 5 business days (conservative default)
        }
    }

    /**
     * Check if incident type is SOX-relevant
     *
     * @return true if SOX-relevant
     */
    public boolean isSOXRelevant() {
        return this == AUDIT_FINDING ||
               this == CONTROL_FAILURE ||
               this == POLICY_VIOLATION;
    }
}
