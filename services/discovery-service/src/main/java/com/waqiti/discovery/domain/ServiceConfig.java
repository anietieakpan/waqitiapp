package com.waqiti.discovery.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Service Configuration Entity
 * Stores configuration for discovered services
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Entity
@Table(name = "service_configuration",
    indexes = {
        @Index(name = "idx_service_configuration_service", columnList = "service_id"),
        @Index(name = "idx_service_configuration_environment", columnList = "environment"),
        @Index(name = "idx_service_configuration_sensitive", columnList = "is_sensitive")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_config_id", columnNames = "config_id"),
        @UniqueConstraint(name = "unique_service_config", columnNames = {"service_id", "config_key", "environment"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(callSuper = true)
public class ServiceConfig extends BaseEntity {

    @NotBlank(message = "Config ID is required")
    @Size(max = 100, message = "Config ID must not exceed 100 characters")
    @Column(name = "config_id", nullable = false, unique = true, length = 100)
    private String configId;

    @NotBlank(message = "Service ID is required")
    @Size(max = 100, message = "Service ID must not exceed 100 characters")
    @Column(name = "service_id", nullable = false, length = 100)
    private String serviceId;

    @NotBlank(message = "Config key is required")
    @Size(max = 255, message = "Config key must not exceed 255 characters")
    @Column(name = "config_key", nullable = false, length = 255)
    private String configKey;

    @NotNull(message = "Config value is required")
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_value", nullable = false, columnDefinition = "jsonb")
    private Object configValue;

    @NotBlank(message = "Config type is required")
    @Size(max = 50, message = "Config type must not exceed 50 characters")
    @Column(name = "config_type", nullable = false, length = 50)
    private String configType;

    @Column(name = "is_sensitive")
    @Builder.Default
    private Boolean isSensitive = false;

    @NotBlank(message = "Environment is required")
    @Size(max = 20, message = "Environment must not exceed 20 characters")
    @Column(name = "environment", nullable = false, length = 20)
    @Builder.Default
    private String environment = "PRODUCTION";

    @Column(name = "version")
    @Builder.Default
    private Integer version = 1;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @NotNull(message = "Effective date is required")
    @Column(name = "effective_date", nullable = false)
    @Builder.Default
    private Instant effectiveDate = Instant.now();

    @Column(name = "expiry_date")
    private Instant expiryDate;

    @NotBlank(message = "Created by is required")
    @Size(max = 100, message = "Created by must not exceed 100 characters")
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    // Additional config metadata
    @Column(name = "load_balancer_strategy", length = 50)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private LoadBalancerStrategy loadBalancerStrategy = LoadBalancerStrategy.ROUND_ROBIN;

    @Column(name = "health_check_enabled")
    @Builder.Default
    private Boolean healthCheckEnabled = true;

    @Column(name = "health_check_interval")
    @Builder.Default
    private Integer healthCheckInterval = 30;

    @Column(name = "timeout")
    @Builder.Default
    private Integer timeout = 5000;

    @Column(name = "retry_attempts")
    @Builder.Default
    private Integer retryAttempts = 3;

    @Column(name = "circuit_breaker_enabled")
    @Builder.Default
    private Boolean circuitBreakerEnabled = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    // Business Methods

    /**
     * Check if config is expired
     *
     * @return true if expired
     */
    public boolean isExpired() {
        return expiryDate != null && Instant.now().isAfter(expiryDate);
    }

    /**
     * Check if config is effective
     *
     * @return true if effective and active
     */
    public boolean isEffective() {
        Instant now = Instant.now();
        return isActive
            && !isExpired()
            && effectiveDate != null
            && now.isAfter(effectiveDate);
    }

    /**
     * Activate configuration
     */
    public void activate() {
        this.isActive = true;
    }

    /**
     * Deactivate configuration
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * Increment version
     */
    public void incrementVersion() {
        if (this.version == null) {
            this.version = 1;
        } else {
            this.version++;
        }
    }

    @PrePersist
    protected void onConfigCreate() {
        if (effectiveDate == null) {
            effectiveDate = Instant.now();
        }
    }
}
