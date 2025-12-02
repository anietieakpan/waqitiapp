package com.waqiti.common.batch;

/**
 * Functional interface for bulk operations
 */
@FunctionalInterface
public interface BulkOperationProcessor<T> {
    void process(T item);
}