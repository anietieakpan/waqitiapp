package com.waqiti.common.exception;

import java.util.List;

/**
 * Exception thrown when database deadlocks occur
 */
public class DeadlockException extends ConcurrencyException {
    
    private final List<String> involvedResources;
    private final String deadlockInfo;
    
    public DeadlockException(String message) {
        super(message);
        this.involvedResources = null;
        this.deadlockInfo = null;
    }
    
    public DeadlockException(String message, List<String> involvedResources) {
        super(message);
        this.involvedResources = involvedResources;
        this.deadlockInfo = null;
    }
    
    public DeadlockException(String message, List<String> involvedResources, String deadlockInfo) {
        super(message);
        this.involvedResources = involvedResources;
        this.deadlockInfo = deadlockInfo;
    }
    
    public DeadlockException(String message, Throwable cause) {
        super(message, cause);
        this.involvedResources = null;
        this.deadlockInfo = null;
    }
    
    public DeadlockException(String message, List<String> involvedResources, String deadlockInfo, Throwable cause) {
        super(message, cause);
        this.involvedResources = involvedResources;
        this.deadlockInfo = deadlockInfo;
    }
    
    public List<String> getInvolvedResources() {
        return involvedResources;
    }
    
    public String getDeadlockInfo() {
        return deadlockInfo;
    }
}