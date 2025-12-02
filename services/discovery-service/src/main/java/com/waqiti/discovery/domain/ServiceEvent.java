package com.waqiti.discovery.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Service Event Entity
 * Tracks important events in the service discovery system
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Entity
@Table(name = "service_event",
    indexes = {
        @Index(name = "idx_service_event_service", columnList = "service_id"),
        @Index(name = "idx_service_event_instance", columnList = "instance_id"),
        @Index(name = "idx_service_event_type", columnList = "event_type"),
        @Index(name = "idx_service_event_level", columnList = "event_level"),
        @Index(name = "idx_service_event_timestamp", columnList = "event_timestamp"),
        @Index(name = "idx_service_event_processed", columnList = "processed")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_event_id", columnNames = "event_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(callSuper = true)
public class ServiceEvent extends BaseEntity {

    @NotBlank(message = "Event ID is required")
    @Size(max = 100, message = "Event ID must not exceed 100 characters")
    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    private String eventId;

    @Size(max = 100, message = "Service ID must not exceed 100 characters")
    @Column(name = "service_id", length = 100)
    private String serviceId;

    @Size(max = 100, message = "Instance ID must not exceed 100 characters")
    @Column(name = "instance_id", length = 100)
    private String instanceId;

    @NotNull(message = "Event type is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    @NotBlank(message = "Event category is required")
    @Size(max = 100, message = "Event category must not exceed 100 characters")
    @Column(name = "event_category", nullable = false, length = 100)
    private String eventCategory;

    @NotNull(message = "Event level is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "event_level", nullable = false, length = 20)
    private EventLevel eventLevel;

    @NotBlank(message = "Event message is required")
    @Column(name = "event_message", nullable = false, columnDefinition = "TEXT")
    private String eventMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_data", columnDefinition = "jsonb")
    private Map<String, Object> eventData;

    @NotBlank(message = "Event source is required")
    @Size(max = 100, message = "Event source must not exceed 100 characters")
    @Column(name = "source", nullable = false, length = 100)
    private String source;

    @Size(max = 100, message = "Correlation ID must not exceed 100 characters")
    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @NotNull(message = "Event timestamp is required")
    @Column(name = "event_timestamp", nullable = false)
    @Builder.Default
    private Instant eventTimestamp = Instant.now();

    @Column(name = "processed")
    @Builder.Default
    private Boolean processed = false;

    @Column(name = "processed_at")
    private Instant processedAt;

    // Business Methods

    /**
     * Mark event as processed
     */
    public void markAsProcessed() {
        this.processed = true;
        this.processedAt = Instant.now();
    }

    /**
     * Check if event is critical
     *
     * @return true if critical
     */
    public boolean isCritical() {
        return eventLevel == EventLevel.CRITICAL
            || (eventType != null && eventType.isCritical());
    }

    /**
     * Check if event requires alert
     *
     * @return true if alert should be sent
     */
    public boolean requiresAlert() {
        return eventLevel != null && eventLevel.requiresAlert();
    }

    /**
     * Add event data entry
     *
     * @param key data key
     * @param value data value
     */
    public void addEventData(String key, Object value) {
        if (eventData == null) {
            eventData = new java.util.HashMap<>();
        }
        eventData.put(key, value);
    }

    @PrePersist
    protected void onEventCreate() {
        if (eventTimestamp == null) {
            eventTimestamp = Instant.now();
        }
    }
}
