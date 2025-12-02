package com.waqiti.rewards.exception;

/**
 * Exception thrown when a referral program is not found
 */
public class ReferralProgramNotFoundException extends RuntimeException {

    public ReferralProgramNotFoundException(String message) {
        super(message);
    }

    public ReferralProgramNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
