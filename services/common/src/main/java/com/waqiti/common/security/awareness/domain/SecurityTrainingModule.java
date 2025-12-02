package com.waqiti.common.security.awareness.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Security Training Module entity
 *
 * @author Waqiti Platform Team
 */
@Entity
@Table(name = "security_training_modules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityTrainingModule {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String category;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(nullable = false)
    private Boolean mandatory;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Convenience getters
    public Integer getEstimatedDurationMinutes() {
        return durationMinutes;
    }

    public String getContentSections() {
        return description; // Placeholder - actual implementation may differ
    }

    public Integer getPassingScorePercentage() {
        return 70; // Default passing score
    }
}