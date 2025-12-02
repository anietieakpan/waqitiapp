package com.waqiti.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sanction_list_entries",
    indexes = {
        @Index(name = "idx_sanction_list_entity_id", columnList = "entity_id", unique = true),
        @Index(name = "idx_sanction_list_name", columnList = "name"),
        @Index(name = "idx_sanction_list_type", columnList = "list_type"),
        @Index(name = "idx_sanction_list_country", columnList = "country_code"),
        @Index(name = "idx_sanction_list_status", columnList = "status")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SanctionListEntry {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    
    @Column(name = "entity_id", unique = true, nullable = false)
    private String entityId;
    
    @Column(name = "name", nullable = false, columnDefinition = "TEXT")
    private String name;
    
    @Column(name = "aliases", columnDefinition = "TEXT")
    private String aliases;
    
    @Column(name = "entity_type", length = 50)
    private String entityType;
    
    @Column(name = "list_type", nullable = false, length = 50)
    private String listType;
    
    @Column(name = "list_name", columnDefinition = "TEXT")
    private String listName;
    
    @Column(name = "source", length = 100)
    private String source;
    
    @Column(name = "country_code", length = 10)
    private String countryCode;
    
    @Column(name = "nationality", length = 10)
    private String nationality;
    
    @Column(name = "date_of_birth")
    private LocalDateTime dateOfBirth;
    
    @Column(name = "place_of_birth", columnDefinition = "TEXT")
    private String placeOfBirth;
    
    @Column(name = "passport_number")
    private String passportNumber;
    
    @Column(name = "national_id")
    private String nationalId;
    
    @Column(name = "address", columnDefinition = "TEXT")
    private String address;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;
    
    @Column(name = "designation", columnDefinition = "TEXT")
    private String designation;
    
    @Column(name = "status", nullable = false, length = 20)
    private String status;
    
    @Column(name = "added_date")
    private LocalDateTime addedDate;
    
    @Column(name = "modified_date")
    private LocalDateTime modifiedDate;
    
    @Column(name = "removed_date")
    private LocalDateTime removedDate;
    
    @Column(name = "reference_url", columnDefinition = "TEXT")
    private String referenceUrl;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = "ACTIVE";
        }
        if (addedDate == null) {
            addedDate = LocalDateTime.now();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        modifiedDate = LocalDateTime.now();
    }
}