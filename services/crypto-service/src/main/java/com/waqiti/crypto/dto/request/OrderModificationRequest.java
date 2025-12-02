package com.waqiti.crypto.dto.request;

import com.waqiti.crypto.entity.TradeOrderType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Order Modification Request DTO
 * Request to modify an existing trading order
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderModificationRequest {
    
    /**
     * New order type (optional - only certain modifications allowed)
     */
    private TradeOrderType orderType;
    
    /**
     * New quantity for the order
     */
    @DecimalMin(value = "0.00000001", message = "Minimum quantity is 0.00000001")
    private BigDecimal quantity;
    
    /**
     * New price for limit orders
     */
    @DecimalMin(value = "0.01", message = "Minimum price is 0.01")
    private BigDecimal price;
    
    /**
     * New stop price for stop orders
     */
    @DecimalMin(value = "0.01", message = "Minimum stop price is 0.01")
    private BigDecimal stopPrice;
    
    /**
     * New time-in-force for the order (in minutes)
     */
    @Min(value = 1, message = "Time in force must be at least 1 minute")
    private Integer timeInForceMinutes;
    
    /**
     * Transaction PIN for security verification
     */
    @Pattern(regexp = "^[0-9]{6}$", message = "Transaction PIN must be 6 digits")
    private String transactionPin;
    
    /**
     * Optional comment for the modification
     */
    private String modificationReason;
}