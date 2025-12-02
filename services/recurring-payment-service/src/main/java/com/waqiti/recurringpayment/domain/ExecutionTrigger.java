package com.waqiti.recurringpayment.domain;

public enum ExecutionTrigger {
    SCHEDULED,  // Automatic scheduled execution
    MANUAL,     // Manual user-triggered execution
    RETRY,      // Retry of failed execution
    ADMIN       // Admin-triggered execution
}