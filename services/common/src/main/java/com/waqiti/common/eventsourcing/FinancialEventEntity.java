package com.waqiti.common.eventsourcing;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * JPA entity for storing financial events in event store
 */
@Entity
@Table(name = "financial_events",
       indexes = {
           @Index(name = "idx_aggregate_id_sequence", columnList = "aggregateId,sequenceNumber"),
           @Index(name = "idx_aggregate_id_version", columnList = "aggregateId,eventVersion"),
           @Index(name = "idx_event_type_timestamp", columnList = "eventType,timestamp"),
           @Index(name = "idx_timestamp", columnList = "timestamp"),
           @Index(name = "idx_correlation_id", columnList = "correlationId")
       })
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialEventEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 100)
    private String aggregateId;
    
    @Column(nullable = false, length = 50)
    private String aggregateType;
    
    @Column(nullable = false, length = 100)
    private String eventType;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String eventData;
    
    @Column(nullable = false)
    private Long eventVersion;
    
    @Column(nullable = false)
    private Long sequenceNumber;
    
    @Column(nullable = false)
    private Instant timestamp;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(length = 100)
    private String userId;
    
    @Column(length = 100)
    private String correlationId;
    
    @Column(length = 100)
    private String causationId;
    
    @Column(length = 50)
    private String eventSource;
    
    @Column
    private Integer eventSchemaVersion;
    
    // Optimistic locking
    @Version
    private Long version;
}