package com.waqiti.rewards.dto.referral;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Comprehensive referral analytics")
public class ReferralAnalyticsDto {

    @Schema(description = "Total number of referral links created")
    private Integer totalLinks;

    @Schema(description = "Total clicks across all links")
    private Long totalClicks;

    @Schema(description = "Total unique clicks")
    private Long uniqueClicks;

    @Schema(description = "Total conversions")
    private Long totalConversions;

    @Schema(description = "Overall conversion rate")
    private Double conversionRate;

    @Schema(description = "Total rewards earned")
    private BigDecimal totalRewardsEarned;

    @Schema(description = "Total cashback earned")
    private BigDecimal totalCashbackEarned;

    @Schema(description = "Total points earned")
    private Long totalPointsEarned;

    @Schema(description = "Pending rewards count")
    private Integer pendingRewardsCount;

    @Schema(description = "Pending rewards value")
    private BigDecimal pendingRewardsValue;

    @Schema(description = "Average time to conversion in hours")
    private Double avgTimeToConversion;

    @Schema(description = "Clicks by country code")
    private Map<String, Long> clicksByCountry;

    @Schema(description = "Clicks by device type")
    private Map<String, Long> clicksByDevice;

    @Schema(description = "Clicks by channel (EMAIL, SMS, SOCIAL_MEDIA, etc.)")
    private Map<String, Long> clicksByChannel;

    @Schema(description = "Top performing links")
    private java.util.List<ReferralLinkDto> topLinks;

    @Schema(description = "Performance by program")
    private Map<String, ProgramPerformanceDto> performanceByProgram;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Program-specific performance metrics")
    public static class ProgramPerformanceDto {
        @Schema(description = "Program ID")
        private String programId;

        @Schema(description = "Program name")
        private String programName;

        @Schema(description = "Total clicks")
        private Long clicks;

        @Schema(description = "Total conversions")
        private Long conversions;

        @Schema(description = "Conversion rate")
        private Double conversionRate;

        @Schema(description = "Total rewards earned")
        private BigDecimal rewardsEarned;
    }
}
