package com.waqiti.common.bulkhead;

/**
 * Types of resources that can be isolated using bulkhead pattern
 */
public enum ResourceType {
    PAYMENT_PROCESSING,
    KYC_VERIFICATION,
    FRAUD_DETECTION,
    NOTIFICATION,
    ANALYTICS,
    CORE_BANKING
}