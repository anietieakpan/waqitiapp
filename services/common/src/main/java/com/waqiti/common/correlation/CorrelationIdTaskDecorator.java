package com.waqiti.common.correlation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

/**
 * Task decorator to propagate correlation IDs to async tasks
 * Ensures correlation context is maintained across thread boundaries
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CorrelationIdTaskDecorator implements TaskDecorator {

    private final CorrelationIdService correlationIdService;

    @Override
    public Runnable decorate(Runnable runnable) {
        // Capture current correlation context
        CorrelationIdService.CorrelationContext contextToPropagate = 
            correlationIdService.copyContext();
        
        String correlationId = correlationIdService.getCorrelationId().orElse("NONE");
        
        log.debug("Decorating async task with correlation ID: {}", correlationId);
        
        return () -> {
            try {
                // Restore correlation context in new thread
                correlationIdService.restoreContext(contextToPropagate);
                log.debug("Restored correlation ID {} in async thread", correlationId);
                
                // Execute the actual task
                runnable.run();
                
            } catch (Exception e) {
                log.error("Error in async task with correlation ID: {}", correlationId, e);
                throw e;
            } finally {
                // Clear context after task execution
                correlationIdService.clearContext();
                log.debug("Cleared correlation context after async task execution");
            }
        };
    }
}