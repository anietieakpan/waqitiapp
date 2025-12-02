package com.waqiti.investment.exception;

import com.waqiti.common.exception.ErrorCode;
import com.waqiti.common.exception.WaqitiException;

/**
 * Exception thrown when market data operations fail
 */
public class MarketDataException extends WaqitiException {
    
    public MarketDataException(String message) {
        super(ErrorCode.INT_SERVICE_UNAVAILABLE, message);
    }
    
    public MarketDataException(String message, Throwable cause) {
        super(ErrorCode.INT_SERVICE_UNAVAILABLE, message, cause);
    }
    
    public MarketDataException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    public MarketDataException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}