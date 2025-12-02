package com.waqiti.common.data;

import com.waqiti.common.dto.CardStatsDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Virtual Card DTO for optimized card queries
 */
@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
public class VirtualCardDTO {
    private String cardId;
    private String userId;
    private String maskedCardNumber;
    private String cardType;
    private String brand;
    private String status;
    private BigDecimal spendingLimit;
    private BigDecimal currentSpend;
    private LocalDateTime expiryDate;
    private LocalDateTime createdAt;
    private Map<String, Object> restrictions;
    private boolean isActive;
    private CardStatsDTO statistics;
    
    public String getId() {
        return cardId;
    }
    
    public void setStatistics(CardStatsDTO statistics) {
        this.statistics = statistics;
    }
}