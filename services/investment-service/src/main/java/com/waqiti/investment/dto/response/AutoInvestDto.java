package com.waqiti.investment.dto.response;

import com.waqiti.investment.domain.enums.AutoInvestFrequency;
import com.waqiti.investment.domain.enums.AutoInvestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for auto-invest plan response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoInvestDto {
    private Long id;
    private Long accountId;
    private String name;
    private BigDecimal investmentAmount;
    private AutoInvestFrequency frequency;
    private AutoInvestStatus status;
    private List<AllocationDto> allocations;
    private LocalDate nextExecutionDate;
    private LocalDate lastExecutionDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllocationDto {
        private String symbol;
        private BigDecimal percentage;
    }
}