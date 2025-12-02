package com.waqiti.payment.businessprofile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Product performance metrics for business analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductMetric {

    private String productId;
    private String productName;
    private String category;
    private Long salesCount;
    private Long totalQuantity;
    private BigDecimal totalRevenue;
    private BigDecimal averagePrice;
    private Double profitMargin;
    private Integer stockLevel;

    // Helper methods
    public boolean isTopPerformer() {
        return salesCount != null && salesCount > 100;
    }

    public boolean isLowStock() {
        return stockLevel != null && stockLevel < 10;
    }

    public BigDecimal getRevenuePerUnit() {
        if (salesCount == null || salesCount == 0) {
            return BigDecimal.ZERO;
        }
        return totalRevenue.divide(BigDecimal.valueOf(salesCount), 2, java.math.RoundingMode.HALF_UP);
    }
}
