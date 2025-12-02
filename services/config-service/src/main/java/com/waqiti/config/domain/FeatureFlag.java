package com.waqiti.config.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Feature flag entity for feature toggle management
 */
@Entity
@Table(name = "feature_flags", indexes = {
    @Index(name = "idx_feature_name", columnList = "name"),
    @Index(name = "idx_feature_enabled", columnList = "enabled"),
    @Index(name = "idx_feature_environment", columnList = "environment")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    @Column(length = 50)
    private String environment;

    @Column(columnDefinition = "TEXT")
    private String rules;

    @Column(columnDefinition = "TEXT")
    private String targetUsers;

    @Column(columnDefinition = "TEXT")
    private String targetGroups;

    @Builder.Default
    private Integer rolloutPercentage = 0;

    @Column
    private Instant startDate;

    @Column
    private Instant endDate;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant lastModified;

    @Column(length = 100)
    private String createdBy;

    @Column(length = 100)
    private String modifiedBy;

    @Version
    private Long version;
}
