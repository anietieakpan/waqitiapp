package com.waqiti.virtualcard.dto;

import com.waqiti.virtualcard.domain.ShippingAddress;
import com.waqiti.virtualcard.domain.enums.ReplacementReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for submitting card replacement to provider
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardProviderReplacementRequest {
    
    private String originalCardProviderId;
    private String orderId;
    private ReplacementReason reason;
    private ShippingAddress shippingAddress;
    
    @Builder.Default
    private boolean rushDelivery = false;
    
    @Builder.Default
    private boolean blockOriginalCard = true;
    
    private String incidentDescription;
    private String policeReportNumber;
    private String callbackUrl;
}