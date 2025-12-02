package com.waqiti.audit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Failure Pattern entity for tracking and analyzing failure patterns
 */
@Entity
@Table(name = "failure_patterns")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailurePattern {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String patternType;
    
    @Column(nullable = false)
    private String failureReason;
    
    @Column(nullable = false)
    private String severity;
    
    @Column(nullable = false)
    private LocalDateTime detectedAt;
    
    @Column
    private Integer occurrenceCount;
    
    @Column
    private String entityId;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column
    private String status; // ACTIVE, RESOLVED, INVESTIGATING
}