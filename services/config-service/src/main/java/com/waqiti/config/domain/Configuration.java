package com.waqiti.config.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Configuration entity for centralized configuration management
 */
@Entity
@Table(name = "configurations", indexes = {
    @Index(name = "idx_config_key", columnList = "key"),
    @Index(name = "idx_config_service", columnList = "service"),
    @Index(name = "idx_config_environment", columnList = "environment"),
    @Index(name = "idx_config_active", columnList = "active"),
    @Index(name = "idx_config_last_modified", columnList = "lastModified")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Configuration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 500)
    private String key;

    @Column(columnDefinition = "TEXT")
    private String value;

    @Column(length = 100)
    private String service;

    @Column(length = 50)
    private String environment;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean sensitive = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean encrypted = false;

    @Column(length = 50)
    private String dataType;

    @Column(columnDefinition = "TEXT")
    private String defaultValue;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant lastModified;

    @Column
    private Instant deletedAt;

    @Column(length = 100)
    private String createdBy;

    @Column(length = 100)
    private String modifiedBy;

    @Version
    private Long version;
}
