package com.waqiti.security.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "failed_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String eventId;
    
    @Column(nullable = false)
    private String topic;
    
    @Column(columnDefinition = "TEXT")
    private Object payload;
    
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(nullable = false)
    private Instant createdAt;
}