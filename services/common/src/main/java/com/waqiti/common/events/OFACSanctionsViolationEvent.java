package com.waqiti.common.events;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OFAC Sanctions Violation Event
 * 
 * CRITICAL COMPLIANCE EVENT: Published when a sanctions violation is detected
 * 
 * This event triggers immediate actions:
 * - Account blocking and transaction freezing
 * - Executive notifications and alerts
 * - Regulatory reporting and SAR filing
 * - Law enforcement notification for severe cases
 * 
 * REGULATORY IMPACT: Failure to act on sanctions violations can result in:
 * - Fines up to $20M per violation
 * - Criminal prosecution
 * - Loss of banking license
 * - Personal liability for officers
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Data
@Builder
public class OFACSanctionsViolationEvent {
    
    // Event identification
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();
    
    @Builder.Default
    private String eventType = "OFAC_SANCTIONS_VIOLATION";
    
    @Builder.Default
    private LocalDateTime eventTimestamp = LocalDateTime.now();
    
    // Violation details
    private String violationId;
    private SanctionsViolationType violationType;
    private SanctionsViolationSeverity severity;
    private String violationSource; // USER_SCREENING, TRANSACTION_SCREENING, BATCH_SCREENING
    
    // Entity information
    private UUID userId;
    private UUID transactionId;
    private UUID merchantId;
    private String entityName;
    private String entityType; // INDIVIDUAL, ORGANIZATION, VESSEL, AIRCRAFT
    
    // Sanctions match details
    private String sanctionedEntityId;
    private String sanctionedEntityName;
    private String sanctionsList; // SDN, CONSOLIDATED, EU, UN, UK
    private Double matchScore; // 0.0 to 1.0
    private String matchType; // EXACT, FUZZY, PHONETIC, ALIAS
    private List<MatchCriteria> matchCriteria;
    
    // Transaction context (if applicable)
    private BigDecimal transactionAmount;
    private String transactionCurrency;
    private String senderCountry;
    private String recipientCountry;
    private String transactionType;
    private String transactionDescription;
    
    // Geographic information
    private String sanctionedCountry;
    private String sanctionedRegion;
    private List<String> sanctionedJurisdictions;
    
    // Risk assessment
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL, MAXIMUM
    private Map<String, String> riskFactors;
    private String complianceOfficerAssigned;
    
    // Regulatory requirements
    private boolean requiresImmediateBlocking;
    private boolean requiresSARFiling;
    private boolean requiresLawEnforcementNotification;
    private boolean requiresRegulatoryReporting;
    private LocalDateTime reportingDeadline;
    
    // Additional context
    private String detectionMethod;
    private String detectionSource;
    private Map<String, Object> additionalData;
    private String originalRequestId;
    private String correlationId;
    
    // Action tracking
    private List<String> immediateActionsRequired;
    private String escalationLevel; // STANDARD, HIGH, CRITICAL, EMERGENCY
    private boolean requiresExecutiveNotification;
    
    /**
     * Type of sanctions violation
     */
    public enum SanctionsViolationType {
        INDIVIDUAL_MATCH,          // Individual on sanctions list
        ORGANIZATION_MATCH,        // Organization on sanctions list  
        VESSEL_MATCH,             // Sanctioned vessel
        AIRCRAFT_MATCH,           // Sanctioned aircraft
        COUNTRY_SANCTIONS,        // Transaction to/from sanctioned country
        SECTORAL_SANCTIONS,       // Sectoral sanctions violation
        SECONDARY_SANCTIONS,      // Secondary sanctions risk
        OWNERSHIP_CONTROL,        // 50%+ ownership/control by sanctioned party
        BLOCKED_PERSON,          // Specially Designated National (SDN)
        FROZEN_ASSETS,           // Assets subject to freezing
        SANCTIONS_EVASION,       // Attempted sanctions evasion
        FRONT_COMPANY,           // Front company for sanctioned entity
        SHELL_COMPANY            // Shell company linked to sanctions
    }
    
    /**
     * Severity of sanctions violation
     */
    public enum SanctionsViolationSeverity {
        LOW,              // Low confidence match, requires review
        MEDIUM,           // Medium confidence, enhanced due diligence required
        HIGH,             // High confidence match, immediate blocking required
        CRITICAL,         // Critical violation, immediate action and reporting
        MAXIMUM          // Maximum severity, law enforcement notification required
    }
    
    /**
     * Match criteria details
     */
    @Data
    @Builder
    public static class MatchCriteria {
        private String criteriaType; // NAME, DOB, ADDRESS, ID_NUMBER, PASSPORT
        private String inputValue;
        private String matchedValue;
        private Double similarityScore;
        private String matchAlgorithm; // EXACT, LEVENSHTEIN, SOUNDEX, METAPHONE
    }
    
    /**
     * Check if this violation requires immediate action
     */
    public boolean requiresImmediateAction() {
        return severity == SanctionsViolationSeverity.HIGH ||
               severity == SanctionsViolationSeverity.CRITICAL ||
               severity == SanctionsViolationSeverity.MAXIMUM ||
               requiresImmediateBlocking;
    }
    
    /**
     * Check if this violation requires executive notification
     */
    public boolean requiresExecutiveAlert() {
        return severity == SanctionsViolationSeverity.CRITICAL ||
               severity == SanctionsViolationSeverity.MAXIMUM ||
               requiresExecutiveNotification ||
               (transactionAmount != null && transactionAmount.compareTo(new BigDecimal("100000")) > 0);
    }
    
    /**
     * Check if this violation requires law enforcement notification
     */
    public boolean requiresLawEnforcementAlert() {
        return severity == SanctionsViolationSeverity.MAXIMUM ||
               requiresLawEnforcementNotification ||
               violationType == SanctionsViolationType.SANCTIONS_EVASION;
    }
    
    /**
     * Get priority level for processing
     */
    public String getPriorityLevel() {
        switch (severity) {
            case MAXIMUM:
                return "EMERGENCY";
            case CRITICAL:
                return "CRITICAL";
            case HIGH:
                return "HIGH";
            case MEDIUM:
                return "MEDIUM";
            default:
                return "STANDARD";
        }
    }
}