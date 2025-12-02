package com.waqiti.discovery.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Service Circuit Breaker Entity
 * Tracks circuit breaker state for service-to-service communication
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Entity
@Table(name = "service_circuit_breaker",
    indexes = {
        @Index(name = "idx_service_circuit_breaker_service", columnList = "service_id"),
        @Index(name = "idx_service_circuit_breaker_target", columnList = "target_service_id"),
        @Index(name = "idx_service_circuit_breaker_state", columnList = "state")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_circuit_breaker_id", columnNames = "circuit_breaker_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(callSuper = true)
public class ServiceCircuitBreaker extends BaseEntity {

    @NotBlank(message = "Circuit breaker ID is required")
    @Size(max = 100)
    @Column(name = "circuit_breaker_id", nullable = false, unique = true, length = 100)
    private String circuitBreakerId;

    @NotBlank(message = "Service ID is required")
    @Size(max = 100)
    @Column(name = "service_id", nullable = false, length = 100)
    private String serviceId;

    @NotBlank(message = "Target service ID is required")
    @Size(max = 100)
    @Column(name = "target_service_id", nullable = false, length = 100)
    private String targetServiceId;

    @NotBlank(message = "Circuit breaker name is required")
    @Size(max = 255)
    @Column(name = "circuit_breaker_name", nullable = false, length = 255)
    private String circuitBreakerName;

    @NotNull(message = "State is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    @Builder.Default
    private CircuitBreakerState state = CircuitBreakerState.CLOSED;

    @Min(value = 1)
    @Column(name = "failure_threshold")
    @Builder.Default
    private Integer failureThreshold = 5;

    @Min(value = 1)
    @Column(name = "success_threshold")
    @Builder.Default
    private Integer successThreshold = 3;

    @Column(name = "timeout_seconds")
    @Builder.Default
    private Integer timeoutSeconds = 60;

    @Column(name = "slow_call_threshold_seconds")
    @Builder.Default
    private Integer slowCallThresholdSeconds = 30;

    @Column(name = "slow_call_rate_threshold", precision = 5, scale = 4)
    @Builder.Default
    private Double slowCallRateThreshold = 0.5;

    @Column(name = "minimum_throughput")
    @Builder.Default
    private Integer minimumThroughput = 10;

    @Column(name = "sliding_window_size")
    @Builder.Default
    private Integer slidingWindowSize = 100;

    @Size(max = 20)
    @Column(name = "sliding_window_type", length = 20)
    @Builder.Default
    private String slidingWindowType = "COUNT_BASED";

    @Column(name = "failure_count")
    @Builder.Default
    private Integer failureCount = 0;

    @Column(name = "success_count")
    @Builder.Default
    private Integer successCount = 0;

    @Column(name = "slow_call_count")
    @Builder.Default
    private Integer slowCallCount = 0;

    @Column(name = "last_failure_time")
    private Instant lastFailureTime;

    @Column(name = "last_success_time")
    private Instant lastSuccessTime;

    @Column(name = "next_attempt_time")
    private Instant nextAttemptTime;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "state_transition_history", columnDefinition = "jsonb")
    private Object stateTransitionHistory;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration", columnDefinition = "jsonb")
    private Map<String, Object> configuration;

    // Business Methods

    public void recordSuccess() {
        this.successCount++;
        this.lastSuccessTime = Instant.now();
        evaluateState();
    }

    public void recordFailure() {
        this.failureCount++;
        this.lastFailureTime = Instant.now();
        evaluateState();
    }

    public void recordSlowCall() {
        this.slowCallCount++;
        evaluateState();
    }

    private void evaluateState() {
        int totalCalls = failureCount + successCount;
        if (totalCalls < minimumThroughput) {
            return;
        }

        double failureRate = (double) failureCount / totalCalls;
        double slowCallRate = (double) slowCallCount / totalCalls;

        if (state == CircuitBreakerState.CLOSED) {
            if (failureRate >= (double) failureThreshold / 100.0 || slowCallRate >= slowCallRateThreshold) {
                transitionTo(CircuitBreakerState.OPEN);
            }
        } else if (state == CircuitBreakerState.HALF_OPEN) {
            if (successCount >= successThreshold) {
                transitionTo(CircuitBreakerState.CLOSED);
            } else if (failureCount > 0) {
                transitionTo(CircuitBreakerState.OPEN);
            }
        }
    }

    private void transitionTo(CircuitBreakerState newState) {
        this.state = newState;
        if (newState == CircuitBreakerState.OPEN) {
            this.nextAttemptTime = Instant.now().plusSeconds(timeoutSeconds);
        } else if (newState == CircuitBreakerState.CLOSED) {
            resetCounters();
        }
    }

    private void resetCounters() {
        this.failureCount = 0;
        this.successCount = 0;
        this.slowCallCount = 0;
    }

    public boolean allowsRequest() {
        if (state == CircuitBreakerState.CLOSED) {
            return true;
        } else if (state == CircuitBreakerState.HALF_OPEN) {
            return successCount < successThreshold;
        } else if (state == CircuitBreakerState.OPEN) {
            if (nextAttemptTime != null && Instant.now().isAfter(nextAttemptTime)) {
                transitionTo(CircuitBreakerState.HALF_OPEN);
                return true;
            }
        }
        return false;
    }
}
