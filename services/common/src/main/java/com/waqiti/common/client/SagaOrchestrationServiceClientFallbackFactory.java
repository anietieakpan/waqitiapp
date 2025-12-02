package com.waqiti.common.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Fallback factory for Saga Orchestration Service Client.
 * Provides graceful degradation when the saga service is unavailable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaOrchestrationServiceClientFallbackFactory implements FallbackFactory<SagaOrchestrationServiceClient> {

    @Override
    public SagaOrchestrationServiceClient create(Throwable cause) {
        return new SagaOrchestrationServiceClientFallback(cause);
    }

    @Slf4j
    @RequiredArgsConstructor
    private static class SagaOrchestrationServiceClientFallback implements SagaOrchestrationServiceClient {
        
        private final Throwable cause;

        @Override
        public ResponseEntity<SagaTransactionResponse> startSaga(SagaTransactionRequest request) {
            log.warn("Saga orchestration service unavailable, using fallback for startSaga", cause);
            
            SagaTransactionResponse response = new SagaTransactionResponse(
                "FALLBACK-" + request.transactionId(),
                request.transactionId(),
                "FALLBACK_INITIATED",
                LocalDateTime.now(),
                null,
                Collections.emptyList(),
                request.steps() != null ? request.steps() : Collections.emptyList(),
                Collections.singletonMap("fallback", true)
            );
            
            return ResponseEntity.ok(response);
        }

        @Override
        public ResponseEntity<SagaStatusResponse> getSagaStatus(String sagaId) {
            log.warn("Saga orchestration service unavailable, using fallback for getSagaStatus: {}", sagaId, cause);
            
            SagaStatusResponse response = new SagaStatusResponse(
                sagaId,
                "UNKNOWN",
                0,
                0,
                "FALLBACK",
                LocalDateTime.now(),
                Collections.singletonMap("fallback", true)
            );
            
            return ResponseEntity.ok(response);
        }

        @Override
        public ResponseEntity<SagaStepResponse> executeSagaStep(String sagaId, SagaStepRequest request) {
            log.warn("Saga orchestration service unavailable, using fallback for executeSagaStep: {} - {}", sagaId, request.stepId(), cause);
            
            SagaStepResponse response = new SagaStepResponse(
                request.stepId(),
                "FALLBACK",
                Collections.singletonMap("fallback", true),
                "Saga service unavailable",
                LocalDateTime.now(),
                0L
            );
            
            return ResponseEntity.ok(response);
        }

        @Override
        public ResponseEntity<CompensationResponse> compensateSaga(String sagaId, String reason) {
            log.warn("Saga orchestration service unavailable, using fallback for compensateSaga: {}", sagaId, cause);
            
            CompensationResponse response = new CompensationResponse(
                sagaId,
                "FALLBACK_COMPENSATION",
                Collections.emptyList(),
                Collections.emptyList(),
                LocalDateTime.now()
            );
            
            return ResponseEntity.ok(response);
        }

        @Override
        public ResponseEntity<SagaCompletionResponse> completeSaga(String sagaId) {
            log.warn("Saga orchestration service unavailable, using fallback for completeSaga: {}", sagaId, cause);
            
            SagaCompletionResponse response = new SagaCompletionResponse(
                sagaId,
                "FALLBACK_COMPLETED",
                Collections.singletonMap("fallback", true),
                LocalDateTime.now(),
                0L,
                Collections.emptyList()
            );
            
            return ResponseEntity.ok(response);
        }

        @Override
        public ResponseEntity<List<SagaHistoryEntry>> getSagaHistory(String sagaId) {
            log.warn("Saga orchestration service unavailable, using fallback for getSagaHistory: {}", sagaId, cause);
            return ResponseEntity.ok(Collections.emptyList());
        }

        @Override
        public ResponseEntity<SagaStepResponse> retrySagaStep(String sagaId, String stepId) {
            log.warn("Saga orchestration service unavailable, using fallback for retrySagaStep: {} - {}", sagaId, stepId, cause);
            
            SagaStepResponse response = new SagaStepResponse(
                stepId,
                "FALLBACK_RETRY",
                Collections.singletonMap("fallback", true),
                "Saga service unavailable for retry",
                LocalDateTime.now(),
                0L
            );
            
            return ResponseEntity.ok(response);
        }

        @Override
        public ResponseEntity<List<SagaTransactionResponse>> getActiveSagas(int page, int size) {
            log.warn("Saga orchestration service unavailable, using fallback for getActiveSagas", cause);
            return ResponseEntity.ok(Collections.emptyList());
        }

        @Override
        public ResponseEntity<Void> cancelSaga(String sagaId, String reason) {
            log.warn("Saga orchestration service unavailable, using fallback for cancelSaga: {}", sagaId, cause);
            return ResponseEntity.ok().build();
        }

        @Override
        public ResponseEntity<SagaMetrics> getSagaMetrics() {
            log.warn("Saga orchestration service unavailable, using fallback for getSagaMetrics", cause);

            SagaMetrics metrics = new SagaMetrics(
                0L, 0L, 0L, 0L, 0L,
                0.0, 0.0,
                Collections.emptyMap()
            );

            return ResponseEntity.ok(metrics);
        }

        @Override
        public ResponseEntity<PaymentSagaExecutionResult> startPaymentSaga(PaymentSagaRequest request) {
            log.warn("Saga orchestration service unavailable, using fallback for startPaymentSaga: {}",
                request != null ? request.sagaId() : "null", cause);

            PaymentSagaExecutionResult result = new PaymentSagaExecutionResult(
                request != null ? request.sagaId() : "FALLBACK-SAGA",
                request != null ? request.paymentId() : "FALLBACK-PAYMENT",
                "FALLBACK_PENDING",
                LocalDateTime.now(),
                null,
                0,
                0,
                Collections.singletonMap("fallback", true),
                "Saga service unavailable - fallback mode",
                false,
                Collections.emptyList()
            );

            return ResponseEntity.ok(result);
        }
    }
}