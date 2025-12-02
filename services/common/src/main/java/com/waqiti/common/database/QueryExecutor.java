package com.waqiti.common.database;

import java.util.List;

/**
 * Functional interface for executing database queries with optimization
 */
@FunctionalInterface
public interface QueryExecutor<T> {
    /**
     * Execute the query and return results
     */
    List<T> execute() throws Exception;
}