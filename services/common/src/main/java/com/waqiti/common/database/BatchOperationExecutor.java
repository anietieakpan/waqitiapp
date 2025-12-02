package com.waqiti.common.database;

import java.util.List;

/**
 * Functional interface for executing batch database operations
 */
@FunctionalInterface
public interface BatchOperationExecutor<T> {
    /**
     * Execute batch operation on the provided batch of entities
     */
    void execute(List<T> batch) throws Exception;
}