package com.waqiti.billpayment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for a participant in a bill sharing request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareBillParticipant {

    @Email(message = "Valid email is required")
    @NotBlank(message = "Email is required")
    private String email;

    @Positive(message = "Share percentage must be positive")
    private BigDecimal sharePercentage; // Required if splitType = PERCENTAGE

    @Positive(message = "Share amount must be positive")
    private BigDecimal shareAmount; // Required if splitType = CUSTOM
}
