package com.waqiti.common.exception;

import lombok.Getter;
import java.util.HashMap;
import java.util.Map;

@Getter
public class BusinessRuleViolationException extends RuntimeException {
    private final String errorCode;
    private final String userFriendlyMessage;
    private final Map<String, Object> details;
    private final boolean retryable;

    public BusinessRuleViolationException(String errorCode, String message, String userFriendlyMessage) {
        super(message);
        this.errorCode = errorCode;
        this.userFriendlyMessage = userFriendlyMessage;
        this.details = new HashMap<>();
        this.retryable = false;
    }

    public BusinessRuleViolationException(String errorCode, String message, String userFriendlyMessage, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.userFriendlyMessage = userFriendlyMessage;
        this.details = new HashMap<>();
        this.retryable = retryable;
    }

    public BusinessRuleViolationException(String errorCode, String message, String userFriendlyMessage, Map<String, Object> details, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.userFriendlyMessage = userFriendlyMessage;
        this.details = details != null ? new HashMap<>(details) : new HashMap<>();
        this.retryable = retryable;
    }
}
