package com.waqiti.saga.service;

import com.waqiti.common.saga.SagaStatus;
import com.waqiti.saga.domain.Saga;
import com.waqiti.saga.domain.TransferSaga;
import com.waqiti.saga.repository.SagaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class SagaOrchestrationService {
    
    private final SagaRepository sagaRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SagaEventHandler sagaEventHandler;
    
    public CompletableFuture<String> startTransferSaga(String transactionId, String fromAccountId, 
                                                      String toAccountId, BigDecimal amount, 
                                                      String currency, String userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Starting transfer saga for transaction: {}", transactionId);
                
                // Create transfer saga
                TransferSaga saga = new TransferSaga(transactionId, fromAccountId, toAccountId, 
                    amount, currency, userId);
                
                // Save saga
                sagaRepository.save(saga);
                
                // Start the saga
                saga.start();
                sagaRepository.update(saga);
                
                // Publish saga started event
                publishSagaEvent("SagaStarted", saga);
                
                // Start the first step
                initiateNextStep(saga);
                
                log.info("Transfer saga started successfully: {}", saga.getSagaId());
                return saga.getSagaId();
                
            } catch (Exception e) {
                log.error("Error starting transfer saga for transaction: {}", transactionId, e);
                throw new SagaException("Failed to start transfer saga", e);
            }
        });
    }
    
    public CompletableFuture<Void> handleSagaEvent(String sagaId, Object event) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Handling saga event for saga: {}", sagaId);
                
                Optional<Saga> sagaOpt = sagaRepository.findById(sagaId);
                if (sagaOpt.isEmpty()) {
                    log.warn("Saga not found: {}", sagaId);
                    return;
                }
                
                Saga saga = sagaOpt.get();
                
                // Check if saga is still active
                if (isSagaTerminated(saga)) {
                    log.debug("Saga {} is already terminated with status: {}", sagaId, saga.getStatus());
                    return;
                }
                
                // Check for timeout
                if (saga.isExpired()) {
                    log.warn("Saga {} has expired", sagaId);
                    handleSagaTimeout(saga);
                    return;
                }
                
                // Handle the event
                saga.handleEvent(event);
                
                // Update saga state
                sagaRepository.update(saga);
                
                // Check if saga is completed or failed
                if (Saga.SagaStatus.COMPLETED.name().equals(saga.getStatus())) {
                    handleSagaCompletion(saga);
                } else if (Saga.SagaStatus.FAILED.name().equals(saga.getStatus())) {
                    handleSagaFailure(saga);
                } else if (saga.canProceed()) {
                    initiateNextStep(saga);
                }
                
            } catch (Exception e) {
                log.error("Error handling saga event for saga: {}", sagaId, e);
                handleSagaError(sagaId, e);
            }
        });
    }
    
    public CompletableFuture<Void> compensateSaga(String sagaId, String reason) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Starting compensation for saga: {} due to: {}", sagaId, reason);
                
                Optional<Saga> sagaOpt = sagaRepository.findById(sagaId);
                if (sagaOpt.isEmpty()) {
                    log.warn("Saga not found for compensation: {}", sagaId);
                    return;
                }
                
                Saga saga = sagaOpt.get();
                
                // Start compensation
                saga.compensate();
                sagaRepository.update(saga);
                
                // Publish compensation event
                publishSagaEvent("SagaCompensated", saga);
                
                log.info("Saga compensation completed: {}", sagaId);
                
            } catch (Exception e) {
                log.error("Error compensating saga: {}", sagaId, e);
                throw new SagaException("Failed to compensate saga", e);
            }
        });
    }
    
    public CompletableFuture<List<Saga>> getActiveSagas() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sagaRepository.findByStatus(List.of(
                    Saga.SagaStatus.STARTED.name(),
                    Saga.SagaStatus.IN_PROGRESS.name(),
                    Saga.SagaStatus.COMPENSATING.name()
                ));
            } catch (Exception e) {
                log.error("Error retrieving active sagas", e);
                throw new SagaException("Failed to retrieve active sagas", e);
            }
        });
    }
    
    public CompletableFuture<Optional<Saga>> getSagaById(String sagaId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sagaRepository.findById(sagaId);
            } catch (Exception e) {
                log.error("Error retrieving saga: {}", sagaId, e);
                throw new SagaException("Failed to retrieve saga", e);
            }
        });
    }
    
    public CompletableFuture<List<Saga>> getSagasByCorrelationId(String correlationId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sagaRepository.findByCorrelationId(correlationId);
            } catch (Exception e) {
                log.error("Error retrieving sagas by correlation ID: {}", correlationId, e);
                throw new SagaException("Failed to retrieve sagas", e);
            }
        });
    }
    
    @Scheduled(fixedDelay = 60000) // Run every minute
    public void handleTimeoutSagas() {
        try {
            log.debug("Checking for timed-out sagas");
            
            List<Saga> activeSagas = sagaRepository.findByStatus(List.of(
                Saga.SagaStatus.STARTED.name(),
                Saga.SagaStatus.IN_PROGRESS.name()
            ));
            
            for (Saga saga : activeSagas) {
                if (saga.isExpired()) {
                    log.warn("Saga {} has timed out", saga.getSagaId());
                    handleSagaTimeout(saga);
                }
            }
            
        } catch (Exception e) {
            log.error("Error checking for timed-out sagas", e);
        }
    }
    
    @Scheduled(fixedDelay = 300000) // Run every 5 minutes
    public void retryFailedSagas() {
        try {
            log.debug("Checking for failed sagas that can be retried");
            
            List<Saga> failedSagas = sagaRepository.findByStatus(List.of(Saga.SagaStatus.FAILED.name()));
            
            for (Saga saga : failedSagas) {
                if (saga.canRetry()) {
                    log.info("Retrying failed saga: {}", saga.getSagaId());
                    saga.incrementRetryCount();
                    saga.setStatus(Saga.SagaStatus.IN_PROGRESS.name());
                    sagaRepository.update(saga);
                    
                    // Re-initiate current step
                    initiateCurrentStep(saga);
                }
            }
            
        } catch (Exception e) {
            log.error("Error retrying failed sagas", e);
        }
    }
    
    private void initiateNextStep(Saga saga) {
        try {
            log.debug("Initiating next step for saga: {} - step: {}", saga.getSagaId(), saga.getCurrentStep());
            
            // Publish step initiation event
            publishStepEvent(saga, "StepInitiated");
            
            // The actual step execution would be handled by appropriate services
            // listening to these events
            
        } catch (Exception e) {
            log.error("Error initiating next step for saga: {}", saga.getSagaId(), e);
            handleSagaError(saga.getSagaId(), e);
        }
    }
    
    private void initiateCurrentStep(Saga saga) {
        try {
            log.debug("Re-initiating current step for saga: {} - step: {}", saga.getSagaId(), saga.getCurrentStep());
            publishStepEvent(saga, "StepRetry");
        } catch (Exception e) {
            log.error("Error re-initiating current step for saga: {}", saga.getSagaId(), e);
        }
    }
    
    private void handleSagaCompletion(Saga saga) {
        log.info("Saga completed successfully: {}", saga.getSagaId());
        publishSagaEvent("SagaCompleted", saga);
    }
    
    private void handleSagaFailure(Saga saga) {
        log.error("Saga failed: {} - reason: {}", saga.getSagaId(), saga.getErrorMessage());
        
        // Decide whether to compensate or retry
        if (saga.canRetry()) {
            log.info("Saga {} will be retried", saga.getSagaId());
        } else {
            log.info("Starting compensation for failed saga: {}", saga.getSagaId());
            saga.compensate();
            sagaRepository.update(saga);
        }
        
        publishSagaEvent("SagaFailed", saga);
    }
    
    private void handleSagaTimeout(Saga saga) {
        log.warn("Handling timeout for saga: {}", saga.getSagaId());
        
        saga.setStatus(SagaStatus.FAILED.name());
        saga.setErrorMessage("Saga timed out after " + saga.getExpiresAt());
        sagaRepository.update(saga);
        
        // Start compensation
        saga.compensate();
        sagaRepository.update(saga);
        
        publishSagaEvent("SagaTimedOut", saga);
    }
    
    private void handleSagaError(String sagaId, Exception error) {
        try {
            Optional<Saga> sagaOpt = sagaRepository.findById(sagaId);
            if (sagaOpt.isPresent()) {
                Saga saga = sagaOpt.get();
                saga.markAsFailed("Unexpected error: " + error.getMessage());
                sagaRepository.update(saga);
                publishSagaEvent("SagaError", saga);
            }
        } catch (Exception e) {
            log.error("Error handling saga error for saga: {}", sagaId, e);
        }
    }
    
    private boolean isSagaTerminated(Saga saga) {
        String status = saga.getStatus();
        return SagaStatus.COMPLETED.name().equals(status) ||
               SagaStatus.COMPENSATED.name().equals(status) ||
               SagaStatus.FAILED.name().equals(status);
    }
    
    private void publishSagaEvent(String eventType, Saga saga) {
        try {
            SagaEvent event = SagaEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .eventType(eventType)
                .sagaId(saga.getSagaId())
                .sagaType(saga.getSagaType())
                .correlationId(saga.getCorrelationId())
                .sagaData(saga.getSagaData())
                .timestamp(Instant.now())
                .build();
            
            kafkaTemplate.send("saga-events", saga.getSagaId(), event);
            log.debug("Published saga event: {} for saga: {}", eventType, saga.getSagaId());
            
        } catch (Exception e) {
            log.error("Error publishing saga event: {} for saga: {}", eventType, saga.getSagaId(), e);
        }
    }
    
    private void publishStepEvent(Saga saga, String eventType) {
        try {
            StepEvent event = StepEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .eventType(eventType)
                .sagaId(saga.getSagaId())
                .sagaType(saga.getSagaType())
                .currentStep(saga.getCurrentStep())
                .stepIndex(saga.getStepIndex())
                .correlationId(saga.getCorrelationId())
                .sagaData(saga.getSagaData())
                .timestamp(Instant.now())
                .build();
            
            String topic = "saga-step-" + saga.getSagaType().toLowerCase();
            kafkaTemplate.send(topic, saga.getSagaId(), event);
            log.debug("Published step event: {} for saga: {} step: {}", 
                eventType, saga.getSagaId(), saga.getCurrentStep());
            
        } catch (Exception e) {
            log.error("Error publishing step event: {} for saga: {}", eventType, saga.getSagaId(), e);
        }
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SagaEvent {
        private String eventId;
        private String eventType;
        private String sagaId;
        private String sagaType;
        private String correlationId;
        private Object sagaData;
        private Instant timestamp;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class StepEvent {
        private String eventId;
        private String eventType;
        private String sagaId;
        private String sagaType;
        private String currentStep;
        private Integer stepIndex;
        private String correlationId;
        private Object sagaData;
        private Instant timestamp;
    }
    
    public static class SagaException extends RuntimeException {
        public SagaException(String message) {
            super(message);
        }
        
        public SagaException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}