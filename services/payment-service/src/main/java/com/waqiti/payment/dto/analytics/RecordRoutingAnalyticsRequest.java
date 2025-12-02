package com.waqiti.payment.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO for recording payment routing analytics data
 * 
 * This request captures comprehensive routing analytics information for:
 * - Gateway performance analysis
 * - Cost optimization tracking
 * - Success rate monitoring
 * - Processing time analysis
 * - Regional performance insights
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecordRoutingAnalyticsRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier for the payment
     */
    @NotBlank(message = "Payment ID is required")
    @Size(max = 100, message = "Payment ID must not exceed 100 characters")
    private String paymentId;

    /**
     * Customer ID associated with the payment
     */
    @NotBlank(message = "Customer ID is required")
    @Size(max = 50, message = "Customer ID must not exceed 50 characters")
    private String customerId;

    /**
     * Original payment gateway before routing change
     */
    @NotBlank(message = "Original gateway is required")
    @Size(max = 50, message = "Original gateway must not exceed 50 characters")
    private String originalGateway;

    /**
     * New payment gateway after routing change
     */
    @NotBlank(message = "New gateway is required")
    @Size(max = 50, message = "New gateway must not exceed 50 characters")
    private String newGateway;

    /**
     * Routing strategy used for the change
     */
    @NotBlank(message = "Strategy is required")
    @Size(max = 50, message = "Strategy must not exceed 50 characters")
    private String strategy;

    /**
     * Cost savings achieved through routing change
     */
    @NotNull(message = "Cost savings is required")
    @DecimalMin(value = "0.00", message = "Cost savings must be non-negative")
    private BigDecimal costSavings;

    /**
     * Original processing cost
     */
    @NotNull(message = "Original cost is required")
    @DecimalMin(value = "0.00", message = "Original cost must be non-negative")
    private BigDecimal originalCost;

    /**
     * New processing cost
     */
    @NotNull(message = "New cost is required")
    @DecimalMin(value = "0.00", message = "New cost must be non-negative")
    private BigDecimal newCost;

    /**
     * Success rate of the original gateway (as percentage)
     */
    @Min(value = 0, message = "Original success rate must be non-negative")
    private Double originalSuccessRate;

    /**
     * Success rate of the new gateway (as percentage)
     */
    @Min(value = 0, message = "New success rate must be non-negative")
    private Double newSuccessRate;

    /**
     * Average processing time of the original gateway (in milliseconds)
     */
    @Min(value = 0, message = "Original processing time must be non-negative")
    private Long originalProcessingTime;

    /**
     * Average processing time of the new gateway (in milliseconds)
     */
    @Min(value = 0, message = "New processing time must be non-negative")
    private Long newProcessingTime;

    /**
     * Payment amount
     */
    @NotNull(message = "Payment amount is required")
    @DecimalMin(value = "0.01", message = "Payment amount must be greater than zero")
    private BigDecimal paymentAmount;

    /**
     * Currency code (ISO 4217 format)
     */
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-character ISO code")
    private String currency;

    /**
     * Geographic region where the payment was processed
     */
    @Size(max = 50, message = "Region must not exceed 50 characters")
    private String region;

    /**
     * Payment method used (card, bank_transfer, wallet, etc.)
     */
    @Size(max = 50, message = "Payment method must not exceed 50 characters")
    private String paymentMethod;

    /**
     * Reason for the routing change
     */
    @NotBlank(message = "Routing reason is required")
    @Size(max = 200, message = "Routing reason must not exceed 200 characters")
    private String routingReason;

    /**
     * Timestamp when the routing change occurred
     */
    @NotNull(message = "Change timestamp is required")
    private Instant changeTimestamp;

    /**
     * Correlation ID for tracing requests across services
     */
    @Size(max = 100, message = "Correlation ID must not exceed 100 characters")
    private String correlationId;
}