package com.waqiti.common.saga;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Gauge;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for collecting and reporting saga execution metrics
 */
@Service
@Slf4j
public class SagaMetricsService {

    private final MeterRegistry meterRegistry;
    
    // Counters
    private final Counter sagaStartedCounter;
    private final Counter sagaCompletedCounter;
    private final Counter sagaFailedCounter;
    private final Counter sagaCompensatedCounter;
    private final Counter stepExecutionCounter;
    private final Counter stepFailureCounter;
    private final Counter compensationSuccessCounter;
    private final Counter compensationFailureCounter;
    
    // Timers
    private final Timer sagaExecutionTimer;
    private final Timer stepExecutionTimer;
    private final Timer compensationTimer;
    
    // Gauges
    private final AtomicLong activeSagasCount = new AtomicLong(0);
    private final AtomicLong pendingStepsCount = new AtomicLong(0);
    private final AtomicLong runningStepsCount = new AtomicLong(0);
    
    // Per-saga-type metrics
    private final ConcurrentHashMap<String, Counter> sagaTypeCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> sagaTypeTimers = new ConcurrentHashMap<>();

    @Autowired
    public SagaMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize counters
        this.sagaStartedCounter = Counter.builder("saga.started")
            .description("Number of sagas started")
            .register(meterRegistry);
            
        this.sagaCompletedCounter = Counter.builder("saga.completed")
            .description("Number of sagas completed successfully")
            .register(meterRegistry);
            
        this.sagaFailedCounter = Counter.builder("saga.failed")
            .description("Number of sagas that failed")
            .register(meterRegistry);
            
        this.sagaCompensatedCounter = Counter.builder("saga.compensated")
            .description("Number of sagas that were compensated")
            .register(meterRegistry);
            
        this.stepExecutionCounter = Counter.builder("saga.step.executed")
            .description("Number of saga steps executed")
            .register(meterRegistry);
            
        this.stepFailureCounter = Counter.builder("saga.step.failed")
            .description("Number of saga steps that failed")
            .register(meterRegistry);
            
        this.compensationSuccessCounter = Counter.builder("saga.compensation.success")
            .description("Number of successful compensations")
            .register(meterRegistry);
            
        this.compensationFailureCounter = Counter.builder("saga.compensation.failed")
            .description("Number of failed compensations")
            .register(meterRegistry);
        
        // Initialize timers
        this.sagaExecutionTimer = Timer.builder("saga.execution.duration")
            .description("Time taken to execute sagas")
            .register(meterRegistry);
            
        this.stepExecutionTimer = Timer.builder("saga.step.execution.duration")
            .description("Time taken to execute saga steps")
            .register(meterRegistry);
            
        this.compensationTimer = Timer.builder("saga.compensation.duration")
            .description("Time taken to execute compensations")
            .register(meterRegistry);
        
        // Initialize gauges
        Gauge.builder("saga.active.count", activeSagasCount, AtomicLong::get)
            .description("Number of currently active sagas")
            .register(meterRegistry);
            
        Gauge.builder("saga.steps.pending.count", pendingStepsCount, AtomicLong::get)
            .description("Number of pending saga steps")
            .register(meterRegistry);
            
        Gauge.builder("saga.steps.running.count", runningStepsCount, AtomicLong::get)
            .description("Number of currently running saga steps")
            .register(meterRegistry);
    }

    /**
     * Record saga started
     */
    public void recordSagaStarted(String sagaType) {
        sagaStartedCounter.increment();
        activeSagasCount.incrementAndGet();
        
        // Per-type metrics
        getSagaTypeCounter(sagaType, "started").increment();
        
        log.debug("Recorded saga started: type={}", sagaType);
    }

    /**
     * Record saga completed
     */
    public void recordSagaCompleted(String sagaType, long durationMs) {
        sagaCompletedCounter.increment();
        activeSagasCount.decrementAndGet();
        sagaExecutionTimer.record(Duration.ofMillis(durationMs));
        
        // Per-type metrics
        getSagaTypeCounter(sagaType, "completed").increment();
        getSagaTypeTimer(sagaType).record(Duration.ofMillis(durationMs));
        
        log.debug("Recorded saga completed: type={}, duration={}ms", sagaType, durationMs);
    }

    /**
     * Record saga failed
     */
    public void recordSagaFailed(String sagaType, long durationMs) {
        sagaFailedCounter.increment();
        activeSagasCount.decrementAndGet();
        sagaExecutionTimer.record(Duration.ofMillis(durationMs));
        
        // Per-type metrics
        getSagaTypeCounter(sagaType, "failed").increment();
        getSagaTypeTimer(sagaType).record(Duration.ofMillis(durationMs));
        
        log.debug("Recorded saga failed: type={}, duration={}ms", sagaType, durationMs);
    }

    /**
     * Record saga compensated
     */
    public void recordSagaCompensated(String sagaType, long durationMs) {
        sagaCompensatedCounter.increment();
        activeSagasCount.decrementAndGet();
        
        // Per-type metrics
        getSagaTypeCounter(sagaType, "compensated").increment();
        
        log.debug("Recorded saga compensated: type={}, duration={}ms", sagaType, durationMs);
    }

    /**
     * Record step execution
     */
    public void recordStepExecution(String sagaId, String stepId, boolean success, long durationMs) {
        if (success) {
            stepExecutionCounter.increment();
        } else {
            stepFailureCounter.increment();
        }
        
        stepExecutionTimer.record(Duration.ofMillis(durationMs));
        
        log.debug("Recorded step execution: sagaId={}, stepId={}, success={}, duration={}ms", 
            sagaId, stepId, success, durationMs);
    }

    /**
     * Record step started
     */
    public void recordStepStarted(String stepType) {
        runningStepsCount.incrementAndGet();
        
        log.debug("Recorded step started: type={}", stepType);
    }

    /**
     * Record step completed
     */
    public void recordStepCompleted(String stepType) {
        runningStepsCount.decrementAndGet();
        
        log.debug("Recorded step completed: type={}", stepType);
    }

    /**
     * Record compensation success
     */
    public void recordCompensationSuccess(String sagaId) {
        compensationSuccessCounter.increment();
        
        log.debug("Recorded compensation success: sagaId={}", sagaId);
    }

    /**
     * Record compensation failure
     */
    public void recordCompensationFailure(String sagaId) {
        compensationFailureCounter.increment();
        
        log.debug("Recorded compensation failure: sagaId={}", sagaId);
    }

    /**
     * Record compensation execution time
     */
    public void recordCompensationDuration(long durationMs) {
        compensationTimer.record(Duration.ofMillis(durationMs));
    }

    /**
     * Update active sagas count
     */
    public void updateActiveSagasCount(long count) {
        activeSagasCount.set(count);
    }

    /**
     * Update pending steps count
     */
    public void updatePendingStepsCount(long count) {
        pendingStepsCount.set(count);
    }

    /**
     * Update running steps count
     */
    public void updateRunningStepsCount(long count) {
        runningStepsCount.set(count);
    }

    /**
     * Get current metrics summary
     */
    public SagaMetricsSummary getMetricsSummary() {
        return SagaMetricsSummary.builder()
            .sagasStarted((long) sagaStartedCounter.count())
            .sagasCompleted((long) sagaCompletedCounter.count())
            .sagasFailed((long) sagaFailedCounter.count())
            .sagasCompensated((long) sagaCompensatedCounter.count())
            .activeSagas(activeSagasCount.get())
            .stepsExecuted((long) stepExecutionCounter.count())
            .stepsFailed((long) stepFailureCounter.count())
            .pendingSteps(pendingStepsCount.get())
            .runningSteps(runningStepsCount.get())
            .averageSagaDurationMs(sagaExecutionTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS))
            .averageStepDurationMs(stepExecutionTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS))
            .compensationsSuccessful((long) compensationSuccessCounter.count())
            .compensationsFailed((long) compensationFailureCounter.count())
            .build();
    }

    // Private helper methods

    private Counter getSagaTypeCounter(String sagaType, String operation) {
        String key = sagaType + "." + operation;
        return sagaTypeCounters.computeIfAbsent(key, k -> 
            Counter.builder("saga.type." + operation)
                .description("Number of " + operation + " sagas by type")
                .tag("sagaType", sagaType)
                .register(meterRegistry));
    }

    private Timer getSagaTypeTimer(String sagaType) {
        return sagaTypeTimers.computeIfAbsent(sagaType, type -> 
            Timer.builder("saga.type.execution.duration")
                .description("Execution duration by saga type")
                .tag("sagaType", type)
                .register(meterRegistry));
    }
}

/**
 * Summary of saga metrics
 */
@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
class SagaMetricsSummary {
    private long sagasStarted;
    private long sagasCompleted;
    private long sagasFailed;
    private long sagasCompensated;
    private long activeSagas;
    private long stepsExecuted;
    private long stepsFailed;
    private long pendingSteps;
    private long runningSteps;
    private double averageSagaDurationMs;
    private double averageStepDurationMs;
    private long compensationsSuccessful;
    private long compensationsFailed;
    
    /**
     * Calculate success rate
     */
    public double getSuccessRate() {
        long total = sagasCompleted + sagasFailed;
        return total > 0 ? (double) sagasCompleted / total : 0.0;
    }
    
    /**
     * Calculate step success rate
     */
    public double getStepSuccessRate() {
        long total = stepsExecuted + stepsFailed;
        return total > 0 ? (double) stepsExecuted / total : 0.0;
    }
    
    /**
     * Calculate compensation success rate
     */
    public double getCompensationSuccessRate() {
        long total = compensationsSuccessful + compensationsFailed;
        return total > 0 ? (double) compensationsSuccessful / total : 0.0;
    }
}