package com.waqiti.payment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List; /**
 * Request to create a new split payment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSplitPaymentRequest {
    @NotBlank(message = "Title is required")
    @Size(max = 100, message = "Title cannot exceed 100 characters")
    private String title;
    
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
    
    @NotNull(message = "Total amount is required")
    @Positive(message = "Total amount must be positive")
    private BigDecimal totalAmount;
    
    @NotNull(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter code")
    private String currency;
    
    @NotEmpty(message = "Participants are required")
    @Valid
    private List<SplitPaymentParticipantRequest> participants;
    
    private Integer expiryDays;
}
