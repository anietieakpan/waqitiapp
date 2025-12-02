package com.waqiti.corebanking.exception;

import java.util.UUID;

/**
 * Exception thrown when a statement job is not found
 */
public class StatementJobNotFoundException extends CoreBankingException {
    
    private final UUID jobId;
    
    public StatementJobNotFoundException(String message, UUID jobId) {
        super(message);
        this.jobId = jobId;
    }
    
    public StatementJobNotFoundException(String message, UUID jobId, Throwable cause) {
        super(message, cause);
        this.jobId = jobId;
    }
    
    public UUID getJobId() {
        return jobId;
    }
    
    @Override
    public String getErrorCode() {
        return "STATEMENT_JOB_NOT_FOUND";
    }
}