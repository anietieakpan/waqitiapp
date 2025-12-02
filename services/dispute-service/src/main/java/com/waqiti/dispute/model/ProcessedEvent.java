package com.waqiti.dispute.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Entity to track processed events for idempotency
 * Prevents duplicate processing of Kafka events
 * Enhanced with additional fields for distributed idempotency
 */
@Entity
@Table(name = "processed_events", indexes = {
    @Index(name = "idx_idempotency_key", columnList = "idempotencyKey", unique = true),
    @Index(name = "idx_event_id", columnList = "eventId"),
    @Index(name = "idx_event_key", columnList = "eventKey"),
    @Index(name = "idx_expires_at", columnList = "expiresAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String eventId;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "event_key", nullable = false)
    private String eventKey;

    @Column(name = "operation_id", nullable = false)
    private String operationId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private Instant processedAt;

    @Column(name = "processed_at_local")
    private LocalDateTime processedAtLocal;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    private String disputeId;

    private Boolean success;

    private String errorMessage;

    @Column(columnDefinition = "TEXT")
    private String result;
}
