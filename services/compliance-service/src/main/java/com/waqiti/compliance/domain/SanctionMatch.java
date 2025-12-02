package com.waqiti.compliance.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Represents a match found during sanctions/PEP/adverse media screening.
 * Production-grade entity with comprehensive tracking and auditing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sanction_matches")
@CompoundIndexes({
    @CompoundIndex(name = "screening_entity_idx", def = "{'screeningId': 1, 'entityId': 1}"),
    @CompoundIndex(name = "list_confidence_idx", def = "{'listType': 1, 'confidenceScore': -1}"),
    @CompoundIndex(name = "status_created_idx", def = "{'matchStatus': 1, 'createdAt': -1}")
})
public class SanctionMatch {

    @Id
    private String id;

    /**
     * Reference to the screening that generated this match
     */
    @Indexed
    private String screeningId;

    /**
     * Entity that was matched (customer ID, transaction ID, etc.)
     */
    @Indexed
    private String entityId;

    /**
     * Type of entity (CUSTOMER, TRANSACTION, MERCHANT)
     */
    private String entityType;

    /**
     * Name of the entity being screened
     */
    private String entityName;

    // Match Details
    /**
     * Unique identifier for this match in the sanctions list
     */
    private String matchId;

    /**
     * Name that was matched in the sanctions list
     */
    private String matchedName;

    /**
     * Type of list (SANCTIONS, PEP, ADVERSE_MEDIA, WATCHLIST)
     */
    @Indexed
    private SanctionListType listType;

    /**
     * Specific list name (OFAC_SDN, UN_SANCTIONS, EU_SANCTIONS, etc.)
     */
    @Indexed
    private String listName;

    /**
     * Source of the list (OFAC, UN, EU, UK_HMT, INTERPOL, etc.)
     */
    private String listSource;

    /**
     * Match category (INDIVIDUAL, ENTITY, VESSEL, AIRCRAFT)
     */
    private String matchCategory;

    /**
     * Confidence score of the match (0.0 to 1.0)
     */
    private Double confidenceScore;

    /**
     * Match score (alternative to confidenceScore for compatibility)
     */
    private Double matchScore;

    /**
     * Match quality (EXACT, HIGH, MEDIUM, LOW, FUZZY)
     */
    private MatchQuality matchQuality;

    // Matched Entity Details
    /**
     * Date of birth of matched entity (if applicable)
     */
    private String matchedDateOfBirth;

    /**
     * Nationality/country of matched entity
     */
    private String matchedNationality;

    /**
     * Address of matched entity
     */
    private String matchedAddress;

    /**
     * Aliases/other names of matched entity
     */
    private List<String> matchedAliases;

    /**
     * Identification numbers of matched entity
     */
    private List<String> matchedIdentifications;

    // Risk Assessment
    /**
     * Risk level of this match
     */
    private AMLRiskLevel riskLevel;

    /**
     * Risk score (0-100)
     */
    private Integer riskScore;

    /**
     * Reasons for the risk assessment
     */
    private List<String> riskReasons;

    // Sanctions Details
    /**
     * Type of sanctions (FINANCIAL, TRAVEL, ARMS_EMBARGO, etc.)
     */
    private List<String> sanctionTypes;

    /**
     * Reason for sanctions/listing
     */
    private String listingReason;

    /**
     * Date when entity was added to the list
     */
    private LocalDateTime listedDate;

    /**
     * Programs associated with the listing (e.g., SYRIA, IRAN, TERRORISM)
     */
    private List<String> programs;

    /**
     * Remarks/notes from the sanctions list
     */
    private String remarks;

    // Review and Decision
    /**
     * Status of this match (PENDING_REVIEW, CONFIRMED, FALSE_POSITIVE, ESCALATED)
     */
    @Indexed
    private MatchStatus matchStatus;

    /**
     * Whether this match is a false positive
     */
    private Boolean falsePositive;

    /**
     * Reason for false positive determination
     */
    private String falsePositiveReason;

    /**
     * Reviewer ID who made the determination
     */
    private String reviewerId;

    /**
     * Review timestamp
     */
    private LocalDateTime reviewedAt;

    /**
     * Review notes
     */
    private String reviewNotes;

    /**
     * Whether this match was escalated
     */
    private Boolean escalated;

    /**
     * Escalation reason
     */
    private String escalationReason;

    // Actions Taken
    /**
     * Whether transaction was blocked due to this match
     */
    private Boolean transactionBlocked;

    /**
     * Whether account was blocked due to this match
     */
    private Boolean accountBlocked;

    /**
     * Whether SAR (Suspicious Activity Report) was filed
     */
    private Boolean sarFiled;

    /**
     * SAR reference number
     */
    private String sarReference;

    /**
     * Whether authorities were notified
     */
    private Boolean authoritiesNotified;

    // Matching Algorithm Details
    /**
     * Matching algorithm used (EXACT, FUZZY, PHONETIC, ML)
     */
    private String matchingAlgorithm;

    /**
     * Matching attributes that triggered the match
     */
    private List<String> matchingAttributes;

    /**
     * Detailed match scores per attribute
     */
    private Map<String, Double> attributeScores;

    // Additional Data
    /**
     * Raw data from the sanctions list provider
     */
    private Map<String, Object> rawMatchData;

    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;

    // Audit Fields
    /**
     * Correlation ID for tracing
     */
    @Indexed
    private String correlationId;

    /**
     * When this match was created
     */
    @Indexed
    private LocalDateTime createdAt;

    /**
     * When this match was last updated
     */
    private LocalDateTime updatedAt;

    /**
     * Version for optimistic locking
     */
    private Long version;

    /**
     * Enumeration for sanction list types
     */
    public enum SanctionListType {
        SANCTIONS,          // Official sanctions lists (OFAC, UN, EU)
        PEP,               // Politically Exposed Persons
        ADVERSE_MEDIA,     // Negative news/media
        WATCHLIST,         // Internal or external watchlists
        ENFORCEMENT,       // Law enforcement lists
        TERRORISM,         // Terrorism-related lists
        FINANCIAL_CRIME    // Financial crime watchlists
    }

    /**
     * Enumeration for match quality
     */
    public enum MatchQuality {
        EXACT,          // Exact match (100%)
        HIGH,           // High confidence (90-99%)
        MEDIUM,         // Medium confidence (70-89%)
        LOW,            // Low confidence (50-69%)
        FUZZY,          // Fuzzy/phonetic match (<50%)
        POSSIBLE        // Possible match requiring review
    }

    /**
     * Enumeration for match status
     */
    public enum MatchStatus {
        PENDING_REVIEW,     // Awaiting review
        UNDER_REVIEW,       // Currently being reviewed
        CONFIRMED,          // Confirmed as a true match
        FALSE_POSITIVE,     // Confirmed as false positive
        ESCALATED,          // Escalated to higher authority
        APPROVED,           // Approved despite match (with justification)
        REJECTED,           // Entity rejected due to match
        ON_HOLD            // On hold pending additional information
    }
}
