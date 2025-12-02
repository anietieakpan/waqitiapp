package com.waqiti.payment.paypal.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PayPalAmount {
    private String currencyCode; // ISO 4217 currency code
    private String value; // Decimal amount as string (e.g., "10.99")
}