package com.waqiti.audit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Audit Record entity for comprehensive audit tracking
 */
@Entity
@Table(name = "audit_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String eventType;
    
    @Column(nullable = false)
    private String entityId;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Column
    private String userId;
    
    @Column
    private String action;
    
    @Column
    private String status;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @ElementCollection
    @CollectionTable(name = "audit_record_metadata")
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata;
}