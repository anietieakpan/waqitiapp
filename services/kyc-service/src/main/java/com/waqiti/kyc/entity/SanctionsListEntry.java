package com.waqiti.kyc.entity;

import com.waqiti.common.encryption.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing an entry in sanctions lists (OFAC, UN, EU, UK, etc.)
 * 
 * This entity stores sanctioned individuals and entities for regulatory compliance.
 * Data is synchronized from official government sources on a daily basis.
 * 
 * @author Waqiti Compliance Team
 * @version 2.0.0
 * @since 2025-01-27
 */
@Entity
@Table(name = "sanctions_list_entries", indexes = {
    @Index(name = "idx_sanctions_full_name", columnList = "full_name"),
    @Index(name = "idx_sanctions_list_type", columnList = "list_type"),
    @Index(name = "idx_sanctions_nationality", columnList = "nationality"),
    @Index(name = "idx_sanctions_dob", columnList = "date_of_birth"),
    @Index(name = "idx_sanctions_passport", columnList = "passport_number"),
    @Index(name = "idx_sanctions_national_id", columnList = "national_id"),
    @Index(name = "idx_sanctions_active", columnList = "active"),
    @Index(name = "idx_sanctions_updated", columnList = "last_updated")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SanctionsListEntry {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
    
    @Column(name = "full_name", nullable = false, length = 500)
    private String fullName;
    
    @ElementCollection
    @CollectionTable(name = "sanctions_aliases", 
                    joinColumns = @JoinColumn(name = "sanctions_entry_id"))
    @Column(name = "alias", length = 500)
    private List<String> aliases;
    
    @Column(name = "list_type", nullable = false, length = 50)
    private String listType; // OFAC, UN, EU, UK_HMT, etc.
    
    @Column(name = "entry_type", nullable = false, length = 50)
    private String entryType; // INDIVIDUAL, ENTITY, VESSEL, AIRCRAFT
    
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    
    @Column(name = "place_of_birth", length = 200)
    private String placeOfBirth;
    
    @Column(name = "nationality", length = 100)
    private String nationality;
    
    @ElementCollection
    @CollectionTable(name = "sanctions_citizenships",
                    joinColumns = @JoinColumn(name = "sanctions_entry_id"))
    @Column(name = "citizenship", length = 100)
    private List<String> citizenships;
    
    @Column(name = "passport_number", length = 100)
    private String passportNumber;
    
    @Column(name = "national_id", length = 100)
    private String nationalId;
    
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "tax_id", length = 500)
    private String taxId; // PCI DSS: Encrypted Tax ID for sanctions matching
    
    @Column(name = "company_registration_number", length = 100)
    private String companyRegistrationNumber;
    
    @Column(name = "address", columnDefinition = "TEXT")
    private String address;
    
    @Column(name = "program_name", length = 200)
    private String programName; // e.g., "Counter Terrorism", "Narcotics Trafficking"
    
    @Column(name = "sanctions_reason", columnDefinition = "TEXT")
    private String sanctionsReason;
    
    @Column(name = "listing_date")
    private LocalDate listingDate;
    
    @Column(name = "last_updated")
    private LocalDate lastUpdated;
    
    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;
    
    @Column(name = "risk_score", precision = 5, scale = 2)
    private BigDecimal riskScore;
    
    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;
    
    @Column(name = "source_url", length = 500)
    private String sourceUrl;
    
    @Column(name = "reference_number", length = 100)
    private String referenceNumber;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "last_sync_date")
    private LocalDateTime lastSyncDate;
}