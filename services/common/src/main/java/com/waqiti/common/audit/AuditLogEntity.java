package com.waqiti.common.audit;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity for audit log entries
 */
@Entity
@Table(name = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntity {
    
    @Id
    private UUID id;
    
    @Column(name = "transaction_id")
    private String transactionId;
    
    @Column(name = "user_id")
    private String userId;
    
    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    private AuditType type;
    
    @Column(name = "action")
    private String action;
    
    @Column(name = "status")
    private String status;
    
    @Column(name = "entity_type")
    private String entityType;
    
    @Column(name = "entity_id")
    private String entityId;
    
    @Column(name = "event_type")
    private String eventType;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "amount")
    private BigDecimal amount;
    
    @Column(name = "currency")
    private String currency;
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "user_agent")
    private String userAgent;
    
    @Column(name = "session_id")
    private String sessionId;
    
    @Column(name = "timestamp")
    private LocalDateTime timestamp;
    
    @Column(name = "severity")
    private String severity;
    
    @Column(name = "source_service")
    private String sourceService;
    
    @Column(name = "correlation_id")
    private String correlationId;
    
    @ElementCollection
    @CollectionTable(name = "audit_log_metadata", joinColumns = @JoinColumn(name = "audit_log_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata;
    
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private JsonNode metadataJson;
    
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