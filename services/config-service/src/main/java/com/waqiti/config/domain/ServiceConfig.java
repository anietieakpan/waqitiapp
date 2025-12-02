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
 * Service-specific configuration entity
 */
@Entity
@Table(name = "service_configs", indexes = {
    @Index(name = "idx_service_name", columnList = "serviceName"),
    @Index(name = "idx_service_environment", columnList = "environment")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String serviceName;

    @Column(nullable = false, length = 50)
    private String environment;

    @Column(columnDefinition = "TEXT")
    private String configData;

    @Column(columnDefinition = "TEXT")
    private String secretsData;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant lastModified;

    @Column(length = 100)
    private String modifiedBy;

    @Version
    private Long version;
}
