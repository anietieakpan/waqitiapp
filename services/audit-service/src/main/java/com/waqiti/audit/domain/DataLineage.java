package com.waqiti.audit.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Entity for tracking data lineage and transformations
 */
@Entity
@Table(name = "data_lineage")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataLineage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "source_system", nullable = false)
    private String sourceSystem;
    
    @Column(name = "source_entity_type")
    private String sourceEntityType;
    
    @Column(name = "source_entity_id")
    private String sourceEntityId;
    
    @Column(name = "target_system", nullable = false)
    private String targetSystem;
    
    @Column(name = "target_entity_type")
    private String targetEntityType;
    
    @Column(name = "target_entity_id")
    private String targetEntityId;
    
    @Column(name = "transformation_type")
    private String transformationType;
    
    @Column(name = "transformation_details", columnDefinition = "TEXT")
    private String transformationDetails;
    
    @Column(name = "entity_type")
    private String entityType;
    
    @Column(name = "entity_id")
    private String entityId;
    
    @Column(name = "data_flow_id")
    private String dataFlowId;
    
    @Column(name = "operation")
    private String operation;
    
    @ElementCollection
    @CollectionTable(name = "lineage_metadata", 
                      joinColumns = @JoinColumn(name = "lineage_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata;
    
    @Column(name = "data_quality_score")
    private Double dataQualityScore;
    
    @Column(name = "is_critical_path")
    private Boolean isCriticalPath;
    
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;
    
    @Column(name = "record_count")
    private Long recordCount;
    
    @Column(name = "error_count")
    private Integer errorCount;
    
    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "created_by")
    private String createdBy;
    
    @Column(name = "correlation_id")
    private String correlationId;
    
    @Column(name = "business_date")
    private LocalDateTime businessDate;
}