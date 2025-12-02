package com.waqiti.discovery.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.Instant;

/**
 * Service Dependency Entity
 * Tracks dependencies between services
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Entity
@Table(name = "service_dependency",
    indexes = {
        @Index(name = "idx_service_dependency_consumer", columnList = "consumer_service_id"),
        @Index(name = "idx_service_dependency_provider", columnList = "provider_service_id"),
        @Index(name = "idx_service_dependency_circuit_state", columnList = "circuit_breaker_state")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_dependency_id", columnNames = "dependency_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(callSuper = true)
public class ServiceDependency extends BaseEntity {

    @NotBlank(message = "Dependency ID is required")
    @Size(max = 100, message = "Dependency ID must not exceed 100 characters")
    @Column(name = "dependency_id", nullable = false, unique = true, length = 100)
    private String dependencyId;

    @NotBlank(message = "Consumer service ID is required")
    @Size(max = 100, message = "Consumer service ID must not exceed 100 characters")
    @Column(name = "consumer_service_id", nullable = false, length = 100)
    private String consumerServiceId;

    @NotBlank(message = "Provider service ID is required")
    @Size(max = 100, message = "Provider service ID must not exceed 100 characters")
    @Column(name = "provider_service_id", nullable = false, length = 100)
    private String providerServiceId;

    @NotNull(message = "Dependency type is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "dependency_type", nullable = false, length = 50)
    private DependencyType dependencyType;

    @NotBlank(message = "Dependency level is required")
    @Size(max = 20, message = "Dependency level must not exceed 20 characters")
    @Column(name = "dependency_level", nullable = false, length = 20)
    private String dependencyLevel;

    @Column(name = "is_critical")
    @Builder.Default
    private Boolean isCritical = false;

    @Column(name = "circuit_breaker_enabled")
    @Builder.Default
    private Boolean circuitBreakerEnabled = true;

    @Min(value = 1, message = "Timeout must be at least 1 second")
    @Column(name = "timeout_seconds")
    @Builder.Default
    private Integer timeoutSeconds = 30;

    @Min(value = 0, message = "Retry attempts must not be negative")
    @Column(name = "retry_attempts")
    @Builder.Default
    private Integer retryAttempts = 3;

    @Size(max = 100, message = "Fallback strategy must not exceed 100 characters")
    @Column(name = "fallback_strategy", length = 100)
    private String fallbackStrategy;

    @Column(name = "last_call_timestamp")
    private Instant lastCallTimestamp;

    @Min(value = 0, message = "Call count must not be negative")
    @Column(name = "call_count")
    @Builder.Default
    private Integer callCount = 0;

    @Min(value = 0, message = "Success count must not be negative")
    @Column(name = "success_count")
    @Builder.Default
    private Integer successCount = 0;

    @Min(value = 0, message = "Failure count must not be negative")
    @Column(name = "failure_count")
    @Builder.Default
    private Integer failureCount = 0;

    @Column(name = "avg_response_time_ms", precision = 10, scale = 2)
    private Double avgResponseTimeMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "circuit_breaker_state", length = 20)
    @Builder.Default
    private CircuitBreakerState circuitBreakerState = CircuitBreakerState.CLOSED;

    @Column(name = "last_circuit_breaker_change")
    private Instant lastCircuitBreakerChange;

    @Min(value = 0, message = "Health impact score must not be negative")
    @Max(value = 100, message = "Health impact score must not exceed 100")
    @Column(name = "health_impact_score")
    @Builder.Default
    private Integer healthImpactScore = 0;

    // Business Methods

    /**
     * Record successful dependency call
     *
     * @param responseTime response time in milliseconds
     */
    public void recordSuccess(long responseTime) {
        this.callCount++;
        this.successCount++;
        this.lastCallTimestamp = Instant.now();
        updateAverageResponseTime(responseTime);
    }

    /**
     * Record failed dependency call
     */
    public void recordFailure() {
        this.callCount++;
        this.failureCount++;
        this.lastCallTimestamp = Instant.now();
        updateCircuitBreakerState();
    }

    /**
     * Calculate failure rate
     *
     * @return failure rate percentage (0-100)
     */
    public double getFailureRate() {
        if (callCount == 0) {
            return 0.0;
        }
        return (double) failureCount / callCount * 100.0;
    }

    /**
     * Calculate success rate
     *
     * @return success rate percentage (0-100)
     */
    public double getSuccessRate() {
        return 100.0 - getFailureRate();
    }

    /**
     * Check if dependency is healthy
     *
     * @return true if healthy
     */
    public boolean isHealthy() {
        return circuitBreakerState == CircuitBreakerState.CLOSED
            && getSuccessRate() >= 95.0;
    }

    /**
     * Update average response time
     *
     * @param newResponseTime new response time
     */
    private void updateAverageResponseTime(long newResponseTime) {
        if (avgResponseTimeMs == null) {
            avgResponseTimeMs = (double) newResponseTime;
        } else {
            // Exponential moving average
            avgResponseTimeMs = (avgResponseTimeMs * 0.9) + (newResponseTime * 0.1);
        }
    }

    /**
     * Update circuit breaker state based on failure rate
     */
    private void updateCircuitBreakerState() {
        if (!circuitBreakerEnabled) {
            return;
        }

        if (getFailureRate() > 50.0 && callCount > 10) {
            if (circuitBreakerState != CircuitBreakerState.OPEN) {
                circuitBreakerState = CircuitBreakerState.OPEN;
                lastCircuitBreakerChange = Instant.now();
            }
        } else if (getFailureRate() < 10.0 && callCount > 10) {
            if (circuitBreakerState != CircuitBreakerState.CLOSED) {
                circuitBreakerState = CircuitBreakerState.CLOSED;
                lastCircuitBreakerChange = Instant.now();
            }
        }
    }
}
