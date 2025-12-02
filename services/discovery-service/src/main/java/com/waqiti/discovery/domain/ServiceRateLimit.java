package com.waqiti.discovery.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Service Rate Limit Entity
 * Defines rate limiting rules for service endpoints
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Entity
@Table(name = "service_rate_limit",
    indexes = {
        @Index(name = "idx_service_rate_limit_service", columnList = "service_id"),
        @Index(name = "idx_service_rate_limit_endpoint", columnList = "endpoint_id"),
        @Index(name = "idx_service_rate_limit_active", columnList = "is_active")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_rate_limit_id", columnNames = "rate_limit_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(callSuper = true)
public class ServiceRateLimit extends BaseEntity {

    @NotBlank(message = "Rate limit ID is required")
    @Size(max = 100)
    @Column(name = "rate_limit_id", nullable = false, unique = true, length = 100)
    private String rateLimitId;

    @NotBlank(message = "Service ID is required")
    @Size(max = 100)
    @Column(name = "service_id", nullable = false, length = 100)
    private String serviceId;

    @Size(max = 100)
    @Column(name = "endpoint_id", length = 100)
    private String endpointId;

    @NotBlank(message = "Rate limit name is required")
    @Size(max = 255)
    @Column(name = "rate_limit_name", nullable = false, length = 255)
    private String rateLimitName;

    @NotBlank(message = "Limit type is required")
    @Size(max = 50)
    @Column(name = "limit_type", nullable = false, length = 50)
    private String limitType;

    @Min(value = 1)
    @Column(name = "requests_per_minute")
    private Integer requestsPerMinute;

    @Min(value = 1)
    @Column(name = "requests_per_hour")
    private Integer requestsPerHour;

    @Min(value = 1)
    @Column(name = "requests_per_day")
    private Integer requestsPerDay;

    @Column(name = "burst_capacity")
    private Integer burstCapacity;

    @Column(name = "window_size_seconds")
    @Builder.Default
    private Integer windowSizeSeconds = 60;

    @NotBlank(message = "Key extraction strategy is required")
    @Size(max = 100)
    @Column(name = "key_extraction_strategy", nullable = false, length = 100)
    private String keyExtractionStrategy;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Size(max = 50)
    @Column(name = "enforcement_action", length = 50)
    @Builder.Default
    private String enforcementAction = "REJECT";

    @Column(name = "bypass_roles", columnDefinition = "text[]")
    private String[] bypassRoles;

    @Column(name = "whitelist_ips", columnDefinition = "text[]")
    private String[] whitelistIps;

    @Column(name = "blacklist_ips", columnDefinition = "text[]")
    private String[] blacklistIps;

    @NotBlank(message = "Created by is required")
    @Size(max = 100)
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    // Business Methods

    public boolean isRateLimited(long currentRequestCount, long windowDurationSeconds) {
        if (!isActive) {
            return false;
        }

        if (windowDurationSeconds <= 60 && requestsPerMinute != null) {
            return currentRequestCount >= requestsPerMinute;
        } else if (windowDurationSeconds <= 3600 && requestsPerHour != null) {
            return currentRequestCount >= requestsPerHour;
        } else if (requestsPerDay != null) {
            return currentRequestCount >= requestsPerDay;
        }

        return false;
    }

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
