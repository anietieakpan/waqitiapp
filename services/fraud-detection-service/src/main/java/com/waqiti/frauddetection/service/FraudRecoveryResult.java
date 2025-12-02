package com.waqiti.frauddetection.service;

import lombok.Builder;
import lombok.Data;

/**
 * Result of fraud alert recovery attempt
 */
@Data
@Builder
public class FraudRecoveryResult {

    private boolean recovered;
    private boolean retriable;
    private String failureReason;
    private String recoveryAction;
    private String details;
}
