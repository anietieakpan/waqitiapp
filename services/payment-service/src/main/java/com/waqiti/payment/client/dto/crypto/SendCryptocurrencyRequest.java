package com.waqiti.payment.client.dto.crypto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for sending cryptocurrency
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendCryptocurrencyRequest {
    private UUID userId;
    private String currency;
    private String toAddress;
    private BigDecimal amount;
    private String memo;
    private BigDecimal networkFee;
    private String priority; // LOW, MEDIUM, HIGH
}
