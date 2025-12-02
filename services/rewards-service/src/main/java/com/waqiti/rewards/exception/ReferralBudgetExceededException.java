package com.waqiti.rewards.exception;

/**
 * Exception thrown when a referral program budget is exceeded
 */
public class ReferralBudgetExceededException extends RuntimeException {

    public ReferralBudgetExceededException(String message) {
        super(message);
    }

    public ReferralBudgetExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
