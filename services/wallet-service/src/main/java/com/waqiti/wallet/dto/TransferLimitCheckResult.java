package com.waqiti.wallet.dto;

import lombok.*;

/**
 * Result of transfer limit validation check.
 * 
 * Encapsulates the outcome of validating a transfer against
 * daily and monthly transaction limits, providing detailed
 * error information when limits are exceeded.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferLimitCheckResult {
    
    /**
     * Whether the transfer is allowed based on limit checks
     */
    private boolean allowed;
    
    /**
     * Error code when transfer is not allowed
     */
    private String errorCode;
    
    /**
     * Detailed error message explaining why transfer was rejected
     */
    private String errorMessage;
    
    /**
     * The limit type that was exceeded (DAILY, MONTHLY)
     */
    private LimitType limitType;
    
    /**
     * Current amount already used against the limit
     */
    private String currentUsage;
    
    /**
     * Maximum allowed limit
     */
    private String limitAmount;
    
    /**
     * Remaining amount available within the limit
     */
    private String remainingAmount;
    
    /**
     * Creates a successful limit check result
     */
    public static TransferLimitCheckResult allowed() {
        return TransferLimitCheckResult.builder()
            .allowed(true)
            .build();
    }
    
    /**
     * Creates a failed limit check result
     */
    public static TransferLimitCheckResult exceeded(LimitType limitType, String errorCode, 
                                                     String errorMessage, String currentUsage, 
                                                     String limitAmount) {
        return TransferLimitCheckResult.builder()
            .allowed(false)
            .limitType(limitType)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .currentUsage(currentUsage)
            .limitAmount(limitAmount)
            .build();
    }
    
    /**
     * Type of transaction limit
     */
    public enum LimitType {
        DAILY,
        MONTHLY,
        PER_TRANSACTION,
        WEEKLY,
        CUSTOM
    }
}