package com.waqiti.common.audit;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity for data access audit entries
 */
@Entity
@Table(name = "data_access_audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataAccessEntity {
    
    @Id
    private UUID id;
    
    @Column(name = "user_id")
    private String userId;
    
    @Column(name = "data_type")
    private String dataType;
    
    @Column(name = "table_name")
    private String tableName;
    
    @Column(name = "operation")
    private String operation;
    
    @Column(name = "record_id")
    private String recordId;
    
    @Column(name = "field_name")
    private String fieldName;
    
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;
    
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;
    
    @Column(name = "access_reason")
    private String accessReason;
    
    @Column(name = "accessed_by")
    private String accessedBy;
    
    @Column(name = "purpose")
    private String purpose;
    
    @Column(name = "sql_query", columnDefinition = "TEXT")
    private String sqlQuery;
    
    @Column(name = "query_execution_time_ms")
    private Long queryExecutionTimeMs;
    
    @Column(name = "records_affected")
    private Integer recordsAffected;
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "session_id")
    private String sessionId;
    
    @Column(name = "application_name")
    private String applicationName;
    
    @Column(name = "timestamp")
    private LocalDateTime timestamp;
    
    @ElementCollection
    @CollectionTable(name = "data_access_metadata", joinColumns = @JoinColumn(name = "data_access_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata;
    
    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}