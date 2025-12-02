package com.waqiti.compliance.model;

import com.waqiti.common.encryption.EncryptedStringConverter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Sanctioned Entity Model
 * 
 * Represents an entity on a sanctions list
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sanctioned_entities")
public class SanctionedEntity {
    
    @Id
    @Column(name = "sanctions_id")
    private String sanctionsId;
    
    @Column(name = "entity_name", nullable = false)
    private String entityName;
    
    @Column(name = "entity_type")
    private String entityType; // INDIVIDUAL, ENTITY, VESSEL, AIRCRAFT
    
    @Column(name = "sanctions_list")
    private String sanctionsList; // OFAC_SDN, EU, UN, UK, etc.
    
    @Column(name = "program_name")
    private String programName; // e.g., "IRAN", "SYRIA", "CYBER2"
    
    // Identifying information
    @Column(name = "date_of_birth")
    private String dateOfBirth;
    
    @Column(name = "place_of_birth")
    private String placeOfBirth;
    
    @Column(name = "nationality")
    private String nationality;
    
    @Column(name = "passport_number")
    private String passportNumber;
    
    @Column(name = "national_id")
    private String nationalId;
    
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "tax_id", length = 500)
    private String taxId; // PCI DSS: Encrypted Tax ID for sanctions screening
    
    // Address information
    @Column(name = "address")
    private String address;
    
    @Column(name = "city")
    private String city;
    
    @Column(name = "country")
    private String country;
    
    @Column(name = "postal_code")
    private String postalCode;
    
    // Aliases
    @ElementCollection
    @CollectionTable(name = "sanctioned_entity_aliases", 
                     joinColumns = @JoinColumn(name = "sanctions_id"))
    @Column(name = "alias")
    private List<String> aliases;
    
    // Associated entities
    @ElementCollection
    @CollectionTable(name = "sanctioned_entity_associates", 
                     joinColumns = @JoinColumn(name = "sanctions_id"))
    @Column(name = "associate_id")
    private List<String> associatedEntities;
    
    // Sanctions details
    @Column(name = "listing_date")
    private LocalDateTime listingDate;
    
    @Column(name = "reason", length = 2000)
    private String reason;
    
    @Column(name = "remarks", length = 2000)
    private String remarks;
    
    @Column(name = "is_active")
    private boolean isActive;
    
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;
    
    // Risk scoring
    @Column(name = "risk_score")
    private Integer riskScore; // 1-100
    
    @Column(name = "is_pep")
    private boolean isPEP; // Politically Exposed Person
    
    @Column(name = "is_rca")
    private boolean isRCA; // Relatives and Close Associates
    
    // Metadata
    @Column(name = "source_url")
    private String sourceUrl;
    
    @Column(name = "source_listing_url")
    private String sourceListingUrl;
    
    @Column(name = "data_source_version")
    private String dataSourceVersion;
}