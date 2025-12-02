package com.waqiti.familyaccount.exception;

import java.math.BigDecimal;

/**
 * Exception thrown when insufficient funds for transaction
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
public class InsufficientFundsException extends FamilyAccountException {

    public InsufficientFundsException(BigDecimal available, BigDecimal required) {
        super("Insufficient funds: available " + available + ", required " + required);
    }

    public InsufficientFundsException(String message) {
        super(message);
    }
}
