package com.waqiti.saga.controller;

import com.waqiti.common.api.ApiResponse;
import com.waqiti.saga.dto.*;
import com.waqiti.saga.service.SagaOrchestrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/saga")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Saga Orchestration", description = "Distributed transaction management using saga pattern")
public class SagaOrchestrationController {

    private final SagaOrchestrationService sagaOrchestrationService;

    @PostMapping("/start")
    @Operation(summary = "Start a new saga transaction", 
               description = "Initiates a new distributed transaction saga")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SagaExecutionResponse>> startSaga(
            @Valid @RequestBody StartSagaRequest request) {
        log.info("Starting saga of type: {} with correlation ID: {}", 
                request.getSagaType(), request.getCorrelationId());
        
        SagaExecutionResponse response = sagaOrchestrationService.startSaga(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/{sagaId}")
    @Operation(summary = "Get saga execution details")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<ApiResponse<SagaExecutionResponse>> getSagaExecution(
            @Parameter(description = "Saga execution ID") @PathVariable UUID sagaId) {
        log.info("Retrieving saga execution: {}", sagaId);
        
        SagaExecutionResponse response = sagaOrchestrationService.getSagaExecution(sagaId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "Get all saga executions with pagination and filtering")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<SagaExecutionResponse>>> getSagaExecutions(
            @Parameter(description = "Saga type filter") @RequestParam(required = false) String sagaType,
            @Parameter(description = "Status filter") @RequestParam(required = false) String status,
            @Parameter(description = "Correlation ID filter") @RequestParam(required = false) String correlationId,
            Pageable pageable) {
        log.info("Retrieving saga executions with filters - type: {}, status: {}, correlationId: {}", 
                sagaType, status, correlationId);
        
        Page<SagaExecutionResponse> response = sagaOrchestrationService.getSagaExecutions(
                sagaType, status, correlationId, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{sagaId}/compensate")
    @Operation(summary = "Compensate a saga transaction",
               description = "Initiates compensation (rollback) for a failed saga")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SagaExecutionResponse>> compensateSaga(
            @Parameter(description = "Saga execution ID") @PathVariable UUID sagaId,
            @Valid @RequestBody CompensateSagaRequest request) {
        log.info("Compensating saga: {} with reason: {}", sagaId, request.getReason());
        
        SagaExecutionResponse response = sagaOrchestrationService.compensateSaga(sagaId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{sagaId}/retry")
    @Operation(summary = "Retry a failed saga step")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SagaExecutionResponse>> retrySaga(
            @Parameter(description = "Saga execution ID") @PathVariable UUID sagaId,
            @Valid @RequestBody RetrySagaRequest request) {
        log.info("Retrying saga: {} from step: {}", sagaId, request.getRetryFromStep());
        
        SagaExecutionResponse response = sagaOrchestrationService.retrySaga(sagaId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{sagaId}/complete-step")
    @Operation(summary = "Complete a saga step",
               description = "Called by participant services to report step completion")
    @PreAuthorize("hasRole('SYSTEM')")
    public ResponseEntity<ApiResponse<SagaExecutionResponse>> completeStep(
            @Parameter(description = "Saga execution ID") @PathVariable UUID sagaId,
            @Valid @RequestBody CompleteStepRequest request) {
        log.info("Completing step: {} for saga: {}", request.getStepName(), sagaId);
        
        SagaExecutionResponse response = sagaOrchestrationService.completeStep(sagaId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{sagaId}/fail-step")
    @Operation(summary = "Report a saga step failure",
               description = "Called by participant services to report step failure")
    @PreAuthorize("hasRole('SYSTEM')")
    public ResponseEntity<ApiResponse<SagaExecutionResponse>> failStep(
            @Parameter(description = "Saga execution ID") @PathVariable UUID sagaId,
            @Valid @RequestBody FailStepRequest request) {
        log.info("Failing step: {} for saga: {} with error: {}", 
                request.getStepName(), sagaId, request.getErrorMessage());
        
        SagaExecutionResponse response = sagaOrchestrationService.failStep(sagaId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{sagaId}/steps")
    @Operation(summary = "Get all steps for a saga execution")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<SagaStepResponse>>> getSagaSteps(
            @Parameter(description = "Saga execution ID") @PathVariable UUID sagaId) {
        log.info("Retrieving steps for saga: {}", sagaId);
        
        List<SagaStepResponse> response = sagaOrchestrationService.getSagaSteps(sagaId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/statistics")
    @Operation(summary = "Get saga execution statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SagaStatisticsResponse>> getSagaStatistics(
            @Parameter(description = "Number of days for statistics") 
            @RequestParam(defaultValue = "30") Integer days) {
        log.info("Retrieving saga statistics for {} days", days);
        
        SagaStatisticsResponse response = sagaOrchestrationService.getSagaStatistics(days);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/failed")
    @Operation(summary = "Get failed sagas requiring attention")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<SagaExecutionResponse>>> getFailedSagas(
            @Parameter(description = "Hours to look back") @RequestParam(defaultValue = "24") Integer hours,
            Pageable pageable) {
        log.info("Retrieving failed sagas from last {} hours", hours);
        
        Page<SagaExecutionResponse> response = sagaOrchestrationService.getFailedSagas(hours, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/stuck")
    @Operation(summary = "Get stuck sagas that may require manual intervention")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<SagaExecutionResponse>>> getStuckSagas(
            @Parameter(description = "Hours to consider saga as stuck") 
            @RequestParam(defaultValue = "2") Integer hours,
            Pageable pageable) {
        log.info("Retrieving stuck sagas older than {} hours", hours);
        
        Page<SagaExecutionResponse> response = sagaOrchestrationService.getStuckSagas(hours, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{sagaId}")
    @Operation(summary = "Cancel a running saga",
               description = "Cancels a saga and initiates compensation if needed")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> cancelSaga(
            @Parameter(description = "Saga execution ID") @PathVariable UUID sagaId,
            @Parameter(description = "Cancellation reason") @RequestParam String reason) {
        log.info("Cancelling saga: {} with reason: {}", sagaId, reason);
        
        sagaOrchestrationService.cancelSaga(sagaId, reason);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/types")
    @Operation(summary = "Get available saga types")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<ApiResponse<List<SagaTypeResponse>>> getSagaTypes() {
        log.info("Retrieving available saga types");
        
        List<SagaTypeResponse> response = sagaOrchestrationService.getSagaTypes();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate saga definition",
               description = "Validates a saga definition before execution")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SagaValidationResponse>> validateSaga(
            @Valid @RequestBody ValidateSagaRequest request) {
        log.info("Validating saga definition for type: {}", request.getSagaType());
        
        SagaValidationResponse response = sagaOrchestrationService.validateSaga(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/health")
    @Operation(summary = "Health check for saga orchestration service")
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.success("Saga Orchestration Service is healthy"));
    }
}