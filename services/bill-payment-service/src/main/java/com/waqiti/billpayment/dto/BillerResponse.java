package com.waqiti.billpayment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for biller information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BillerResponse {

    private UUID id;

    private String billerCode;

    private String name;

    private String category;

    private String country;

    private String logoUrl;

    private String description;

    private Boolean isActive;

    private Boolean supportsInstantPayment;

    private Boolean supportsScheduledPayment;

    private Boolean supportsRecurringPayment;

    private Boolean supportsPartialPayment;

    private String paymentInstructions;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
