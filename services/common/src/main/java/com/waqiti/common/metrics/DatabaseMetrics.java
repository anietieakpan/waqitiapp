package com.waqiti.common.metrics;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

/**
 * Database operation metrics data model
 */
@Data
@Builder
public class DatabaseMetrics {
    private String operation; // SELECT, INSERT, UPDATE, DELETE
    private String tableName;
    private String status; // SUCCESS, FAILURE, TIMEOUT
    private Duration queryTime;
    private int rowsAffected;
    private String errorCode;
    private String connectionPool;
    private boolean indexUsed;
    private long memoryUsed;
    private String queryHash;
}