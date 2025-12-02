package com.waqiti.corebanking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update account status")
public class AccountStatusUpdateDto {

    @NotBlank(message = "Status is required")
    @Schema(description = "New account status", 
            example = "ACTIVE",
            allowableValues = {"PENDING", "ACTIVE", "SUSPENDED", "FROZEN", "DORMANT", "CLOSED"},
            required = true)
    private String status;

    @Schema(description = "Reason for status change", example = "Account verification completed")
    private String reason;

    @Schema(description = "Whether to notify the account holder", example = "true")
    private Boolean notifyUser = true;
}