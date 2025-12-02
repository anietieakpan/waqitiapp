package com.waqiti.crypto.dto;

import com.waqiti.crypto.domain.CryptoCurrency;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PriceHistoryRequest {
    private CryptoCurrency currency;
    private LocalDateTime fromDate;
    private LocalDateTime toDate;
    private String interval; // 1m, 5m, 1h, 1d, etc.
    private int limit;
}