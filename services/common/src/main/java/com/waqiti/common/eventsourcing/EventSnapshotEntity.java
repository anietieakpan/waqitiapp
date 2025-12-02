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
 * JPA entity for storing aggregate snapshots
 */
@Entity
@Table(name = "event_snapshots",
       indexes = {
           @Index(name = "idx_aggregate_id_version", columnList = "aggregateId,version"),
           @Index(name = "idx_aggregate_type", columnList = "aggregateType"),
           @Index(name = "idx_timestamp", columnList = "timestamp")
       })
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventSnapshotEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 100)
    private String aggregateId;
    
    @Column(nullable = false, length = 50)
    private String aggregateType;
    
    @Column(nullable = false)
    private Long version;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String snapshotData;
    
    @Column(nullable = false)
    private Instant timestamp;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(nullable = false)
    private Long eventCount;
    
    @Column(length = 100)
    private String snapshotReason;
    
    @Column
    private Long dataSize;
    
    @Version
    private Long optimisticVersion;
}