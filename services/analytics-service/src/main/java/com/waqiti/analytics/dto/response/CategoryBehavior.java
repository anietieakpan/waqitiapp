package com.waqiti.analytics.dto.response;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryBehavior {
    private String pattern;             // REGULAR, IRREGULAR, SEASONAL
    private String frequency;           // DAILY, WEEKLY, MONTHLY
    private BigDecimal predictability;  // 0.0 - 1.0
    private String peakDay;
    private Integer peakHour;
}
