package com.waqiti.common.database;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Functional interface for executing paginated database queries
 */
@FunctionalInterface
public interface PageQueryExecutor<T> {
    /**
     * Execute the paginated query and return page results
     */
    Page<T> execute(Pageable pageable) throws Exception;
}