package com.waqiti.rewards.controller;

import com.waqiti.common.security.SecurityContext;
import com.waqiti.rewards.domain.ReferralLink;
import com.waqiti.rewards.dto.referral.ReferralAnalyticsDto;
import com.waqiti.rewards.dto.referral.ReferralLinkDto;
import com.waqiti.rewards.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API controller for referral analytics and reporting
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-09
 */
@RestController
@RequestMapping("/api/v1/referrals/analytics")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Referral Analytics", description = "Referral program analytics and reporting")
public class ReferralAnalyticsController {

    private final ReferralLinkService linkService;
    private final ReferralRewardService rewardService;
    private final ReferralClickAnalyticsService clickAnalyticsService;
    private final ReferralStatisticsService statisticsService;
    private final ReferralProgramService programService;
    private final SecurityContext securityContext;

    @Operation(
            summary = "Get my referral analytics",
            description = "Get comprehensive analytics for the authenticated user's referrals"
    )
    @ApiResponse(responseCode = "200", description = "Analytics retrieved successfully")
    @GetMapping("/my-analytics")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ReferralAnalyticsDto> getMyAnalytics() {
        String userId = securityContext.getCurrentUserId();
        log.debug("User {} retrieving referral analytics", userId);

        UUID userUuid = UUID.fromString(userId);

        // Get user's links
        List<ReferralLink> links = linkService.getUserLinks(userUuid,
                org.springframework.data.domain.Pageable.unpaged()).getContent();

        // Calculate totals
        long totalClicks = links.stream()
                .mapToLong(l -> l.getClickCount() != null ? l.getClickCount() : 0)
                .sum();

        long uniqueClicks = links.stream()
                .mapToLong(l -> l.getUniqueClicks() != null ? l.getUniqueClicks() : 0)
                .sum();

        long totalConversions = links.stream()
                .mapToLong(l -> l.getConversionCount() != null ? l.getConversionCount() : 0)
                .sum();

        double conversionRate = totalClicks > 0
                ? (totalConversions * 100.0) / totalClicks
                : 0.0;

        // Get rewards totals
        BigDecimal totalCashback = rewardService.getTotalCashbackEarned(userUuid);
        Long totalPoints = rewardService.getTotalPointsEarned(userUuid);
        BigDecimal totalRewards = totalCashback != null ? totalCashback : BigDecimal.ZERO;

        // Get pending rewards
        List<com.waqiti.rewards.domain.ReferralReward> pending =
                rewardService.getPendingRewardsForUser(userUuid);
        BigDecimal pendingValue = pending.stream()
                .filter(r -> r.getCashbackAmount() != null)
                .map(com.waqiti.rewards.domain.ReferralReward::getCashbackAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Get top performing links (top 5)
        List<ReferralLinkDto> topLinks = links.stream()
                .sorted((a, b) -> Integer.compare(
                        b.getConversionCount() != null ? b.getConversionCount() : 0,
                        a.getConversionCount() != null ? a.getConversionCount() : 0
                ))
                .limit(5)
                .map(this::toLinkDto)
                .collect(Collectors.toList());

        // Get analytics by link for channel/device breakdown
        Map<String, Long> clicksByChannel = new HashMap<>();
        Map<String, Long> clicksByDevice = new HashMap<>();
        Map<String, Long> clicksByCountry = new HashMap<>();

        for (ReferralLink link : links) {
            try {
                Map<String, Object> linkAnalytics = clickAnalyticsService.getLinkAnalytics(link.getLinkId());

                // Channel breakdown
                String channel = link.getChannel();
                if (channel != null) {
                    clicksByChannel.merge(channel, (long) link.getClickCount(), Long::sum);
                }

                // Device and country from analytics
                @SuppressWarnings("unchecked")
                Map<String, Long> deviceMap = (Map<String, Long>) linkAnalytics.get("by_device");
                if (deviceMap != null) {
                    deviceMap.forEach((device, count) ->
                            clicksByDevice.merge(device, count, Long::sum));
                }

                @SuppressWarnings("unchecked")
                Map<String, Long> countryMap = (Map<String, Long>) linkAnalytics.get("by_country");
                if (countryMap != null) {
                    countryMap.forEach((country, count) ->
                            clicksByCountry.merge(country, count, Long::sum));
                }
            } catch (Exception e) {
                log.warn("Error fetching analytics for link: {}", link.getLinkId(), e);
            }
        }

        // Build response
        ReferralAnalyticsDto analytics = ReferralAnalyticsDto.builder()
                .totalLinks(links.size())
                .totalClicks(totalClicks)
                .uniqueClicks(uniqueClicks)
                .totalConversions(totalConversions)
                .conversionRate(conversionRate)
                .totalRewardsEarned(totalRewards)
                .totalCashbackEarned(totalCashback)
                .totalPointsEarned(totalPoints)
                .pendingRewardsCount(pending.size())
                .pendingRewardsValue(pendingValue)
                .clicksByChannel(clicksByChannel)
                .clicksByDevice(clicksByDevice)
                .clicksByCountry(clicksByCountry)
                .topLinks(topLinks)
                .build();

        return ResponseEntity.ok(analytics);
    }

    @Operation(
            summary = "Get program statistics",
            description = "Get detailed statistics for a specific referral program"
    )
    @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Program not found")
    @GetMapping("/programs/{programId}/statistics")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> getProgramStatistics(
            @Parameter(description = "Program ID") @PathVariable String programId,
            @Parameter(description = "Number of days to analyze") @RequestParam(defaultValue = "30") int days) {
        log.debug("Retrieving statistics for program: {}, days: {}", programId, days);

        java.time.LocalDateTime since = java.time.LocalDateTime.now().minusDays(days);
        Map<String, Object> stats = statisticsService.getProgramStatistics(programId, since);

        return ResponseEntity.ok(stats);
    }

    @Operation(
            summary = "Get user program performance",
            description = "Get user's performance metrics for a specific program"
    )
    @ApiResponse(responseCode = "200", description = "Performance metrics retrieved successfully")
    @GetMapping("/programs/{programId}/my-performance")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> getMyProgramPerformance(
            @Parameter(description = "Program ID") @PathVariable String programId) {
        String userId = securityContext.getCurrentUserId();
        log.debug("User {} retrieving performance for program: {}", userId, programId);

        UUID userUuid = UUID.fromString(userId);

        // Get user links for this program
        List<ReferralLink> links = linkService.getUserLinksByProgram(
                userUuid,
                programId,
                org.springframework.data.domain.Pageable.unpaged()
        ).getContent();

        long clicks = links.stream()
                .mapToLong(l -> l.getClickCount() != null ? l.getClickCount() : 0)
                .sum();

        long conversions = links.stream()
                .mapToLong(l -> l.getConversionCount() != null ? l.getConversionCount() : 0)
                .sum();

        double conversionRate = clicks > 0 ? (conversions * 100.0) / clicks : 0.0;

        // Get rewards for this program
        List<com.waqiti.rewards.domain.ReferralReward> rewards =
                rewardService.getUserRewardsByProgram(
                        userUuid,
                        programId,
                        org.springframework.data.domain.Pageable.unpaged()
                ).getContent();

        BigDecimal totalEarned = rewards.stream()
                .filter(r -> r.getCashbackAmount() != null)
                .map(com.waqiti.rewards.domain.ReferralReward::getCashbackAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> performance = new HashMap<>();
        performance.put("program_id", programId);
        performance.put("total_links", links.size());
        performance.put("total_clicks", clicks);
        performance.put("total_conversions", conversions);
        performance.put("conversion_rate", conversionRate);
        performance.put("total_rewards", rewards.size());
        performance.put("total_earned", totalEarned);

        return ResponseEntity.ok(performance);
    }

    // ========== ADMIN ENDPOINTS ==========

    @Operation(
            summary = "Admin: Get system-wide analytics",
            description = "Get comprehensive analytics across all referral programs (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Analytics retrieved successfully")
    @GetMapping("/admin/system-analytics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getSystemAnalytics(
            @Parameter(description = "Number of days to analyze") @RequestParam(defaultValue = "30") int days) {
        log.info("Admin retrieving system-wide referral analytics");

        java.time.LocalDateTime since = java.time.LocalDateTime.now().minusDays(days);

        // Get all programs
        List<com.waqiti.rewards.domain.ReferralProgram> programs =
                programService.getActivePrograms();

        Map<String, Object> systemAnalytics = new HashMap<>();
        systemAnalytics.put("total_programs", programs.size());

        // Aggregate across all programs
        long totalReferrals = programs.stream()
                .mapToLong(p -> p.getTotalReferrals() != null ? p.getTotalReferrals() : 0)
                .sum();

        long successfulReferrals = programs.stream()
                .mapToLong(p -> p.getSuccessfulReferrals() != null ? p.getSuccessfulReferrals() : 0)
                .sum();

        BigDecimal totalRewardsIssued = programs.stream()
                .map(p -> p.getTotalRewardsIssued() != null ? p.getTotalRewardsIssued() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double avgConversionRate = programs.stream()
                .filter(p -> p.getConversionRate() != null)
                .mapToDouble(p -> p.getConversionRate().doubleValue())
                .average()
                .orElse(0.0);

        systemAnalytics.put("total_referrals", totalReferrals);
        systemAnalytics.put("successful_referrals", successfulReferrals);
        systemAnalytics.put("total_rewards_issued", totalRewardsIssued);
        systemAnalytics.put("avg_conversion_rate", avgConversionRate);
        systemAnalytics.put("period_days", days);

        // Get top performing programs
        List<Map<String, Object>> topPrograms = programs.stream()
                .sorted((a, b) -> {
                    BigDecimal aRate = a.getConversionRate() != null ? a.getConversionRate() : BigDecimal.ZERO;
                    BigDecimal bRate = b.getConversionRate() != null ? b.getConversionRate() : BigDecimal.ZERO;
                    return bRate.compareTo(aRate);
                })
                .limit(5)
                .map(p -> {
                    Map<String, Object> programData = new HashMap<>();
                    programData.put("program_id", p.getProgramId());
                    programData.put("program_name", p.getProgramName());
                    programData.put("total_referrals", p.getTotalReferrals());
                    programData.put("conversion_rate", p.getConversionRate());
                    programData.put("rewards_issued", p.getTotalRewardsIssued());
                    return programData;
                })
                .collect(Collectors.toList());

        systemAnalytics.put("top_performing_programs", topPrograms);

        return ResponseEntity.ok(systemAnalytics);
    }

    @Operation(
            summary = "Admin: Get program comparison",
            description = "Compare performance metrics across multiple programs (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Comparison retrieved successfully")
    @GetMapping("/admin/program-comparison")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> compareProgramPerformance(
            @Parameter(description = "Program IDs to compare") @RequestParam List<String> programIds) {
        log.info("Admin comparing programs: {}", programIds);

        List<Map<String, Object>> comparison = new ArrayList<>();

        for (String programId : programIds) {
            try {
                com.waqiti.rewards.domain.ReferralProgram program =
                        programService.getProgramByProgramId(programId);

                Map<String, Object> programMetrics = new HashMap<>();
                programMetrics.put("program_id", program.getProgramId());
                programMetrics.put("program_name", program.getProgramName());
                programMetrics.put("total_referrals", program.getTotalReferrals());
                programMetrics.put("successful_referrals", program.getSuccessfulReferrals());
                programMetrics.put("conversion_rate", program.getConversionRate());
                programMetrics.put("total_rewards_issued", program.getTotalRewardsIssued());
                programMetrics.put("avg_time_to_conversion", program.getAverageTimeToConversion());

                BigDecimal roi = calculateROI(program);
                programMetrics.put("roi", roi);

                comparison.add(programMetrics);
            } catch (Exception e) {
                log.warn("Error fetching program: {}", programId, e);
            }
        }

        return ResponseEntity.ok(comparison);
    }

    @Operation(
            summary = "Admin: Get user referral performance",
            description = "Get detailed referral performance for any user (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "User performance retrieved successfully")
    @GetMapping("/admin/users/{userId}/performance")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getUserPerformance(
            @Parameter(description = "User ID") @PathVariable String userId) {
        log.info("Admin retrieving performance for user: {}", userId);

        UUID userUuid = UUID.fromString(userId);

        // Get user's links
        List<ReferralLink> links = linkService.getUserLinks(
                userUuid,
                org.springframework.data.domain.Pageable.unpaged()
        ).getContent();

        long totalClicks = links.stream()
                .mapToLong(l -> l.getClickCount() != null ? l.getClickCount() : 0)
                .sum();

        long totalConversions = links.stream()
                .mapToLong(l -> l.getConversionCount() != null ? l.getConversionCount() : 0)
                .sum();

        // Get rewards
        BigDecimal totalCashback = rewardService.getTotalCashbackEarned(userUuid);
        Long totalPoints = rewardService.getTotalPointsEarned(userUuid);

        Map<String, Object> performance = new HashMap<>();
        performance.put("user_id", userId);
        performance.put("total_links", links.size());
        performance.put("total_clicks", totalClicks);
        performance.put("total_conversions", totalConversions);
        performance.put("conversion_rate", totalClicks > 0 ? (totalConversions * 100.0) / totalClicks : 0.0);
        performance.put("total_cashback_earned", totalCashback);
        performance.put("total_points_earned", totalPoints);

        return ResponseEntity.ok(performance);
    }

    /**
     * Calculate ROI for a program
     */
    private BigDecimal calculateROI(com.waqiti.rewards.domain.ReferralProgram program) {
        // Simple ROI calculation - would need actual revenue data for accurate calculation
        if (program.getTotalRewardsIssued() == null ||
            program.getTotalRewardsIssued().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        // Placeholder: assume 10x revenue multiplier
        BigDecimal estimatedRevenue = program.getTotalRewardsIssued().multiply(new BigDecimal("10"));
        BigDecimal profit = estimatedRevenue.subtract(program.getTotalRewardsIssued());

        return profit.divide(program.getTotalRewardsIssued(), 2, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }

    /**
     * Convert link entity to DTO
     */
    private ReferralLinkDto toLinkDto(ReferralLink link) {
        Double conversionRate = null;
        if (link.getClickCount() != null && link.getClickCount() > 0) {
            conversionRate = (link.getConversionCount() * 100.0) / link.getClickCount();
        }

        return ReferralLinkDto.builder()
                .linkId(link.getLinkId())
                .userId(link.getUserId())
                .programId(link.getProgram().getProgramId())
                .referralCode(link.getReferralCode())
                .shortUrl(link.getShortUrl())
                .clickCount(link.getClickCount())
                .uniqueClicks(link.getUniqueClicks())
                .conversionCount(link.getConversionCount())
                .conversionRate(conversionRate)
                .channel(link.getChannel())
                .build();
    }
}
