package com.waqiti.payment.qrcode.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Request DTO for QR code analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for QR code analytics")
public class QRCodeAnalyticsRequest {

    @Schema(description = "Merchant ID for merchant-specific analytics")
    @JsonProperty("merchant_id")
    private String merchantId;

    @Schema(description = "User ID for user-specific analytics")
    @JsonProperty("user_id")
    private String userId;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Start date for analytics period", required = true)
    @JsonProperty("start_date")
    private LocalDateTime startDate;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "End date for analytics period", required = true)
    @JsonProperty("end_date")
    private LocalDateTime endDate;

    @Schema(description = "Grouping period for time series data", example = "DAY")
    @JsonProperty("group_by")
    private String groupBy;

    @Schema(description = "Specific metrics to include")
    private List<String> metrics;

    @Schema(description = "Filters to apply to the analytics")
    private AnalyticsFilters filters;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalyticsFilters {
        @JsonProperty("qr_types")
        private List<String> qrTypes;
        
        private List<String> statuses;
        
        @JsonProperty("min_amount")
        private Double minAmount;
        
        @JsonProperty("max_amount")
        private Double maxAmount;
        
        private List<String> currencies;
        
        @JsonProperty("payment_methods")
        private List<String> paymentMethods;
    }
}