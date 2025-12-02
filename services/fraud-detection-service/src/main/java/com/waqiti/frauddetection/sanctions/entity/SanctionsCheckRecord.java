package com.waqiti.frauddetection.sanctions.entity;

import com.waqiti.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Entity for storing OFAC sanctions screening results.
 * Part of AML/BSA compliance framework.
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-01
 */
@Entity
@Table(name = "sanctions_check_records", indexes = {
    @Index(name = "idx_sanctions_user_id", columnList = "user_id"),
    @Index(name = "idx_sanctions_check_status", columnList = "check_status"),
    @Index(name = "idx_sanctions_risk_level", columnList = "risk_level"),
    @Index(name = "idx_sanctions_checked_at", columnList = "checked_at"),
    @Index(name = "idx_sanctions_entity_type_id", columnList = "entity_type,entity_id"),
    @Index(name = "idx_sanctions_match_found", columnList = "match_found"),
    @Index(name = "idx_sanctions_list_version", columnList = "sanctions_list_version"),
    @Index(name = "idx_sanctions_transaction_id", columnList = "related_transaction_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SanctionsCheckRecord extends BaseEntity {

    /**
     * User being screened (if applicable)
     */
    @Column(name = "user_id")
    private UUID userId;

    /**
     * Entity type being screened (USER, MERCHANT, BENEFICIARY, COUNTERPARTY)
     */
    @Column(name = "entity_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private EntityType entityType;

    /**
     * Specific entity ID being screened
     */
    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    /**
     * Full name being checked against sanctions lists
     */
    @Column(name = "checked_name", nullable = false, length = 500)
    private String checkedName;

    /**
     * Date of birth (if available for enhanced matching)
     */
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /**
     * Nationality/Country (ISO 3166-1 alpha-3)
     */
    @Column(name = "nationality", length = 3)
    private String nationality;

    /**
     * Address being checked
     */
    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    /**
     * Country of residence (ISO 3166-1 alpha-3)
     */
    @Column(name = "country", length = 3)
    private String country;

    /**
     * Identification number (passport, SSN, etc.) - encrypted
     */
    @Column(name = "identification_number", length = 255)
    private String identificationNumber;

    /**
     * Type of identification (PASSPORT, NATIONAL_ID, SSN, etc.)
     */
    @Column(name = "identification_type", length = 50)
    private String identificationType;

    /**
     * Related transaction ID (if screening triggered by transaction)
     */
    @Column(name = "related_transaction_id")
    private UUID relatedTransactionId;

    /**
     * Transaction amount (if applicable)
     */
    @Column(name = "transaction_amount", precision = 19, scale = 4)
    private BigDecimal transactionAmount;

    /**
     * Transaction currency (ISO 4217)
     */
    @Column(name = "transaction_currency", length = 3)
    private String transactionCurrency;

    /**
     * Whether a match was found on sanctions list
     */
    @Column(name = "match_found", nullable = false)
    private Boolean matchFound = false;

    /**
     * Number of potential matches found
     */
    @Column(name = "match_count", nullable = false)
    private Integer matchCount = 0;

    /**
     * Overall match score (0-100, higher = more confident match)
     */
    @Column(name = "match_score", precision = 5, scale = 2)
    private BigDecimal matchScore;

    /**
     * Risk level assessment (LOW, MEDIUM, HIGH, CRITICAL)
     */
    @Column(name = "risk_level", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel = RiskLevel.LOW;

    /**
     * Check status (PENDING, IN_PROGRESS, COMPLETED, FAILED, MANUAL_REVIEW)
     */
    @Column(name = "check_status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private CheckStatus checkStatus = CheckStatus.PENDING;

    /**
     * Sanctions list version used for screening
     */
    @Column(name = "sanctions_list_version", nullable = false, length = 50)
    private String sanctionsListVersion;

    /**
     * Timestamp of the check
     */
    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    /**
     * Duration of the check in milliseconds
     */
    @Column(name = "check_duration_ms")
    private Long checkDurationMs;

    /**
     * Source of the check (REGISTRATION, TRANSACTION, PERIODIC_REVIEW, MANUAL)
     */
    @Column(name = "check_source", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private CheckSource checkSource;

    /**
     * OFAC SDN list checked
     */
    @Column(name = "ofac_sdn_checked", nullable = false)
    private Boolean ofacSdnChecked = true;

    /**
     * EU sanctions list checked
     */
    @Column(name = "eu_sanctions_checked", nullable = false)
    private Boolean euSanctionsChecked = true;

    /**
     * UN sanctions list checked
     */
    @Column(name = "un_sanctions_checked", nullable = false)
    private Boolean unSanctionsChecked = true;

    /**
     * UK sanctions list checked
     */
    @Column(name = "uk_sanctions_checked", nullable = false)
    private Boolean ukSanctionsChecked = false;

    /**
     * Matched sanctions list names (if match found)
     */
    @Type(JsonBinaryType.class)
    @Column(name = "matched_lists", columnDefinition = "jsonb")
    private List<String> matchedLists;

    /**
     * Detailed match information (scores, reasons, list entries)
     */
    @Type(JsonBinaryType.class)
    @Column(name = "match_details", columnDefinition = "jsonb")
    private List<MatchDetail> matchDetails;

    /**
     * False positive indicator (if review determined no actual match)
     */
    @Column(name = "false_positive")
    private Boolean falsePositive;

    /**
     * Resolution decision (CLEARED, BLOCKED, ESCALATED)
     */
    @Column(name = "resolution", length = 30)
    @Enumerated(EnumType.STRING)
    private Resolution resolution;

    /**
     * Resolved by (user ID of compliance officer)
     */
    @Column(name = "resolved_by")
    private UUID resolvedBy;

    /**
     * Resolution timestamp
     */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /**
     * Resolution notes
     */
    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    /**
     * Compliance case ID (if escalated)
     */
    @Column(name = "compliance_case_id")
    private UUID complianceCaseId;

    /**
     * SAR (Suspicious Activity Report) filed
     */
    @Column(name = "sar_filed")
    private Boolean sarFiled = false;

    /**
     * SAR filing ID
     */
    @Column(name = "sar_filing_id")
    private UUID sarFilingId;

    /**
     * Idempotency key for duplicate prevention
     */
    @Column(name = "idempotency_key", unique = true, nullable = false, length = 255)
    private String idempotencyKey;

    /**
     * Check requested by (user ID or system)
     */
    @Column(name = "requested_by")
    private UUID requestedBy;

    /**
     * IP address of request origin
     */
    @Column(name = "request_ip_address", length = 45)
    private String requestIpAddress;

    /**
     * User agent of request
     */
    @Column(name = "request_user_agent", length = 500)
    private String requestUserAgent;

    /**
     * Automated decision made (if no manual review needed)
     */
    @Column(name = "automated_decision")
    private Boolean automatedDecision = false;

    /**
     * Confidence level of automated decision (0-100)
     */
    @Column(name = "automated_decision_confidence", precision = 5, scale = 2)
    private BigDecimal automatedDecisionConfidence;

    /**
     * Next scheduled review date (for periodic screening)
     */
    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    /**
     * API provider used (OFAC_API, COMPLYADVANTAGE, WORLDCHECK, etc.)
     */
    @Column(name = "api_provider", length = 50)
    private String apiProvider;

    /**
     * External reference ID from sanctions API provider
     */
    @Column(name = "external_reference_id", length = 255)
    private String externalReferenceId;

    /**
     * Fuzzy matching algorithms used (LEVENSHTEIN, SOUNDEX, METAPHONE, etc.)
     */
    @Type(JsonBinaryType.class)
    @Column(name = "matching_algorithms", columnDefinition = "jsonb")
    private List<String> matchingAlgorithms;

    /**
     * Additional screening context and metadata
     */
    @Type(JsonBinaryType.class)
    @Column(name = "screening_metadata", columnDefinition = "jsonb")
    private java.util.Map<String, Object> screeningMetadata;

    public enum EntityType {
        USER,
        MERCHANT,
        BENEFICIARY,
        COUNTERPARTY,
        BUSINESS_ENTITY,
        PAYMENT_ORIGINATOR,
        PAYMENT_RECIPIENT
    }

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum CheckStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        MANUAL_REVIEW,
        TIMEOUT,
        API_ERROR
    }

    public enum CheckSource {
        REGISTRATION,
        TRANSACTION,
        PERIODIC_REVIEW,
        MANUAL,
        THRESHOLD_TRIGGER,
        RISK_BASED_TRIGGER,
        REGULATORY_REQUIREMENT
    }

    public enum Resolution {
        CLEARED,
        BLOCKED,
        ESCALATED,
        PENDING_INVESTIGATION,
        WHITELISTED,
        BLACKLISTED
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MatchDetail {
        private String listName;
        private String listType;
        private String entryId;
        private String matchedName;
        private String matchType; // EXACT, FUZZY, PARTIAL
        private BigDecimal confidence;
        private String algorithm;
        private Integer levenshteinDistance;
        private String soundexCode;
        private String metaphoneCode;
        private List<String> aliases;
        private String nationality;
        private String designation;
        private String program;
        private LocalDate listingDate;
        private String remarks;
        private java.util.Map<String, Object> additionalInfo;
    }
}
