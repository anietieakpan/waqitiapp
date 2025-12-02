package com.waqiti.common.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Feign client for Saga Orchestration Service.
 * Manages distributed transactions using the Saga pattern across microservices.
 */
@FeignClient(
    name = "saga-orchestration-service",
    path = "/api/saga",
    fallbackFactory = SagaOrchestrationServiceClientFallbackFactory.class
)
public interface SagaOrchestrationServiceClient {

    /**
     * Start a new saga transaction.
     */
    @PostMapping("/start")
    ResponseEntity<SagaTransactionResponse> startSaga(@RequestBody SagaTransactionRequest request);

    /**
     * Get saga status by ID.
     */
    @GetMapping("/{sagaId}/status")
    ResponseEntity<SagaStatusResponse> getSagaStatus(@PathVariable String sagaId);

    /**
     * Execute a saga step.
     */
    @PostMapping("/{sagaId}/step")
    ResponseEntity<SagaStepResponse> executeSagaStep(
        @PathVariable String sagaId,
        @RequestBody SagaStepRequest request
    );

    /**
     * Compensate (rollback) a saga transaction.
     */
    @PostMapping("/{sagaId}/compensate")
    ResponseEntity<CompensationResponse> compensateSaga(
        @PathVariable String sagaId,
        @RequestParam(required = false) String reason
    );

    /**
     * Complete a saga transaction.
     */
    @PostMapping("/{sagaId}/complete")
    ResponseEntity<SagaCompletionResponse> completeSaga(@PathVariable String sagaId);

    /**
     * Get saga execution history.
     */
    @GetMapping("/{sagaId}/history")
    ResponseEntity<List<SagaHistoryEntry>> getSagaHistory(@PathVariable String sagaId);

    /**
     * Retry a failed saga step.
     */
    @PostMapping("/{sagaId}/step/{stepId}/retry")
    ResponseEntity<SagaStepResponse> retrySagaStep(
        @PathVariable String sagaId,
        @PathVariable String stepId
    );

    /**
     * Get all active sagas.
     */
    @GetMapping("/active")
    ResponseEntity<List<SagaTransactionResponse>> getActiveSagas(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    );

    /**
     * Cancel a saga transaction.
     */
    @PostMapping("/{sagaId}/cancel")
    ResponseEntity<Void> cancelSaga(
        @PathVariable String sagaId,
        @RequestParam String reason
    );

    /**
     * Get saga metrics.
     */
    @GetMapping("/metrics")
    ResponseEntity<SagaMetrics> getSagaMetrics();

    /**
     * Start a payment saga transaction.
     * Specialized endpoint for payment processing with payment-specific context.
     *
     * @param request Payment saga request with payment details
     * @return Payment saga execution result
     */
    @PostMapping("/payment/start")
    ResponseEntity<PaymentSagaExecutionResult> startPaymentSaga(@RequestBody PaymentSagaRequest request);

    // Data Transfer Objects

    record SagaTransactionRequest(
        String transactionId,
        String transactionType,
        Map<String, Object> payload,
        List<SagaStep> steps,
        Integer timeoutSeconds,
        Boolean autoCompensate,
        Map<String, String> metadata
    ) {}

    record SagaTransactionResponse(
        String sagaId,
        String transactionId,
        String status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        List<SagaStep> completedSteps,
        List<SagaStep> pendingSteps,
        Map<String, Object> result
    ) {}

    record SagaStep(
        String stepId,
        String serviceName,
        String operation,
        Map<String, Object> parameters,
        Integer order,
        Boolean isCompensatable,
        String compensationOperation,
        Integer retryCount,
        Integer timeoutSeconds
    ) {}

    record SagaStepRequest(
        String stepId,
        String serviceName,
        String operation,
        Map<String, Object> parameters,
        Boolean async
    ) {}

    record SagaStepResponse(
        String stepId,
        String status,
        Map<String, Object> result,
        String errorMessage,
        LocalDateTime executionTime,
        Long durationMs
    ) {}

    record SagaStatusResponse(
        String sagaId,
        String status,
        Integer completedSteps,
        Integer totalSteps,
        String currentStep,
        LocalDateTime lastUpdateTime,
        Map<String, Object> context
    ) {}

    record CompensationResponse(
        String sagaId,
        String status,
        List<String> compensatedSteps,
        List<String> failedCompensations,
        LocalDateTime compensationTime
    ) {}

    record SagaCompletionResponse(
        String sagaId,
        String status,
        Map<String, Object> finalResult,
        LocalDateTime completionTime,
        Long totalDurationMs,
        List<String> executedSteps
    ) {}

    record SagaHistoryEntry(
        String entryId,
        String sagaId,
        String stepId,
        String action,
        String status,
        LocalDateTime timestamp,
        Map<String, Object> details
    ) {}

    record SagaMetrics(
        Long totalSagas,
        Long activeSagas,
        Long completedSagas,
        Long failedSagas,
        Long compensatedSagas,
        Double averageDurationMs,
        Double successRate,
        Map<String, Long> sagasByType
    ) {}

    // Payment-specific DTOs

    record PaymentSagaRequest(
        String sagaId,
        String paymentType,
        String paymentId,
        String payerId,
        String payeeId,
        java.math.BigDecimal amount,
        String currency,
        Map<String, Object> paymentDetails,
        String provider,
        String strategy,
        Map<String, String> metadata
    ) {}

    record PaymentSagaExecutionResult(
        String sagaId,
        String paymentId,
        String status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer completedSteps,
        Integer totalSteps,
        Map<String, Object> stepResults,
        String errorMessage,
        Boolean compensationTriggered,
        Long durationMs
    ) {}
}