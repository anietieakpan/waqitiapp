package com.waqiti.common.database.performance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents a database deadlock event for monitoring and analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadlockEvent {
    private String tableName;
    private String lockType;
    private String query1;
    private String query2;
    private Instant timestamp;
    private String databaseName;
    private String sessionId1;
    private String sessionId2;
    private String resolution;
    private long durationMs;
}