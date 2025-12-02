package com.waqiti.crypto.dto;

import com.waqiti.crypto.domain.CryptoCurrency;
import com.waqiti.crypto.domain.PriceData;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PriceHistoryResponse {
    private CryptoCurrency currency;
    private List<PriceData> prices;
    private LocalDateTime fromDate;
    private LocalDateTime toDate;
    private int dataPoints;
    private String interval;
}