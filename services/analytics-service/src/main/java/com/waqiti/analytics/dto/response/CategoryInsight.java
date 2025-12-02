package com.waqiti.analytics.dto.response;

import lombok.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryInsight {
    @NotBlank private String category;
    @NotNull @PositiveOrZero private BigDecimal amount;
    @NotNull @PositiveOrZero private BigDecimal percentage;
    @PositiveOrZero private Integer transactionCount;
    private BigDecimal averageAmount;
    private BigDecimal changeFromPrevious;
    private BigDecimal changePercentage;
    private String trend;
    private CategoryBehavior behavior;
    private List<String> insights;
}
