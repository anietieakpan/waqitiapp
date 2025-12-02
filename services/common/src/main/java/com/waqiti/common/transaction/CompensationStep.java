package com.waqiti.common.transaction;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a compensation step in a SAGA transaction
 */
@Data
@Builder
public class CompensationStep {
    private String name;
    private String description;
    private Runnable compensationAction;
    private int retryCount;
    private long timeoutMs;
    
    public static CompensationStep create(String name, Runnable action) {
        return CompensationStep.builder()
            .name(name)
            .compensationAction(action)
            .retryCount(3)
            .timeoutMs(30000)
            .build();
    }
}
