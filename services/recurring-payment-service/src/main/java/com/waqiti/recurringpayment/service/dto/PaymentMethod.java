package com.waqiti.recurringpayment.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethod {
    private String id;
    private String type;
    private String displayName;
    private String lastFourDigits;
    private boolean isDefault;
    private boolean isActive;
    private Map<String, String> metadata;
}
