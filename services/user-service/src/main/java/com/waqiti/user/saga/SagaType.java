package com.waqiti.user.saga;

/**
 * Saga Types
 *
 * Different types of sagas supported by the system
 */
public enum SagaType {
    /**
     * User registration saga
     */
    USER_REGISTRATION,

    /**
     * Account deletion saga (GDPR)
     */
    ACCOUNT_DELETION,

    /**
     * KYC verification saga
     */
    KYC_VERIFICATION,

    /**
     * Password reset saga
     */
    PASSWORD_RESET
}
