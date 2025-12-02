package com.waqiti.common.saga;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

/**
 * Service for managing saga timeouts and scheduling timeout actions
 */
@Service
@Slf4j
public class SagaTimeoutService {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private final ConcurrentHashMap<String, ScheduledFuture<?>> timeoutTasks = new ConcurrentHashMap<>();

    /**
     * Schedule a timeout for a saga
     */
    public SagaTimeoutHandle scheduleTimeout(String sagaId, int timeoutMinutes, Runnable timeoutAction) {
        log.debug("Scheduling timeout for saga: sagaId={}, timeoutMinutes={}", sagaId, timeoutMinutes);
        
        ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
            try {
                log.warn("Saga timeout triggered: sagaId={}", sagaId);
                timeoutAction.run();
            } catch (Exception e) {
                log.error("Error executing timeout action for saga: " + sagaId, e);
            } finally {
                timeoutTasks.remove(sagaId);
            }
        }, timeoutMinutes, TimeUnit.MINUTES);
        
        timeoutTasks.put(sagaId, timeoutTask);
        
        return new SagaTimeoutHandle(sagaId, timeoutTask);
    }

    /**
     * Cancel timeout for a saga
     */
    public boolean cancelTimeout(String sagaId) {
        ScheduledFuture<?> timeoutTask = timeoutTasks.remove(sagaId);
        if (timeoutTask != null) {
            boolean cancelled = timeoutTask.cancel(false);
            log.debug("Cancelled timeout for saga: sagaId={}, success={}", sagaId, cancelled);
            return cancelled;
        }
        return false;
    }

    /**
     * Check if saga has active timeout
     */
    public boolean hasActiveTimeout(String sagaId) {
        ScheduledFuture<?> timeoutTask = timeoutTasks.get(sagaId);
        return timeoutTask != null && !timeoutTask.isDone();
    }

    /**
     * Get remaining timeout in seconds
     */
    public long getRemainingTimeoutSeconds(String sagaId) {
        ScheduledFuture<?> timeoutTask = timeoutTasks.get(sagaId);
        if (timeoutTask != null && !timeoutTask.isDone()) {
            return timeoutTask.getDelay(TimeUnit.SECONDS);
        }
        return -1;
    }

    /**
     * Get count of active timeouts
     */
    public int getActiveTimeoutCount() {
        return (int) timeoutTasks.values().stream()
            .filter(task -> !task.isDone())
            .count();
    }

    /**
     * Shutdown the timeout service
     */
    public void shutdown() {
        log.info("Shutting down saga timeout service");
        
        // Cancel all pending timeouts
        timeoutTasks.values().forEach(task -> task.cancel(false));
        timeoutTasks.clear();
        
        // Shutdown the scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}