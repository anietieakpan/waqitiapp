package com.waqiti.crypto.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Entity representing a sanctioned cryptocurrency address
 */
@Entity
@Table(name = "sanctioned_addresses", indexes = {
    @Index(name = "idx_sanctioned_address", columnList = "address", unique = true),
    @Index(name = "idx_sanctioned_list", columnList = "sanctions_list"),
    @Index(name = "idx_sanctioned_active", columnList = "active"),
    @Index(name = "idx_sanctioned_jurisdiction", columnList = "jurisdiction")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class SanctionedAddress {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank
    @Column(nullable = false, unique = true)
    private String address;
    
    @NotBlank
    @Column(name = "sanctions_list", nullable = false)
    private String sanctionsList; // OFAC, EU, UN, etc.
    
    @Column
    private String jurisdiction; // US, EU, UN, etc.
    
    @Column(length = 500)
    private String description;
    
    @Column(name = "entity_name")
    private String entityName; // Name of sanctioned entity
    
    @Column(name = "listing_date")
    private LocalDateTime listingDate;
    
    @Column(name = "reference_number")
    private String referenceNumber; // Sanctions list reference
    
    @NotNull
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;    
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @Column
    private String createdBy;
    
    @Column
    private String updatedBy;
}