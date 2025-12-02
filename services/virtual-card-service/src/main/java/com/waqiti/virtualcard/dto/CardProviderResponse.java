package com.waqiti.virtualcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
<parameter name="content">import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO from card provider after card creation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardProviderResponse {
    private String providerId;
    private String cardNumber;
    private String lastFourDigits;
    private Integer expiryMonth;
    private Integer expiryYear;
    private String cvv; // Only used during creation, never stored
    private boolean success;
    private String errorMessage;
}
