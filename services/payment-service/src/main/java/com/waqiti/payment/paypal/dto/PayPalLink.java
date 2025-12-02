package com.waqiti.payment.paypal.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PayPalLink {
    private String href;
    private String rel; // self, refund, up, etc.
    private String method; // GET, POST, PATCH, etc.
}