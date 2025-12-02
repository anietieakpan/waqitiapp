package com.waqiti.saga.domain;

import com.waqiti.common.saga.SagaStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class Saga {
    
    private String sagaId;
    private String sagaType;
    private String status;
    private String currentStep;
    private Integer stepIndex;
    private String correlationId;
    private Map<String, Object> sagaData;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant expiresAt;
    private String errorMessage;
    private Integer retryCount;
    
    public Saga(String sagaType, String correlationId) {
        this.sagaId = UUID.randomUUID().toString();
        this.sagaType = sagaType;
        this.correlationId = correlationId;
        this.status = SagaStatus.INITIATED.name();
        this.stepIndex = 0;
        this.retryCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    public abstract void start();
    public abstract void handleEvent(Object event);
    public abstract void compensate();
    public abstract boolean canProceed();
    public abstract String getNextStep();
    
    public void markAsCompleted() {
        this.status = SagaStatus.COMPLETED.name();
        this.updatedAt = Instant.now();
    }
    
    public void markAsFailed(String errorMessage) {
        this.status = SagaStatus.FAILED.name();
        this.errorMessage = errorMessage;
        this.updatedAt = Instant.now();
    }
    
    public void markAsCompensating() {
        this.status = SagaStatus.COMPENSATING.name();
        this.updatedAt = Instant.now();
    }
    
    public void markAsCompensated() {
        this.status = SagaStatus.COMPENSATED.name();
        this.updatedAt = Instant.now();
    }
    
    public void incrementRetryCount() {
        this.retryCount++;
        this.updatedAt = Instant.now();
    }
    
    public void moveToNextStep() {
        this.stepIndex++;
        this.updatedAt = Instant.now();
    }
    
    public void setCurrentStep(String step) {
        this.currentStep = step;
        this.updatedAt = Instant.now();
    }
    
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
    
    public boolean canRetry() {
        return retryCount < getMaxRetries();
    }
    
    protected int getMaxRetries() {
        return 3; // Default max retries
    }
    
}