package com.waqiti.saga.builder;

import com.waqiti.saga.domain.SagaExecution;
import com.waqiti.common.saga.SagaStatus;
import com.waqiti.saga.domain.SagaType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Test data builder for SagaExecution entity
 *
 * Provides fluent API for creating saga execution test data.
 *
 * Usage:
 * <pre>
 * SagaExecution execution = SagaExecutionBuilder.aSagaExecution()
 *     .withSagaType(SagaType.P2P_TRANSFER)
 *     .withStatus(SagaStatus.RUNNING)
 *     .build();
 * </pre>
 */
public class SagaExecutionBuilder {

    private String sagaId;
    private SagaType sagaType;
    private SagaStatus status;
    private String correlationId;
    private String initiatedBy;
    private String currentStep;
    private Integer currentStepIndex;
    private Integer totalSteps;
    private String errorMessage;
    private String errorCode;
    private String failedStep;
    private LocalDateTime timeoutAt;
    private Integer retryCount;
    private Integer maxRetries;

    private SagaExecutionBuilder() {
        // Set sensible defaults
        this.sagaId = UUID.randomUUID().toString();
        this.sagaType = SagaType.P2P_TRANSFER;
        this.status = SagaStatus.INITIATED;
        this.correlationId = UUID.randomUUID().toString();
        this.initiatedBy = "user-test";
        this.currentStepIndex = 0;
        this.totalSteps = 6;
        this.retryCount = 0;
        this.maxRetries = 3;
        this.timeoutAt = LocalDateTime.now().plusMinutes(30);
    }

    public static SagaExecutionBuilder aSagaExecution() {
        return new SagaExecutionBuilder();
    }

    public SagaExecutionBuilder withSagaId(String sagaId) {
        this.sagaId = sagaId;
        return this;
    }

    public SagaExecutionBuilder withSagaType(SagaType sagaType) {
        this.sagaType = sagaType;
        return this;
    }

    public SagaExecutionBuilder withStatus(SagaStatus status) {
        this.status = status;
        return this;
    }

    public SagaExecutionBuilder withCorrelationId(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }

    public SagaExecutionBuilder withInitiatedBy(String initiatedBy) {
        this.initiatedBy = initiatedBy;
        return this;
    }

    public SagaExecutionBuilder withCurrentStep(String currentStep) {
        this.currentStep = currentStep;
        return this;
    }

    public SagaExecutionBuilder withCurrentStepIndex(Integer currentStepIndex) {
        this.currentStepIndex = currentStepIndex;
        return this;
    }

    public SagaExecutionBuilder withTotalSteps(Integer totalSteps) {
        this.totalSteps = totalSteps;
        return this;
    }

    public SagaExecutionBuilder withErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    public SagaExecutionBuilder withErrorCode(String errorCode) {
        this.errorCode = errorCode;
        return this;
    }

    public SagaExecutionBuilder withFailedStep(String failedStep) {
        this.failedStep = failedStep;
        return this;
    }

    public SagaExecutionBuilder withTimeoutAt(LocalDateTime timeoutAt) {
        this.timeoutAt = timeoutAt;
        return this;
    }

    public SagaExecutionBuilder withRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
        return this;
    }

    public SagaExecutionBuilder withMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    // ========== PRESET SCENARIOS ==========

    /**
     * Create a running P2P transfer saga
     */
    public SagaExecutionBuilder asRunningP2PTransfer() {
        this.sagaType = SagaType.P2P_TRANSFER;
        this.status = SagaStatus.RUNNING;
        this.currentStep = "DEBIT_WALLET";
        this.currentStepIndex = 3;
        this.totalSteps = 6;
        return this;
    }

    /**
     * Create a failed saga requiring compensation
     */
    public SagaExecutionBuilder asFailedSaga() {
        this.status = SagaStatus.FAILED;
        this.currentStep = "CREDIT_WALLET";
        this.currentStepIndex = 4;
        this.errorMessage = "Destination wallet not found";
        this.errorCode = "WALLET_NOT_FOUND";
        this.failedStep = "CREDIT_WALLET";
        return this;
    }

    /**
     * Create a completed saga
     */
    public SagaExecutionBuilder asCompletedSaga() {
        this.status = SagaStatus.COMPLETED;
        this.currentStep = null;
        this.currentStepIndex = 6;
        return this;
    }

    /**
     * Create a compensated saga
     */
    public SagaExecutionBuilder asCompensatedSaga() {
        this.status = SagaStatus.COMPENSATED;
        this.currentStep = "REVERSE_DEBIT";
        this.errorMessage = "Compensation completed successfully";
        this.failedStep = "CREDIT_WALLET";
        return this;
    }

    /**
     * Create an international transfer saga
     */
    public SagaExecutionBuilder asInternationalTransfer() {
        this.sagaType = SagaType.INTERNATIONAL_TRANSFER;
        this.totalSteps = 13;
        this.timeoutAt = LocalDateTime.now().plusHours(2);
        return this;
    }

    /**
     * Create a saga that has been retried
     */
    public SagaExecutionBuilder asRetriedSaga() {
        this.retryCount = 2;
        this.status = SagaStatus.RUNNING;
        return this;
    }

    /**
     * Create a saga that has exceeded max retries
     */
    public SagaExecutionBuilder asExceededRetries() {
        this.retryCount = 3;
        this.maxRetries = 3;
        this.status = SagaStatus.FAILED;
        this.errorMessage = "Max retries exceeded";
        return this;
    }

    public SagaExecution build() {
        SagaExecution execution = new SagaExecution(sagaId, sagaType, correlationId);
        execution.setStatus(status);
        execution.setInitiatedBy(initiatedBy);
        execution.setCurrentStep(currentStep);
        execution.setCurrentStepIndex(currentStepIndex);
        execution.setTotalSteps(totalSteps);
        execution.setErrorMessage(errorMessage);
        execution.setErrorCode(errorCode);
        execution.setFailedStep(failedStep);
        execution.setTimeoutAt(timeoutAt);
        execution.setRetryCount(retryCount);
        execution.setMaxRetries(maxRetries);

        return execution;
    }
}
