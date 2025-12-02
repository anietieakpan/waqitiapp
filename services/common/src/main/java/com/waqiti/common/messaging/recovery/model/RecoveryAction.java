package com.waqiti.common.messaging.recovery.model;

public enum RecoveryAction {
    IMMEDIATE_RETRY,
    EXPONENTIAL_BACKOFF,
    MANUAL_REVIEW,
    REPLAY_FROM_SOURCE,
    COMPENSATION,
    DEAD_STORAGE
}

