package com.waqiti.kyc.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing a Politically Exposed Person (PEP) database entry
 * 
 * PEPs require Enhanced Due Diligence (EDD) under global AML regulations including:
 * - FATF Recommendations
 * - EU 4th & 5th AML Directives
 * - US Bank Secrecy Act
 * - UK Money Laundering Regulations
 * 
 * @author Waqiti Compliance Team
 * @version 2.0.0
 * @since 2025-01-27
 */
@Entity
@Table(name = "pep_entries", indexes = {
    @Index(name = "idx_pep_full_name", columnList = "full_name"),
    @Index(name = "idx_pep_status", columnList = "pep_status"),
    @Index(name = "idx_pep_category", columnList = "pep_category"),
    @Index(name = "idx_pep_country", columnList = "country"),
    @Index(name = "idx_pep_risk_level", columnList = "risk_level"),
    @Index(name = "idx_pep_passport", columnList = "passport_number"),
    @Index(name = "idx_pep_national_id", columnList = "national_id"),
    @Index(name = "idx_pep_active", columnList = "active"),
    @Index(name = "idx_pep_review_date", columnList = "last_review_date")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PEPEntry {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
    
    @Column(name = "full_name", nullable = false, length = 500)
    private String fullName;
    
    @ElementCollection
    @CollectionTable(name = "pep_aliases",
                    joinColumns = @JoinColumn(name = "pep_entry_id"))
    @Column(name = "alias", length = 500)
    private List<String> aliases;
    
    @Column(name = "pep_status", nullable = false, length = 20)
    private String pepStatus; // CURRENT, FORMER, DECEASED
    
    @Column(name = "pep_category", nullable = false, length = 50)
    private String pepCategory; // PEP, RCA (Relative/Close Associate), FAMILY
    
    @Column(name = "position_title", length = 300)
    private String positionTitle;
    
    @Column(name = "position_type", length = 100)
    private String positionType; // HEAD_OF_STATE, MINISTER, MILITARY_OFFICER, JUDGE, DIPLOMAT, etc.
    
    @Column(name = "organization", length = 300)
    private String organization;
    
    @Column(name = "country", length = 100)
    private String country;
    
    @Column(name = "nationality", length = 100)
    private String nationality;
    
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    
    @Column(name = "place_of_birth", length = 200)
    private String placeOfBirth;
    
    @Column(name = "passport_number", length = 100)
    private String passportNumber;
    
    @Column(name = "national_id", length = 100)
    private String nationalId;
    
    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;
    
    @Column(name = "exit_date")
    private LocalDate exitDate;
    
    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    
    @Column(name = "relationship_to_pep", length = 200)
    private String relationshipToPEP; // For RCA entries: SPOUSE, CHILD, BUSINESS_PARTNER, etc.
    
    @Column(name = "linked_pep_id")
    private UUID linkedPEPId; // For RCA entries, links to the primary PEP
    
    @Column(name = "source_of_wealth", columnDefinition = "TEXT")
    private String sourceOfWealth;
    
    @Column(name = "known_associates", columnDefinition = "TEXT")
    private String knownAssociates;
    
    @Column(name = "adverse_information", columnDefinition = "TEXT")
    private String adverseInformation;
    
    @Column(name = "monitoring_required", nullable = false)
    @Builder.Default
    private Boolean monitoringRequired = true;
    
    @Column(name = "edd_required", nullable = false)
    @Builder.Default
    private Boolean eddRequired = true;
    
    @Column(name = "last_review_date")
    private LocalDate lastReviewDate;
    
    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;
    
    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;
    
    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;
    
    @Column(name = "data_source", length = 200)
    private String dataSource;
    
    @Column(name = "reference_id", length = 100)
    private String referenceId;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "last_sync_date")
    private LocalDateTime lastSyncDate;
}