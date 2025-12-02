package com.waqiti.payment.wise.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Wise Exchange Rate DTO
 * 
 * Represents exchange rate information from Wise API.
 */
@Data
public class WiseExchangeRate {
    private String source;
    private String target;
    private BigDecimal rate;
    private LocalDateTime time;
}