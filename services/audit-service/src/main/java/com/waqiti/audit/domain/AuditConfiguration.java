package com.waqiti.audit.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Entity for audit configuration and policies
 */
@Entity
@Table(name = "audit_configurations", indexes = {
    @Index(name = "idx_config_name", columnList = "configuration_name", unique = true),
    @Index(name = "idx_config_active", columnList = "is_active"),
    @Index(name = "idx_config_type", columnList = "configuration_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditConfiguration {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID configurationId;
    
    @Column(name = "configuration_name", nullable = false, unique = true)
    private String configurationName;
    
    @Column(name = "configuration_type", nullable = false)
    private String configurationType; // RETENTION_POLICY, ALERT_RULE, COMPLIANCE_SETTING, etc.
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
    
    @Column(name = "priority")
    private Integer priority;
    
    // Retention policies
    @Column(name = "retention_days")
    private Integer retentionDays;
    
    @Column(name = "archive_after_days")
    private Integer archiveAfterDays;
    
    @Column(name = "delete_after_days")
    private Integer deleteAfterDays;
    
    // Alert configurations
    @Column(name = "alert_enabled")
    private Boolean alertEnabled;
    
    @Column(name = "alert_threshold")
    private Integer alertThreshold;
    
    @Column(name = "alert_frequency_minutes")
    private Integer alertFrequencyMinutes;
    
    @Column(name = "alert_recipients")
    private String alertRecipients;
    
    // Compliance settings
    @Column(name = "compliance_framework")
    private String complianceFramework;
    
    @Column(name = "audit_level")
    private String auditLevel; // MINIMAL, STANDARD, DETAILED, FULL
    
    @Column(name = "encryption_required")
    private Boolean encryptionRequired;
    
    @Column(name = "integrity_check_required")
    private Boolean integrityCheckRequired;
    
    // Event filtering
    @Column(name = "included_event_types", columnDefinition = "TEXT")
    private String includedEventTypes;
    
    @Column(name = "excluded_event_types", columnDefinition = "TEXT")
    private String excludedEventTypes;
    
    @Column(name = "included_services", columnDefinition = "TEXT")
    private String includedServices;
    
    @Column(name = "excluded_services", columnDefinition = "TEXT")
    private String excludedServices;
    
    // Performance settings
    @Column(name = "batch_size")
    private Integer batchSize;
    
    @Column(name = "async_processing")
    private Boolean asyncProcessing;
    
    @Column(name = "compression_enabled")
    private Boolean compressionEnabled;
    
    // Additional configuration
    @ElementCollection
    @CollectionTable(name = "audit_config_settings", 
                      joinColumns = @JoinColumn(name = "configuration_id"))
    @MapKeyColumn(name = "setting_key")
    @Column(name = "setting_value")
    private Map<String, String> additionalSettings;
    
    @Column(name = "created_by")
    private String createdBy;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_by")
    private String updatedBy;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;
    
    @Column(name = "effective_until")
    private LocalDateTime effectiveUntil;
    
    @Version
    @Column(name = "version")
    private Long version;
}