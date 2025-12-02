package com.waqiti.investment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for historical market data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricalDataDto {
    private String symbol;
    private String interval; // DAILY, WEEKLY, MONTHLY
    private List<HistoricalQuote> quotes;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoricalQuote {
        private LocalDateTime date;
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private BigDecimal adjClose;
        private Long volume;
    }
}