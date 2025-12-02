package com.waqiti.bankintegration.api;

import java.util.Map;

// Response DTOs
@lombok.Data
@lombok.Builder
public class PaymentProvider {
    private String type;
    private Map<String, String> credentials;
}
