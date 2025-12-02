package com.waqiti.virtualcard.dto;

import com.waqiti.virtualcard.domain.CardDesign;
import com.waqiti.virtualcard.domain.CardPersonalization;
import com.waqiti.virtualcard.domain.ShippingAddress;
import com.waqiti.virtualcard.domain.enums.CardBrand;
import com.waqiti.virtualcard.domain.enums.CardType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for submitting card order to provider
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardProviderOrderRequest {
    
    private String orderId;
    private String userId;
    private CardType type;
    private CardBrand brand;
    private CardDesign design;
    private CardPersonalization personalization;
    private ShippingAddress shippingAddress;
    
    @Builder.Default
    private boolean rushOrder = false;
    
    @Builder.Default
    private boolean testMode = false;
    
    private String callbackUrl;
    private String clientReferenceId;
}