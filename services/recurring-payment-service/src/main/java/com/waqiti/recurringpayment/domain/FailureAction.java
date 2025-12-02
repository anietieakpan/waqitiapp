package com.waqiti.recurringpayment.domain;

public enum FailureAction {
    CONTINUE,   // Continue with next scheduled execution
    PAUSE,      // Pause the recurring payment
    CANCEL      // Cancel the recurring payment
}