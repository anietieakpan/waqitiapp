package com.waqiti.payment.integration.dwolla.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.Map;

@Value
@Builder
public class DwollaTransferRequest {
    String source;
    String destination;
    BigDecimal amount;
    String currency;
    Map<String, String> metadata;
    String correlationId;
}