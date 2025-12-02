package com.waqiti.billpayment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for sharing a bill with other users
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareBillRequest {

    @NotNull(message = "Bill ID is required")
    private UUID billId;

    @NotBlank(message = "Split type is required")
    @Size(max = 50, message = "Split type must not exceed 50 characters")
    private String splitType; // EQUAL, PERCENTAGE, CUSTOM

    @NotEmpty(message = "At least one participant is required")
    @Size(max = 50, message = "Maximum 50 participants allowed")
    @Valid
    private List<ShareBillParticipant> participants;

    @Size(max = 500, message = "Notes must not exceed 500 characters")
    private String notes;
}
