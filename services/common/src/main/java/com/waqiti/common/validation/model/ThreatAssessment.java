package com.waqiti.common.validation.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;
import lombok.ToString;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Enterprise-grade Threat Assessment model for comprehensive threat intelligence analysis.
 * This class represents a complete threat assessment for an entity (IP, domain, email, user, etc.)
 * including threat scoring, categorization, indicators of compromise (IOCs), and recommended actions.
 * 
 * Designed for production use in financial services with full compliance, audit trail,
 * and integration with threat intelligence feeds and security orchestration platforms.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@With
@ToString(exclude = {"sensitiveData", "rawIntelligence"})
@EqualsAndHashCode(of = {"assessmentId", "entityId", "entityType"})
@JsonIgnoreProperties(ignoreUnknown = true)
public class ThreatAssessment implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Unique identifier for this threat assessment
     */
    @NotNull
    @Builder.Default
    private String assessmentId = UUID.randomUUID().toString();
    
    /**
     * The entity being assessed (IP address, domain, email, user ID, etc.)
     */
    @NotBlank(message = "Entity ID is required")
    @Size(max = 500, message = "Entity ID cannot exceed 500 characters")
    private String entityId;
    
    /**
     * Type of entity being assessed
     */
    @NotNull(message = "Entity type is required")
    private EntityType entityType;
    
    /**
     * Overall threat level
     */
    @NotNull
    @Builder.Default
    private ThreatLevel threatLevel = ThreatLevel.UNKNOWN;
    
    /**
     * Numerical threat score (0.0 to 100.0)
     */
    @NotNull
    @DecimalMin(value = "0.0", message = "Threat score must be at least 0.0")
    @DecimalMax(value = "100.0", message = "Threat score must be at most 100.0")
    @Builder.Default
    private Double threatScore = 0.0;
    
    /**
     * Confidence level in the assessment (0.0 to 1.0)
     */
    @NotNull
    @DecimalMin(value = "0.0", message = "Confidence must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Confidence must be at most 1.0")
    @Builder.Default
    private Double confidence = 0.0;
    
    /**
     * Risk rating for business impact
     */
    @NotNull
    @Builder.Default
    private RiskRating riskRating = RiskRating.LOW;
    
    /**
     * Threat categories detected
     */
    @Builder.Default
    private Set<ThreatCategory> threatCategories = new HashSet<>();
    
    /**
     * Indicators of Compromise (IOCs)
     */
    @Builder.Default
    private List<IndicatorOfCompromise> indicators = new ArrayList<>();
    
    /**
     * Threat intelligence sources used
     */
    @Builder.Default
    private Set<ThreatIntelligenceSource> sources = new HashSet<>();
    
    /**
     * Recommended actions
     */
    @Builder.Default
    private List<RecommendedAction> recommendedActions = new ArrayList<>();
    
    /**
     * Detailed threat analysis
     */
    @Size(max = 5000, message = "Analysis cannot exceed 5000 characters")
    private String detailedAnalysis;
    
    /**
     * Executive summary
     */
    @Size(max = 1000, message = "Summary cannot exceed 1000 characters")
    private String executiveSummary;
    
    /**
     * Geographic location associated with threat
     */
    private GeographicContext geographicContext;
    
    /**
     * Historical threat data
     */
    @Builder.Default
    private HistoricalContext historicalContext = new HistoricalContext();
    
    /**
     * Attack patterns detected
     */
    @Builder.Default
    private Set<AttackPattern> attackPatterns = new HashSet<>();
    
    /**
     * MITRE ATT&CK tactics and techniques
     */
    @Builder.Default
    private Set<String> mitreTactics = new HashSet<>();
    
    /**
     * Timestamp when assessment was created
     */
    @NotNull
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Builder.Default
    private LocalDateTime assessmentTime = LocalDateTime.now();
    
    /**
     * Time when threat was first detected
     */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime firstSeenTime;
    
    /**
     * Time when threat was last seen
     */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime lastSeenTime;
    
    /**
     * Assessment expiry time
     */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime expiryTime;
    
    /**
     * Processing time in milliseconds
     */
    private Long processingTimeMs;
    
    /**
     * Whether this is an active threat
     */
    @Builder.Default
    private Boolean isActive = false;
    
    /**
     * Whether immediate action is required
     */
    @Builder.Default
    private Boolean requiresImmediateAction = false;
    
    /**
     * Whether this has been verified by human analyst
     */
    @Builder.Default
    private Boolean humanVerified = false;
    
    /**
     * Analyst notes
     */
    @Size(max = 2000)
    private String analystNotes;
    
    /**
     * Related threat assessments
     */
    @Builder.Default
    private List<String> relatedAssessmentIds = new ArrayList<>();
    
    /**
     * Custom metadata
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    
    /**
     * Sensitive data (excluded from toString)
     */
    private Map<String, Object> sensitiveData;
    
    /**
     * Raw intelligence data (excluded from toString)
     */
    private String rawIntelligence;
    
    /**
     * Compliance tags
     */
    @Builder.Default
    private Set<String> complianceTags = new HashSet<>();
    
    /**
     * Entity type enumeration
     */
    public enum EntityType {
        IP_ADDRESS("IP Address"),
        DOMAIN("Domain Name"),
        EMAIL("Email Address"),
        USER_ID("User Identifier"),
        PHONE_NUMBER("Phone Number"),
        URL("URL"),
        FILE_HASH("File Hash"),
        BITCOIN_ADDRESS("Bitcoin Address"),
        TRANSACTION_ID("Transaction ID"),
        DEVICE_ID("Device ID"),
        ACCOUNT_ID("Account ID");
        
        private final String description;
        
        EntityType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Threat level enumeration
     */
    public enum ThreatLevel {
        CRITICAL("Critical", 5, 80.0),
        HIGH("High", 4, 60.0),
        MEDIUM("Medium", 3, 40.0),
        LOW("Low", 2, 20.0),
        MINIMAL("Minimal", 1, 10.0),
        NONE("None", 0, 0.0),
        UNKNOWN("Unknown", -1, null);
        
        private final String label;
        private final int severity;
        private final Double thresholdScore;
        
        ThreatLevel(String label, int severity, Double thresholdScore) {
            this.label = label;
            this.severity = severity;
            this.thresholdScore = thresholdScore;
        }
        
        public String getLabel() { return label; }
        public int getSeverity() { return severity; }
        public Double getThresholdScore() { return thresholdScore; }
        
        public static ThreatLevel fromScore(double score) {
            if (score >= 80.0) return CRITICAL;
            if (score >= 60.0) return HIGH;
            if (score >= 40.0) return MEDIUM;
            if (score >= 20.0) return LOW;
            if (score >= 10.0) return MINIMAL;
            if (score >= 0.0) return NONE;
            return UNKNOWN;
        }
    }
    
    /**
     * Risk rating enumeration
     */
    public enum RiskRating {
        CRITICAL("Critical Risk"),
        HIGH("High Risk"),
        MEDIUM("Medium Risk"),
        LOW("Low Risk"),
        ACCEPTABLE("Acceptable Risk");
        
        private final String description;
        
        RiskRating(String description) {
            this.description = description;
        }
        
        public String getDescription() { return description; }
    }
    
    /**
     * Threat category enumeration
     */
    public enum ThreatCategory {
        MALWARE("Malware"),
        PHISHING("Phishing"),
        FRAUD("Fraud"),
        BOTNET("Botnet"),
        SPAM("Spam"),
        DOS_ATTACK("DoS Attack"),
        BRUTE_FORCE("Brute Force"),
        EXPLOITATION("Exploitation"),
        DATA_BREACH("Data Breach"),
        ACCOUNT_TAKEOVER("Account Takeover"),
        MONEY_LAUNDERING("Money Laundering"),
        TERRORIST_FINANCING("Terrorist Financing"),
        SANCTIONS_VIOLATION("Sanctions Violation"),
        PII_EXPOSURE("PII Exposure"),
        UNAUTHORIZED_ACCESS("Unauthorized Access"),
        INSIDER_THREAT("Insider Threat"),
        SUPPLY_CHAIN_ATTACK("Supply Chain Attack"),
        ZERO_DAY("Zero Day"),
        ADVANCED_PERSISTENT_THREAT("APT"),
        CRYPTOJACKING("Cryptojacking");
        
        private final String description;
        
        ThreatCategory(String description) {
            this.description = description;
        }
        
        public String getDescription() { return description; }
    }
    
    /**
     * Attack pattern enumeration
     */
    public enum AttackPattern {
        CREDENTIAL_STUFFING("Credential Stuffing"),
        PASSWORD_SPRAY("Password Spray"),
        SQL_INJECTION("SQL Injection"),
        CROSS_SITE_SCRIPTING("Cross-Site Scripting"),
        COMMAND_INJECTION("Command Injection"),
        PATH_TRAVERSAL("Path Traversal"),
        LDAP_INJECTION("LDAP Injection"),
        XML_INJECTION("XML Injection"),
        SSRF("Server-Side Request Forgery"),
        RACE_CONDITION("Race Condition"),
        SESSION_HIJACKING("Session Hijacking"),
        CLICKJACKING("Clickjacking"),
        DNS_SPOOFING("DNS Spoofing"),
        MAN_IN_THE_MIDDLE("Man-in-the-Middle"),
        REPLAY_ATTACK("Replay Attack"),
        TIMING_ATTACK("Timing Attack");
        
        private final String description;
        
        AttackPattern(String description) {
            this.description = description;
        }
        
        public String getDescription() { return description; }
    }
    
    /**
     * Indicator of Compromise
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndicatorOfCompromise {
        @NotBlank
        private String type;
        @NotBlank
        private String value;
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private Double confidence;
        private String source;
        private LocalDateTime detectedAt;
        private Map<String, Object> attributes;
    }
    
    /**
     * Threat Intelligence Source
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThreatIntelligenceSource {
        @NotBlank
        private String name;
        private String type;
        private Double reliability;
        private LocalDateTime lastUpdated;
        private String feedUrl;
        private Map<String, Object> metadata;
    }
    
    /**
     * Recommended Action
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendedAction {
        @NotBlank
        private String action;
        @NotNull
        private Priority priority;
        private String description;
        private String category;
        private Double expectedEffectiveness;
        private String implementationGuide;
        
        public enum Priority {
            IMMEDIATE, HIGH, MEDIUM, LOW
        }
    }
    
    /**
     * Geographic Context
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeographicContext {
        private String country;
        private String countryCode;
        private String city;
        private String region;
        private Double latitude;
        private Double longitude;
        private String timezone;
        private Boolean isHighRiskCountry;
        private Boolean isSanctionedCountry;
    }
    
    /**
     * Historical Context
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoricalContext {
        @Min(0)
        @Builder.Default
        private Integer previousIncidents = 0;
        private LocalDateTime firstIncidentDate;
        private LocalDateTime lastIncidentDate;
        @DecimalMin("0.0")
        @DecimalMax("100.0")
        @Builder.Default
        private Double averageThreatScore = 0.0;
        @Builder.Default
        private List<String> previousAssessmentIds = new ArrayList<>();
        @Builder.Default
        private Map<String, Integer> incidentsByCategory = new HashMap<>();
        private String trend;  // INCREASING, DECREASING, STABLE
    }
    
    /**
     * Helper methods
     */
    
    public void addThreatCategory(ThreatCategory category) {
        if (threatCategories == null) {
            threatCategories = new HashSet<>();
        }
        threatCategories.add(category);
    }
    
    public void addIndicator(IndicatorOfCompromise ioc) {
        if (indicators == null) {
            indicators = new ArrayList<>();
        }
        indicators.add(ioc);
    }
    
    public void addRecommendedAction(RecommendedAction action) {
        if (recommendedActions == null) {
            recommendedActions = new ArrayList<>();
        }
        recommendedActions.add(action);
    }
    
    public void addMetadata(String key, Object value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(key, value);
    }
    
    public boolean isHighRisk() {
        return threatLevel == ThreatLevel.HIGH || threatLevel == ThreatLevel.CRITICAL;
    }
    
    public boolean requiresManualReview() {
        return confidence < 0.7 && threatScore > 50.0;
    }
    
    public String generateAlertMessage() {
        return String.format("[%s] Threat Assessment for %s (%s): Score=%.1f, Level=%s, Categories=%s",
                requiresImmediateAction ? "URGENT" : "ALERT",
                entityId, entityType, threatScore, threatLevel, threatCategories);
    }
}