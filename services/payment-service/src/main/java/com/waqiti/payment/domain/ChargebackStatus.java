package com.waqiti.payment.domain;

public enum ChargebackStatus {
    INITIATED,
    DISPUTED,
    ACCEPTED,
    RESPONSE_FAILED,
    PENDING_RESPONSE,
    WON,
    LOST,
    EXPIRED
}
