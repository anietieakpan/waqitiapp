package com.waqiti.compliance.model.sanctions;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sanctioned Entity - Comprehensive OFAC/EU/UN Sanctions Entity
 *
 * Represents individuals, entities, vessels, and aircraft on sanctions lists
 * from OFAC (US), EU, and UN sources.
 *
 * CRITICAL COMPLIANCE REQUIREMENT:
 * This entity must match the official sanctions list schema for accurate
 * screening and regulatory compliance.
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2025-11-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sanctioned_entities", indexes = {
        @Index(name = "idx_sanctioned_entities_metadata", columnList = "list_metadata_id"),
        @Index(name = "idx_sanctioned_entities_active", columnList = "is_active"),
        @Index(name = "idx_sanctioned_entities_type", columnList = "entity_type"),
        @Index(name = "idx_sanctioned_entities_source_id", columnList = "source_id"),
        @Index(name = "idx_sanctioned_entities_normalized", columnList = "name_normalized"),
        @Index(name = "idx_sanctioned_entities_nationality", columnList = "nationality"),
        @Index(name = "idx_sanctioned_entities_country", columnList = "country"),
        @Index(name = "idx_sanctioned_entities_dob", columnList = "date_of_birth"),
        @Index(name = "idx_sanctioned_entities_listing", columnList = "listing_date")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_sanctioned_entity_source", columnNames = {"list_metadata_id", "source_id"})
})
public class SanctionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    // List metadata reference
    @Column(name = "list_metadata_id", nullable = false)
    private UUID listMetadataId;

    // Entity identification
    @Column(name = "source_id", nullable = false, length = 50)
    private String sourceId; // Original ID from source list

    @Column(name = "entity_type", nullable = false, length = 20)
    private String entityType; // INDIVIDUAL, ENTITY, VESSEL, AIRCRAFT

    // Primary information
    @Column(name = "primary_name", nullable = false, length = 500)
    private String primaryName;

    @Column(name = "name_normalized", nullable = false, length = 500)
    private String nameNormalized; // Normalized for matching

    // Individual-specific fields
    @Column(name = "title", length = 100)
    private String title;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "maiden_name", length = 100)
    private String maidenName;

    @Column(name = "gender", length = 10)
    private String gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "place_of_birth", length = 255)
    private String placeOfBirth;

    // Entity-specific fields
    @Column(name = "organization_type", length = 100)
    private String organizationType;

    // Location information
    @Column(name = "nationality", length = 3) // ISO 3166-1 alpha-3
    private String nationality;

    @Column(name = "country_of_residence", length = 3)
    private String countryOfResidence;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state_province", length = 100)
    private String stateProvince;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country", length = 3)
    private String country;

    // Identification documents
    @Column(name = "passport_number", length = 50)
    private String passportNumber;

    @Column(name = "passport_country", length = 3)
    private String passportCountry;

    @Column(name = "national_id_number", length = 50)
    private String nationalIdNumber;

    @Column(name = "national_id_country", length = 3)
    private String nationalIdCountry;

    @Column(name = "tax_id_number", length = 50)
    private String taxIdNumber;

    @Column(name = "ssn", length = 20)
    private String ssn;

    // Vessel/Aircraft specific
    @Column(name = "vessel_call_sign", length = 50)
    private String vesselCallSign;

    @Column(name = "vessel_type", length = 100)
    private String vesselType;

    @Column(name = "vessel_tonnage")
    private Integer vesselTonnage;

    @Column(name = "vessel_flag", length = 3)
    private String vesselFlag;

    @Column(name = "vessel_owner", length = 255)
    private String vesselOwner;

    @Column(name = "aircraft_tail_number", length = 50)
    private String aircraftTailNumber;

    @Column(name = "aircraft_manufacturer", length = 100)
    private String aircraftManufacturer;

    @Column(name = "aircraft_model", length = 100)
    private String aircraftModel;

    // Sanctions details
    @Column(name = "program_name", length = 200)
    private String programName;

    @Column(name = "sanctions_type", length = 100)
    private String sanctionsType; // BLOCKING, SDN, ASSET_FREEZE, etc.

    @Column(name = "listing_date")
    private LocalDate listingDate;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "legal_basis", columnDefinition = "TEXT")
    private String legalBasis;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    // Risk scoring
    @Column(name = "match_score_threshold", precision = 5, scale = 2)
    private BigDecimal matchScoreThreshold; // Fuzzy match threshold

    @Column(name = "risk_level", length = 20)
    private String riskLevel; // HIGH, CRITICAL

    // Status
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "removed_date")
    private LocalDate removedDate;

    @Column(name = "removal_reason", columnDefinition = "TEXT")
    private String removalReason;

    // Audit
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (isActive == null) {
            isActive = true;
        }
        if (matchScoreThreshold == null) {
            matchScoreThreshold = new BigDecimal("85.00");
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
