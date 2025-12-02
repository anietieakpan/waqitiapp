package com.waqiti.common.fraud.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime; /**
 * Merchant analysis result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantAnalysis {
    private String merchantId;
    private int analysisWindow;
    private int transactionCount;
    private MerchantMetrics metrics;
    private MerchantRiskIndicators riskIndicators;
    private LocalDateTime analysisTimestamp;
    private String error;
    
    public static MerchantAnalysis noData(String merchantId) {
        return MerchantAnalysis.builder()
                .merchantId(merchantId)
                .transactionCount(0)
                .analysisTimestamp(LocalDateTime.now())
                .build();
    }
    
    public static MerchantAnalysis error(String merchantId, String error) {
        return MerchantAnalysis.builder()
                .merchantId(merchantId)
                .error(error)
                .analysisTimestamp(LocalDateTime.now())
                .build();
    }
}
