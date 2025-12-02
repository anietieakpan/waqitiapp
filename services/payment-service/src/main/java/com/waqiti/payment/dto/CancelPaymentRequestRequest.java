package com.waqiti.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request to cancel a payment request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelPaymentRequestRequest {
    @Size(max = 255, message = "Reason cannot exceed 255 characters")
    private String reason;
}