package com.waqiti.analytics.dto.pattern;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DigitalPattern {
    private String preferredPaymentMethod; // CARD, DIGITAL_WALLET, BANK_TRANSFER
    private Map<String, BigDecimal> paymentMethodDistribution;
    private BigDecimal digitalAdoption; // 0-1 score
    private String devicePreference; // MOBILE, DESKTOP, TABLET
    private Boolean automationUsage; // Uses recurring payments, auto-pay etc
}