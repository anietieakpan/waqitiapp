package com.waqiti.lending.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;

/**
 * Processed Event Entity
 * Tracks processed Kafka events for idempotency
 */
@Entity
@Table(name = "processed_event", indexes = {
    @Index(name = "idx_processed_event_id", columnList = "event_id", unique = true),
    @Index(name = "idx_processed_event_type", columnList = "event_type"),
    @Index(name = "idx_processed_event_processed_at", columnList = "processed_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent extends BaseEntity {

    @Column(name = "event_id", nullable = false, unique = true, length = 255)
    @NotBlank
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    @NotBlank
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    @NotNull
    @Builder.Default
    private Instant processedAt = Instant.now();

    @Column(name = "processing_result", length = 50)
    private String processingResult;

    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Override
    public String toString() {
        return "ProcessedEvent{" +
                "eventId='" + eventId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", processedAt=" + processedAt +
                '}';
    }
}
