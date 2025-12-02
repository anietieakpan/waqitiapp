package com.waqiti.common.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Represents the context of a database query
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryContext {
    private String queryText;
    private String queryType;
    private String userId;
    private String sessionId;
    private String transactionId;
    private Instant timestamp;
    private Map<String, Object> parameters;
    private String applicationName;
    private String requestId;
    private boolean isPartOfTransaction;
    private QueryPriority priority;
    private boolean readOnly;
    
    // Explicit getters for compilation issues
    public String getQueryType() { return queryType; }
    public QueryPriority getPriority() { return priority; }
    private long estimatedDuration;
    private String dataSource;
    private boolean cacheable;
    private int maxRetries;
    private long timeout;
}