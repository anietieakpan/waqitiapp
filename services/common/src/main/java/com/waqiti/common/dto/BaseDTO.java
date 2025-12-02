package com.waqiti.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CRITICAL SECURITY FIX - BaseDTO
 * Base class for all Data Transfer Objects
 * Provides secure, consistent data exposure patterns
 */
@Data
public abstract class BaseDTO {
    
    protected UUID id;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    protected LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    protected LocalDateTime updatedAt;
    
    /**
     * Mask sensitive string data for logging/exposure
     */
    protected String maskSensitiveData(String data) {
        if (data == null || data.length() <= 4) {
            return "****";
        }
        return data.substring(0, 2) + "*".repeat(data.length() - 4) + data.substring(data.length() - 2);
    }
    
    /**
     * Mask financial amounts for non-authorized users
     */
    protected String maskAmount(java.math.BigDecimal amount) {
        if (amount == null) return null;
        return "***.**";
    }
    
    /**
     * Mask credit score for privacy
     */
    protected String maskCreditScore(Integer score) {
        if (score == null) return null;
        return "***";
    }
    
    /**
     * Get safe display amount (rounded to 2 decimal places)
     */
    protected java.math.BigDecimal getSafeAmount(java.math.BigDecimal amount) {
        if (amount == null) return null;
        return amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}