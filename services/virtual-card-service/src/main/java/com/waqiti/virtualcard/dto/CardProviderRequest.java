package com.waqiti.virtualcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a card with the provider
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardProviderRequest {
    private String userId;
    private String type;
    private String currency;
    private String cardholderName;
    private Object billingAddress; // Type depends on actual implementation
}
