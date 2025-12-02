package com.waqiti.investment.exception;

import com.waqiti.common.exception.ErrorCode;
import com.waqiti.common.exception.WaqitiException;

/**
 * General investment service exception
 */
public class InvestmentException extends WaqitiException {
    
    public InvestmentException(String message) {
        super(ErrorCode.BIZ_INVALID_OPERATION, message);
    }
    
    public InvestmentException(String message, Throwable cause) {
        super(ErrorCode.BIZ_INVALID_OPERATION, message, cause);
    }
    
    public InvestmentException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    public InvestmentException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}