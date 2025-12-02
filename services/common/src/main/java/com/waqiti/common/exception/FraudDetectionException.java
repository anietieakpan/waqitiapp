package com.waqiti.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class FraudDetectionException extends BusinessException {
    private final String riskLevel;
    private final String blockReason;
    private final boolean requiresVerification;
    private final HttpStatus httpStatus;

    public FraudDetectionException(String message, String riskLevel, String blockReason, boolean requiresVerification) {
        super(message, "FRAUD_DETECTED");
        this.riskLevel = riskLevel;
        this.blockReason = blockReason;
        this.requiresVerification = requiresVerification;
        this.httpStatus = HttpStatus.FORBIDDEN;
    }
}