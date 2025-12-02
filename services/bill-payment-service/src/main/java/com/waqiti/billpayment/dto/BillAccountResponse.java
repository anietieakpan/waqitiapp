package com.waqiti.billpayment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for bill account information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BillAccountResponse {

    private UUID id;

    private String userId;

    private UUID billerId;

    private String billerName;

    private String billerCategory;

    private String accountNumber;

    private String maskedAccountNumber;

    private String accountName;

    private String notes;

    private Boolean isDefault;

    private Boolean isActive;

    private LocalDateTime lastUsedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
