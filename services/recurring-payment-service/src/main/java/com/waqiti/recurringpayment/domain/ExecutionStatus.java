package com.waqiti.recurringpayment.domain;

public enum ExecutionStatus {
    SCHEDULED,  // Execution is scheduled
    PROCESSING, // Currently being processed
    COMPLETED,  // Successfully completed
    FAILED,     // Failed to execute
    RETRYING,   // Failed but retrying
    CANCELLED,  // Cancelled before execution
    SKIPPED,     // Skipped due to conditions
    PENDING, // ADDED BY aniix 21-october-25
    SUCCESS // ADDED BY aniix 21-october-25

}