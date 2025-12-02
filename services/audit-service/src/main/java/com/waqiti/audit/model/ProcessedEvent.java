package com.waqiti.audit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity for tracking processed events to ensure idempotency
 */
@Entity
@Table(name = "processed_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEvent {
    
    @Id
    private String eventId;
    
    @Column(nullable = false)
    private String eventType;
    
    @Column(nullable = false)
    private LocalDateTime processedAt;
    
    @Column(nullable = false)
    private String status;
    
    @Column
    private String entityId;
    
    @Column
    private String processingServiceName;
}