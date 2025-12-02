package com.waqiti.audit.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Audit Alert domain entity
 */
@Entity
@Table(name = "audit_alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditAlert {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String alertType;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AlertSeverity severity;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AlertStatus status;
    
    @Column(nullable = false)
    private String message;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @Column
    private LocalDateTime acknowledgedAt;
    
    @Column
    private LocalDateTime resolvedAt;
    
    @Column
    private String assignedTo;
    
    @Column(columnDefinition = "TEXT")
    private String description;
}