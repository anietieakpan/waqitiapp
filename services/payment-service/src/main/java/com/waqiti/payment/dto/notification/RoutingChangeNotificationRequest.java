package com.waqiti.payment.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for sending payment routing change notifications
 * 
 * This request triggers notifications to users about payment gateway routing changes,
 * including cost savings and routing optimization details.
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoutingChangeNotificationRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * User ID to receive the notification
     */
    @NotBlank(message = "User ID is required")
    @Size(max = 50, message = "User ID must not exceed 50 characters")
    private String userId;

    /**
     * Unique identifier for the payment
     */
    @NotBlank(message = "Payment ID is required")
    @Size(max = 100, message = "Payment ID must not exceed 100 characters")
    private String paymentId;

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
     * Reason for the routing change
     */
    @NotBlank(message = "Routing reason is required")
    @Size(max = 200, message = "Routing reason must not exceed 200 characters")
    private String routingReason;

    /**
     * List of notification channels to use
     * Examples: EMAIL, SMS, PUSH, IN_APP
     */
    @NotNull(message = "Channels list is required")
    private List<String> channels;

    /**
     * Correlation ID for tracing requests across services
     */
    @Size(max = 100, message = "Correlation ID must not exceed 100 characters")
    private String correlationId;
}