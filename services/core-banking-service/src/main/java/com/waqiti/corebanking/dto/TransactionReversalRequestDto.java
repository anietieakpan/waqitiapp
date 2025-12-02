package com.waqiti.corebanking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to reverse a transaction")
public class TransactionReversalRequestDto {

    @NotBlank(message = "Reason is required")
    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    @Schema(description = "Reason for transaction reversal", 
            example = "Customer dispute - unauthorized transaction", 
            required = true)
    private String reason;

    @Schema(description = "Additional notes for the reversal", 
            example = "Approved by compliance team")
    @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
    private String notes;

    @Schema(description = "Whether to notify the affected parties", example = "true")
    private Boolean notifyParties = true;
}