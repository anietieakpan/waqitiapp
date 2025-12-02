package com.waqiti.familyaccount.exception;

import java.math.BigDecimal;

/**
 * Exception thrown when spending limit is exceeded
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
public class SpendingLimitExceededException extends FamilyAccountException {

    public SpendingLimitExceededException(String limitType, BigDecimal limit, BigDecimal attempted) {
        super(limitType + " spending limit exceeded: limit " + limit + ", attempted " + attempted);
    }

    public SpendingLimitExceededException(String message) {
        super(message);
    }
}
