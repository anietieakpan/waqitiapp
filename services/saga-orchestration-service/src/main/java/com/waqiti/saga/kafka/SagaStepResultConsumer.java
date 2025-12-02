package com.waqiti.saga.kafka;

import com.waqiti.common.saga.SagaStepResult;
import com.waqiti.saga.service.SagaStateMachineService;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;

/**
 * CRITICAL FIX: Saga Step Result Consumer
 *
 * Consumes saga step results from participant services and updates
 * saga state machine to proceed with next step or trigger compensation.
 *
 * This was identified as a CRITICAL P0 blocker - saga orchestration
 * was completely broken without this consumer.
 *
 * @author Waqiti Engineering Team - Production Fix
 * @since 2.0.0
 * @priority P0-CRITICAL
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SagaStepResultConsumer {

    private final SagaStateMachineService stateMachineService;
    private final MeterRegistry meterRegistry;

    private Counter successCounter;
    private Counter failureCounter;
    private Counter compensationCounter;
    private Counter retryCounter;

    @PostConstruct
    public void initMetrics() {
        successCounter = meterRegistry.counter("saga.step.result.success");
        failureCounter = meterRegistry.counter("saga.step.result.failure");
        compensationCounter = meterRegistry.counter("saga.step.compensation.triggered");
        retryCounter = meterRegistry.counter("saga.step.retry.triggered");
    }

    /**
     * Handles saga step results from participant services.
     *
     * This is the CRITICAL missing consumer that was causing distributed
     * transactions to hang indefinitely. Now properly wired up.
     *
     * @param result The saga step result
     * @param acknowledgment Kafka acknowledgment for manual commit
     * @param partition Kafka partition for debugging
     * @param offset Kafka offset for tracking
     */
    @KafkaListener(
        topics = "${kafka.topics.saga-step-results:saga-step-results}",
        groupId = "${kafka.consumer-groups.saga-orchestrator:saga-orchestrator}",
        containerFactory = "sagaKafkaListenerContainerFactory",
        concurrency = "${kafka.saga-result-consumer.concurrency:3}"
    )
    @Timed(value = "saga.step.result.processing.time", description = "Time to process saga step result")
    public void handleSagaStepResult(
            @Payload SagaStepResult result,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_PARTITION_ID) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        long startTime = System.currentTimeMillis();

        log.info("Received saga step result: sagaId={}, step={}, status={}, partition={}, offset={}",
                result.getSagaId(), result.getStepName(), result.getStatus(), partition, offset);

        try {
            // Validate result
            if (result.getSagaId() == null || result.getStepName() == null || result.getStatus() == null) {
                log.error("Invalid saga step result received: {}", result);
                // Acknowledge to avoid reprocessing invalid message
                acknowledgment.acknowledge();
                return;
            }

            // Process based on status
            switch (result.getStatus().toUpperCase()) {
                case "SUCCESS":
                    handleSuccess(result);
                    successCounter.increment();
                    break;

                case "FAILED":
                    handleFailure(result);
                    failureCounter.increment();
                    break;

                case "RETRY":
                    handleRetry(result);
                    retryCounter.increment();
                    break;

                default:
                    log.warn("Unknown saga step result status: {}", result.getStatus());
                    // Still process as failure for safety
                    handleFailure(result);
            }

            // Acknowledge successful processing
            acknowledgment.acknowledge();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully processed saga step result in {}ms: sagaId={}, step={}",
                    duration, result.getSagaId(), result.getStepName());

        } catch (Exception e) {
            log.error("Error processing saga step result: sagaId={}, step={}",
                    result.getSagaId(), result.getStepName(), e);

            // Do NOT acknowledge - message will be retried
            // Let Kafka retry mechanism handle transient failures
            throw new RuntimeException("Failed to process saga step result", e);
        }
    }

    /**
     * Handles successful step execution.
     * Updates state machine and proceeds to next step.
     */
    private void handleSuccess(SagaStepResult result) {
        log.info("Saga step succeeded: sagaId={}, step={}, duration={}ms",
                result.getSagaId(), result.getStepName(), result.getDurationMs());

        try {
            // Update state machine with success
            boolean sagaCompleted = stateMachineService.processStepSuccess(
                result.getSagaId(),
                result.getStepName(),
                result.getData(),
                result.getTimestamp()
            );

            if (sagaCompleted) {
                log.info("✅ Saga completed successfully: sagaId={}", result.getSagaId());
                meterRegistry.counter("saga.completed.success").increment();
            } else {
                log.info("Saga step succeeded, proceeding to next step: sagaId={}, completedStep={}",
                        result.getSagaId(), result.getStepName());
            }

        } catch (Exception e) {
            log.error("Error updating saga state machine after success: sagaId={}, step={}",
                    result.getSagaId(), result.getStepName(), e);
            throw e;
        }
    }

    /**
     * Handles failed step execution.
     * Triggers compensation (rollback) for all completed steps.
     */
    private void handleFailure(SagaStepResult result) {
        log.error("❌ Saga step failed: sagaId={}, step={}, error={}, errorCode={}",
                result.getSagaId(), result.getStepName(),
                result.getErrorMessage(), result.getErrorCode());

        try {
            // Check if retry is recommended for transient failures
            if (result.isRetryable()) {
                log.info("Transient failure detected, will retry: sagaId={}, step={}",
                        result.getSagaId(), result.getStepName());
                handleRetry(result);
                return;
            }

            // Trigger compensation for non-retryable failures
            log.warn("Non-retryable failure, triggering compensation: sagaId={}", result.getSagaId());

            boolean compensationTriggered = stateMachineService.processStepFailure(
                result.getSagaId(),
                result.getStepName(),
                result.getErrorMessage(),
                result.getErrorCode(),
                result.getTimestamp()
            );

            if (compensationTriggered) {
                compensationCounter.increment();
                meterRegistry.counter("saga.completed.failure").increment();
                log.info("Compensation (rollback) triggered for saga: sagaId={}", result.getSagaId());
            }

        } catch (Exception e) {
            log.error("Error handling saga step failure: sagaId={}, step={}",
                    result.getSagaId(), result.getStepName(), e);
            throw e;
        }
    }

    /**
     * Handles retry requests for transient failures.
     */
    private void handleRetry(SagaStepResult result) {
        log.info("Saga step retry requested: sagaId={}, step={}, retryable={}",
                result.getSagaId(), result.getStepName(), result.isRetryable());

        try {
            stateMachineService.processStepRetry(
                result.getSagaId(),
                result.getStepName(),
                result.getErrorMessage()
            );

        } catch (Exception e) {
            log.error("Error handling saga step retry: sagaId={}, step={}",
                    result.getSagaId(), result.getStepName(), e);
            throw e;
        }
    }
}
