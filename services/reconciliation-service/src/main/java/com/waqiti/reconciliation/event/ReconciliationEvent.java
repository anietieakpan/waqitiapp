package com.waqiti.reconciliation.event;

import com.waqiti.reconciliation.command.ReconciliationCommand;
import com.waqiti.reconciliation.model.ReconciliationResult;
import lombok.Builder;
import lombok.Data;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * Event-driven architecture for reconciliation process
 * Enables loose coupling and async processing
 */
@Data
public class ReconciliationEvent extends ApplicationEvent {
    
    private final String eventId;
    private final EventType eventType;
    private final String executionId;
    private final ReconciliationCommand command;
    private final Instant timestamp;
    private final ReconciliationResult result;
    private final Exception error;
    
    private ReconciliationEvent(Object source, EventType eventType, String executionId, 
                               ReconciliationCommand command, ReconciliationResult result, 
                               Exception error) {
        super(source);
        this.eventId = java.util.UUID.randomUUID().toString();
        this.eventType = eventType;
        this.executionId = executionId;
        this.command = command;
        this.timestamp = Instant.now();
        this.result = result;
        this.error = error;
    }
    
    /**
     * Create a reconciliation started event
     */
    public static ReconciliationEvent started(ReconciliationCommand command, String executionId) {
        return new ReconciliationEvent(command, EventType.STARTED, executionId, command, null, null);
    }
    
    /**
     * Create a reconciliation completed event
     */
    public static ReconciliationEvent completed(ReconciliationCommand command, String executionId, 
                                               ReconciliationResult result) {
        return new ReconciliationEvent(command, EventType.COMPLETED, executionId, command, result, null);
    }
    
    /**
     * Create a reconciliation failed event
     */
    public static ReconciliationEvent failed(ReconciliationCommand command, String executionId, 
                                            Exception error) {
        return new ReconciliationEvent(command, EventType.FAILED, executionId, command, null, error);
    }
    
    /**
     * Create a reconciliation discrepancy detected event
     */
    public static ReconciliationEvent discrepancyDetected(ReconciliationCommand command, 
                                                         String executionId, 
                                                         ReconciliationResult result) {
        return new ReconciliationEvent(command, EventType.DISCREPANCY_DETECTED, executionId, 
                                      command, result, null);
    }
    
    /**
     * Create a reconciliation progress event
     */
    public static ReconciliationEvent progress(ReconciliationCommand command, String executionId, 
                                              ReconciliationResult partialResult) {
        return new ReconciliationEvent(command, EventType.PROGRESS, executionId, command, 
                                      partialResult, null);
    }
    
    public enum EventType {
        STARTED,
        PROGRESS,
        COMPLETED,
        FAILED,
        DISCREPANCY_DETECTED,
        CANCELLED,
        TIMEOUT
    }
}